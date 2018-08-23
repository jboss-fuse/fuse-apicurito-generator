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
package com.redhat.fuse.apicurio.resources;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.camel.CamelContext;
import org.apache.camel.generator.swagger.RestDslGenerator;
import org.apache.camel.generator.swagger.RestDslXmlGenerator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.io.IOUtils;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.springframework.stereotype.Component;

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
            fuseVersion = "7.1.0.fuse-710019";
        }
        FUSE_VERSION = fuseVersion;
    }

    /**
     * Generate an example zip file containing a camel project for an example
     * openapi spec.
     */
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path(value = "/camel-project.zip")
    public InputStream generate() throws Exception {
        try (InputStream openapiDoc = getClass().getResourceAsStream("open-api-example.json")) {
            return generate(openapiDoc);
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
    public InputStream generate(InputStream openapiDoc) throws Exception {

        final Swagger swagger = new SwaggerParser().read(Json.mapper().readTree(openapiDoc));
        final CamelContext camel = new DefaultCamelContext();
        RestDslXmlGenerator restDslXmlGenerator = RestDslGenerator.toXml(swagger);
        final String xml = restDslXmlGenerator.generate(camel);
        System.out.println(xml);

        GenericArchive archive = ShrinkWrap.create(GenericArchive.class, "camel-project.zip");

        String title = "Example";
        if (swagger.getInfo() != null && swagger.getInfo().getTitle() != null) {
            title = swagger.getInfo().getTitle();
        }
        String artifactId = title.toLowerCase().replaceAll("[^a-zA-Z0-9-_]+", "-").replaceAll("-+", "-");

        HashMap<String, Object> temlateVariables = new HashMap<>();
        temlateVariables.put("swagger", swagger);
        temlateVariables.put("title", title);
        temlateVariables.put("artifactId", artifactId);
        temlateVariables.put("fuseVersion", FUSE_VERSION);

        addTemplateResource(archive, "README.md", temlateVariables);
        addTemplateResource(archive, "pom.xml", temlateVariables);
        addTemplateResource(archive, "src/main/resources/application.yml", temlateVariables);


        addStaticResource(archive, "configuration/settings.xml");
        addStaticResource(archive, "src/main/fabric8/deployment.yml");
        addStaticResource(archive, "src/main/fabric8/service.yml");
        addStaticResource(archive, "src/main/java/io/example/openapi/Application.java");

        // Todo: add the rest of a camel project to the archive.

        return archive.as(ZipExporter.class).exportAsInputStream();
    }

    private void addTemplateResource(GenericArchive archive, String fileName, Object context) throws IOException {
        archive.add((Asset) () -> {
            StringWriter sw = null;
            try {
                MustacheFactory mf = new DefaultMustacheFactory();

                String template = readUTF8Resource("camel-project-template/" + fileName);
                Mustache mustache = mf.compile(new StringReader(template), fileName);

                sw = new StringWriter();
                mustache.execute(new PrintWriter(sw), context).flush();
                String templateResult = sw.toString();
                return new ByteArrayInputStream(templateResult.getBytes(UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, fileName);
    }

    static private String readUTF8Resource(String resourceName) throws IOException {
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

}
