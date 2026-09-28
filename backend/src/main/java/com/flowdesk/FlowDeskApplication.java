package com.flowdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the FlowDesk backend.
 *
 * <p>FlowDesk is built as a modular monolith: distinct business modules
 * (auth, user, organization, team, project, ticket, comment, notification,
 * dashboard, audit) live under {@code com.flowdesk.<module>} with their own
 * controller/service/repository/entity/dto layers, but are deployed and run
 * as a single Spring Boot application. See the project README for the
 * rationale behind this choice over a microservices split.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FlowDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowDeskApplication.class, args);
    }
}
