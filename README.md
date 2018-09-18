[![CircleCI](https://circleci.com/gh/jboss-fuse/fuse-apicurito-generator.svg?style=svg)](https://circleci.com/gh/jboss-fuse/fuse-apicurito-generator)

# Fuse Apicurito Generator

This project implements Fuse specific project generators that the
[Apicurito](https://github.com/Apicurio/apicurito) project can be
configured to use.

# Local Development
## Building

    mvn clean package

## Running

    java -jar target/fuse-apicurito-generator-*-SNAPSHOT.jar

## Testing with Curl

Assuming you create an open OpenAPI file called `api.json` and you want
to generate file called `example.zip`, then you would run the following:

     curl -s -X POST -H "Content-Type: application/json" \
          -d @api.json http://localhost:8080/api/v1/generate/camel-project.zip \
          -o example.zip

If you want to just test against the Swagger Petstore API, you run:

     curl -s http://localhost:8080/api/v1/generate/camel-project.zip \
          -o example.zip

See the `README.md` in zip file for more details about the generate project.

# OpenShift Development

## Building an Image

    mvn -P image

## Deploying to OpenShift

    mvn -P deployment

## Testing with Curl against OpenShift

Firstly lets figure out the hostname where the service is running:

    APPHOST=$(oc get route fuse-apicurito-generator --template={{.spec.host}})

Assuming you create an open OpenAPI file called `api.json` and you want
to generate file called `example.zip`, then you would run the following:

     curl -s -X POST -H "Content-Type: application/json" \
          -d @api.json http://$APPHOST/api/v1/generate/camel-project.zip \
          -o example.zip

If you want to just test against the Swagger Petstore API, you run:

     curl -s http://$APPHOST/api/v1/generate/camel-project.zip \
          -o example.zip

See the `README.md` in zip file for more details about the generate project.

## Building and Pushing the Image to Dockerhub

    mvn clean package -Pfabric8.mode=kubernetes fabric8:build
    docker push apicurio/fuse-apicurito-generator:latest
