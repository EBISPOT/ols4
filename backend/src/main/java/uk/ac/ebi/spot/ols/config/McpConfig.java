package uk.ac.ebi.spot.ols.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Configuration for MCP (Model Context Protocol) server customizations.
 * 
 * This configuration registers the icon filter for the MCP endpoint, which injects
 * server icon information into MCP InitializeResult responses as per the MCP specification.
 */
@Configuration
public class McpConfig {

    /**
     * Registers the MCP icon filter to intercept responses from the MCP endpoint.
     * The filter is set to run with high priority (low order number) to ensure
     * it wraps responses before they are sent to clients.
     */
    @Bean
    public FilterRegistrationBean<McpIconFilter> mcpIconFilterRegistration(McpIconFilter filter) {
        FilterRegistrationBean<McpIconFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/mcp", "/api/mcp/*");
        registration.setName("mcpIconFilter");
        // Run after authentication but before final response
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
