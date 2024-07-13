
package uk.ac.ebi.spot.ols;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * TODO: We should consider caching responses that are static (such as statistics and ontologies loaded
 * for the ontologies in the ontologies tab) on startup of the OLS backend.
 *
 */
@SpringBootApplication
public class Ols4Backend {

    public static void main(String[] args) {
        SpringApplication.run(Ols4Backend.class, args);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins("*").allowedHeaders("*").allowedMethods("GET");
            }
        };
    }

}
