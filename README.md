# Fuse Apicurito Generator

This project implements Fuse specific project generators that the 
[Apicurito](https://github.com/Apicurio/apicurito) project can be 
configued to use.

# Building

    mvn clean install

# Running

    java -jar target/fuse-apicurito-generator-*-SNAPSHOT.jar

# Testing with Curl

Assuming you create an open OpenAPI file called `api.json`:

     curl -X POST -H "Content-Type: application/json" \
          -d @api.json http://localhost:8080/api/v1/generate/camel-project.zip \
          -o project.zip