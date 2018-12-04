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
package com.redhat.fuse.apicurio.jaxrs.tests;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.redhat.fuse.apicurio.jaxrs.GenerateFuseProjectResource;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;

public class GenerateFuseProjectResourceTest {

    /**
     * Not the cleanest approach, but uses the same approach as the generator to
     * create the Rest DSL configuration and return it as a string.
     * 
     * @param filePath
     * @return
     * @throws Exception
     */
    private String getRestDSLXMLFromSwagger(String filePath) throws Exception {
        GenerateFuseProjectResource generator = new GenerateFuseProjectResource();
        final Swagger swagger = getSwaggerFile(filePath);
        assertNotNull(swagger);

        String title = "Example";
        if (swagger.getInfo() != null && swagger.getInfo().getTitle() != null) {
            title = swagger.getInfo().getTitle();
        }
        String artifactId = title.toLowerCase().replaceAll("[^a-zA-Z0-9-_]+", "-").replaceAll("-+", "-");

        HashMap<String, Object> variables = new HashMap<>();
        variables.put("swagger", swagger);
        variables.put("title", title);
        variables.put("artifactId", artifactId);
        variables.put("fuseVersion", GenerateFuseProjectResource.FUSE_VERSION);

        return generator.generateCamelContextXML(variables);
    }

    private Swagger getSwaggerFile(String filePath) throws Exception {
        File swaggerFile = new File(filePath);
        assertTrue(swaggerFile.exists());

        try (InputStream openapiDoc = new FileInputStream(swaggerFile)) {
            String rawText = readUTF8(openapiDoc);
            Swagger swagger = 
                new SwaggerParser().read(Json.mapper().readTree(rawText));
            return swagger;
        }
    }

    private Swagger getCleanedUpSwagger(String filePath) throws Exception {
        GenerateFuseProjectResource generator = new GenerateFuseProjectResource();
        final Swagger initialSwagger = getSwaggerFile(filePath);
        return generator.cleanupSwaggerFile(initialSwagger);
    }

    /**
     * Ensure with an good swagger file that we are clearing the XML headers on the
     * generated Rest DSL. See http://petstore.swagger.io/ for petstore example.
     * 
     * @throws Exception
     */
    @Test
    public void testForDuplicateXMLHeadersInGeneratedRestDSLOutput() throws Exception {
        String camelContextXML = getRestDSLXMLFromSwagger("src/test/resources/petstore-swagger.json");
        assertTrue(camelContextXML != null);
        assertTrue(camelContextXML.trim().length() > 0);
        int xmlHeaders = StringUtils.countMatches(camelContextXML, "<?xml version=\"1.0\" encoding=\"UTF-8\"");
        assertEquals(1, xmlHeaders);
    }

    @Test
    public void testForUniqueIDsInGeneratedRestDSLOutput() throws Exception {
        String camelContextXML = getRestDSLXMLFromSwagger("src/test/resources/petstore-swagger.json");
        assertTrue(camelContextXML != null);
        assertTrue(camelContextXML.trim().length() > 0);
        int placeholders = StringUtils.countMatches(camelContextXML, "{{uuid}}");
        assertEquals(0, placeholders);
    }

    @Test
    public void testForRestDSLOutputFromSimpleOpenAPIFile() throws Exception {
        String camelContextXML = getRestDSLXMLFromSwagger("src/test/resources/simple-swagger.json");
        assertTrue(camelContextXML != null);
        assertTrue(camelContextXML.trim().length() > 0);
    }

    @Test
    public void testForRemovedHostAndSchemesFromSwagger() throws Exception {
        Swagger swagger = getCleanedUpSwagger("src/test/resources/todo-swagger.json");
        assertNotNull(swagger);
        assertNull(swagger.getHost());
        assertNull(swagger.getSchemes());
    }

    @Test
    public void ensureThatCamelSpringXMLIsValid() throws Exception {
        String camelContextXML = getRestDSLXMLFromSwagger("src/test/resources/petstore-swagger.json");
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        StreamSource springBeansSchema = new StreamSource(GenerateFuseProjectResourceTest.class.getResourceAsStream("/spring-beans.xsd"));
        StreamSource camelSpringSchema = new StreamSource(GenerateFuseProjectResourceTest.class.getResourceAsStream("/camel-spring.xsd"));

        Schema schema = schemaFactory.newSchema(new Source[] { springBeansSchema, camelSpringSchema });
        Validator validator = schema.newValidator();
        validator.validate(new StreamSource(new StringReader(camelContextXML)));
    }

    private static String readUTF8(InputStream is) throws IOException {
        if (is == null) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(is, baos);
        return new String(baos.toByteArray(), UTF_8);
    }
}
