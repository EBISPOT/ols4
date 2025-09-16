package uk.ac.ebi.spot.ols.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Simon Jupp
 * @date 07/07/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class CrossOriginResourceSharingFilter implements Filter {
    private Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Alwasy add CORS headers. add CORS "pre-flight" request headers
        httpResponse.addHeader("Access-Control-Allow-Origin", "*");
        httpResponse.addHeader("Access-Control-Allow-Headers", "*");
        httpResponse.addHeader("Access-Control-Allow-Methods", "GET");
        httpResponse.addHeader("Access-Control-Max-Age", "3600");

        // is this a CORS request?
        if (httpRequest.getHeader("Origin") != null) {
            String origin = httpRequest.getHeader("Origin");
            String requestURI = httpRequest.getRequestURI();
            getLog().trace("Possible cross-origin request received from '" + origin + "' to IRI: " +
                                   "'" + requestURI + "'.  Enabling CORS.");
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}