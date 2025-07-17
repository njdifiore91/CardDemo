package com.carddemo.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {
  
  @Bean
  public OpenAPI api() {
    return new OpenAPI()
      .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
      .components(new Components()
        .addSecuritySchemes("bearerAuth",
          new io.swagger.v3.oas.models.security.SecurityScheme()
            .type(Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
        )
      );
  }
}
