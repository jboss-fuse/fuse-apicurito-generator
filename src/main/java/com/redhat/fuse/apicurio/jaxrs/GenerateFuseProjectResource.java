/*
 * Copyright (C) 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.fuse.apicurio.jaxrs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.camel.CamelContext;
import org.apache.camel.generator.swagger.RestDslGenerator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.io.IOUtils;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import io.swagger.annotations.Api;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;

/**
 * Implements a jaxrs resource that can be used by Apicurito to
 * generate Camel projects from an openapi spec.
 */
@Path("/api/v1/generate")
@Api(value = "generate")
@Component
public class GenerateFuseProjectResource {

    public static final String FUSE_VERSION;

    private static final Pattern LINE_START = Pattern.compile("^", Pattern.MULTILINE);

    static {
        String fuseVersion = null;
        try {
            fuseVersion = readUTF8Resource("/fuse-version.txt");
        } catch (IOException e) {
        }
        if (fuseVersion == null) {
            // We hit this case when running in an IDE since it likely will not
            // do the resource filtering on the fuse-version.txt file.  So set it
            // to something we can test with.
            fuseVersion = "7.1.0.fuse-710019-redhat-00002";
        }
        FUSE_VERSION = fuseVersion;
    }

    Function<String, String> generateuuid = (s) -> UUID.randomUUID().toString();

    /**
     * Generate an example zip file containing a camel project for an example
     * openapi spec.
     */
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path(value = "/camel-project.zip")
    public InputStream generate() throws Exception {
        try (InputStream openapiDoc = getClass().getResourceAsStream("open-api-example.json")) {
            return generate(readUTF8(openapiDoc));
        }
    }


    /**
     * Generate a zip file containing a camel project that implements the posted
     * openapi spec.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path(value = "/camel-project.zip")
    public InputStream generate(String openapiDoc) throws Exception {

        final Swagger initialSwagger = new SwaggerParser().read(Json.mapper().readTree(openapiDoc));
        final Swagger swagger = cleanupSwaggerFile(initialSwagger);

        GenericArchive archive = ShrinkWrap.create(GenericArchive.class, "camel-project.zip");

        String title = "Example";
        if (swagger.getInfo() != null && swagger.getInfo().getTitle() != null) {
            title = swagger.getInfo().getTitle();
        }
        String artifactId = title.toLowerCase().replaceAll("[^a-zA-Z0-9-_]+", "-").replaceAll("-+", "-");

        HashMap<String, Object> variables = new HashMap<>();
        variables.put("swagger", swagger);
        variables.put("title", title);
        variables.put("artifactId", artifactId);
        variables.put("fuseVersion", FUSE_VERSION);
        variables.put("uuid", generateuuid);

        addTemplateResource(archive, "README.md", variables);
        addTemplateResource(archive, "pom.xml", variables);
        addTemplateResource(archive, "src/main/resources/application.yml", variables);

        addStaticResource(archive, "configuration/settings.xml");
        addStaticResource(archive, "src/main/fabric8/deployment.yml");
        addStaticResource(archive, "src/main/java/io/example/openapi/Application.java");

        String camelContextXML = generateCamelContextXML(variables);
        archive.add(new StringAsset(camelContextXML), "src/main/resources/spring/camel-context.xml");

        archive.add(new StringAsset(Json.pretty(swagger)), "src/main/resources/openapi.json");

        return archive.as(ZipExporter.class).exportAsInputStream();
    }

    public String generateCamelContextXML(HashMap<String, Object> variables) throws Exception {

        Swagger swagger = (Swagger) variables.get("swagger");
        final CamelContext camel = new DefaultCamelContext();
        String restXMLString = RestDslGenerator.toXml(swagger).generate(camel);

        Document restXML = parseXmlFrom(restXMLString);
        NodeList restElements = restXML.getElementsByTagName("rest");
        if (restElements.getLength() != 1) {
            throw new IllegalStateException("Expected only one `rest` element to be generated from the OpenAPI specification");
        }

        Element rest = (Element) restElements.item(0);
        rest.setAttribute("id", "rest-" + generateuuid.apply(null));
        rest.setAttribute("bindingMode", "json");
        rest.setAttribute("enableCORS", "true");

        String serialized = serializeElement(rest);
        String idented = LINE_START.matcher(serialized).replaceAll("    ");
        variables.put("restXML", idented);

        // Extract the direct endpoint names..
        // <to uri="direct:updateUser"/>
        ArrayList<String> directEndpoints = new ArrayList<>();
        for (String line : restXMLString.split("\n")) {
            Matcher matcher = Pattern.compile(".*?<to uri=\"(direct:[^\"]+)\"/>.*", Pattern.DOTALL).matcher(line);
            if (matcher.matches()) {
                directEndpoints.add(matcher.group(1));
            }
        }
        variables.put("directEndpoints", directEndpoints);
        return renderTemplateResource("src/main/resources/spring/camel-context.xml", variables);
    }

    static private void addTemplateResource(GenericArchive archive, String fileName, Object context) throws IOException {
        archive.add((Asset) () -> {
            try {
                String templateResult = renderTemplateResource(fileName, context);
                return new ByteArrayInputStream(templateResult.getBytes(UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, fileName);
    }

    private static String renderTemplateResource(String templateResource, Object variables) throws IOException {
        MustacheFactory mf = new DefaultMustacheFactory();

        String template = readUTF8Resource("camel-project-template/" + templateResource);
        Mustache mustache = mf.compile(new StringReader(template), templateResource);

        StringWriter sw = new StringWriter();
        mustache.execute(new PrintWriter(sw), variables).flush();
        return sw.toString();
    }

    private static String readUTF8Resource(String resourceName) throws IOException {
        if (resourceName == null) {
            return null;
        }
        try (InputStream is = GenerateFuseProjectResource.class.getClassLoader().getResourceAsStream(resourceName)) {
            return readUTF8(is);
        }
    }

    private static String readUTF8(InputStream is) throws IOException {
        if (is == null) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(is, baos);
        return new String(baos.toByteArray(), UTF_8);
    }

    static private void addStaticResource(GenericArchive archive, String fileName) {
        archive.add(new ClassLoaderAsset("camel-project-template/" + fileName), fileName);
    }

    private static Document parseXmlFrom(String xml) {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        //documentBuilderFactory.setNamespaceAware(true);
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

            return documentBuilder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("The given xml cannot be parsed", e);
        }
    }

    private String serializeElement(Element rest) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter serialized = new StringWriter();
        transformer.transform(new DOMSource(rest), new StreamResult(serialized));

        return serialized.toString();
    }

    public Swagger cleanupSwaggerFile(Swagger swagger) throws Exception {
        if (swagger != null) {
            swagger.setHost(null);
            swagger.setSchemes(null);
        }
        return swagger;
    }
}
