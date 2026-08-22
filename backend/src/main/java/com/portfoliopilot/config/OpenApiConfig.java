package com.portfoliopilot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger UI at {@code /swagger-ui.html}, OpenAPI JSON at {@code /v3/api-docs}.
 *
 * <p>Registers the bearer scheme globally so the "Authorize" button works: paste
 * the {@code accessToken} from {@code POST /api/auth/login} and every protected
 * endpoint becomes callable from the browser.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI portfolioPilotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PortfolioPilot AI API")
                        .version("1.0.0")
                        .description("""
                                Backend for PortfolioPilot AI - "Build a portfolio that adapts to the opportunity."

                                **Core flow**
                                `ONE PROFILE -> MANY OPPORTUNITIES -> MATCH ANALYSIS -> TAILORED RESUME
                                 -> ADAPTIVE PORTFOLIO -> PUBLISHED PORTFOLIO`

                                **Authentication**
                                1. `POST /api/auth/register` or `POST /api/auth/login`
                                2. Copy `data.accessToken` from the response
                                3. Click **Authorize** above and paste it
                                4. Access tokens last 15 minutes; use `POST /api/auth/refresh` to renew

                                **Response envelope** - every endpoint returns:
                                ```json
                                { "success": true, "message": "...", "data": { } }
                                ```

                                **Ownership** - user-scoped resources are always filtered by the
                                authenticated user id taken from the token. A `userId` in a request
                                body is ignored. Requesting another user's resource returns 404, not
                                403, so ids cannot be probed.
                                """)
                        .contact(new Contact().name("PortfolioPilot AI"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local development")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the raw access token. Swagger adds the 'Bearer ' prefix.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
