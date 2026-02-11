
package uk.ac.ebi.spot.ols;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import uk.ac.ebi.spot.ols.controller.mcp.McpClassService;
import uk.ac.ebi.spot.ols.controller.mcp.McpEmbeddingService;
import uk.ac.ebi.spot.ols.controller.mcp.McpOntologyService;
import uk.ac.ebi.spot.ols.controller.mcp.McpSearchService;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration.class,
    org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration.class
})
public class Ols4Backend {

    public static void main(String[] args) {
        SpringApplication.run(Ols4Backend.class, args);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins("*").allowedHeaders("*").allowedMethods("GET", "POST", "DELETE", "OPTIONS");
            }
        };
    }

	@Bean
	public ToolCallbackProvider mcpOntologyTools(McpOntologyService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
	}
	@Bean
	public ToolCallbackProvider mcpClassTools(McpClassService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
	}
	@Bean
	public ToolCallbackProvider mcpSearchTools(McpSearchService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
	}
	@Bean
	public ToolCallbackProvider mcpEmbeddingTools(McpEmbeddingService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
	}
}
