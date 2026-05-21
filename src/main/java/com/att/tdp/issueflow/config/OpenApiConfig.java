package com.att.tdp.issueflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI 3.0 specification exposed at /v3/api-docs.
 * The Swagger UI is served at /swagger-ui.html.
 *
 * All secured endpoints require a Bearer JWT obtained from POST /api/auth/login.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI issueFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("IssueFlow API")
                        .description("""
                                Ticket Management Backend Platform - AT&T TDP 2026

                                **How to authenticate:**
                                1. `POST /api/users` - create a user
                                2. `POST /api/auth/login` - obtain a JWT
                                3. Click **Authorize** above and paste the token
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Aviad Alon")))
                // Apply the bearerAuth scheme globally to every secured endpoint
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT token returned by POST /api/auth/login")));
    }
}
