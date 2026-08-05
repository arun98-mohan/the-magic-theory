package com.themagictheory.demo.api;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** Adds the Authorize button in Swagger UI for the X-API-Key header. */
@Configuration
@SecurityScheme(name = "ApiKey", type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER, paramName = ApiKeyFilter.API_KEY_HEADER)
@OpenAPIDefinition(security = @SecurityRequirement(name = "ApiKey"))
public class OpenApiConfig {
}
