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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.fuse.apicurio.Application;

import io.swagger.annotations.Api;

/**
 * Implements a jaxrs resource that can be used by Apicurito to
 * generate Camel projects from an openapi spec.
 */
@Path("/generate")
@Api(value = "generate")
@Component
public class GenerateFuseProjectResource {
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    /**
     * Generate an example zip file containing a camel project for an example
     * openapi spec.
     */
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path(value = "/camel-project.zip")
    public InputStream generate() throws IOException {
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
    public InputStream generate(InputStream openapiDoc) throws IOException {

        HashMap hashMap = new ObjectMapper().readValue(openapiDoc, HashMap.class);
        Object swagger = hashMap.get("swagger");
        if (swagger != null) {
            LOG.info("Working with Swagger: {}", swagger);
        }

        GenericArchive archive = ShrinkWrap.create(GenericArchive.class, "myarchive.zip");
        archive.add(new StringAsset("Hello"), "readme.txt");

        // Todo: add the rest of a camel project to the archive.

        return archive.as(ZipExporter.class).exportAsInputStream();
    }

}
