# Camel based implementation of the _{{title}}_ API

## API Description ##
{{swagger.info.description}}

### Building

    mvn clean package

### Running Locally

    mvn spring-boot:run

Getting the API docs:

    curl http://localhost:8080/openapi.json

## Running on OpenShift

    mvn fabric8:deploy

