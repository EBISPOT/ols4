package uk.ac.ebi.spot.ols.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.servlet.ServletContext;

@Configuration
public class SwaggerConfig {

    private final String OLS4_SERVER_URL = "https://api.terminology.tib.eu";

    @Lazy
    @Bean
    public OpenAPI customOpenApi(ServletContext context) {
        String serverUrl = context.getContextPath().equals("") ? OLS4_SERVER_URL : context.getContextPath();
        return new OpenAPI()
                .addServersItem(new Server().url(serverUrl))
                .info(new Info()
                        .title("TIB Terminology Service")
                        .description("REST API for OLS4. Please see <a href='https://www.ebi.ac.uk/ols4/defined-response-fields' target='_blank'> this page</a> for defined response field in OLS.")
                        .version("4.0")
                        .termsOfService("https://www.tib.eu/en/terms-of-use")
                        .license(new License()
                                .name("CC0 1.0 Universal (CC0 1.0) Public Domain Dedication")
                                .url("https://creativecommons.org/publicdomain/zero/1.0/")
                        )
                );
    }

}

