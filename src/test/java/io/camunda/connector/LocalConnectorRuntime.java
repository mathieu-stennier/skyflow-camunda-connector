package io.camunda.connector;

import io.camunda.connector.runtime.app.ConnectorRuntimeApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.AbstractEnvironment;

public class LocalConnectorRuntime {
    public static void main(String[] args) {
        // Comment this line if you are using the docker-compose.yml file instead of the
        // docker-compose-core.yml file
        System.setProperty(AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME, "local");
        SpringApplication.run(ConnectorRuntimeApplication.class, args);
    }
}
