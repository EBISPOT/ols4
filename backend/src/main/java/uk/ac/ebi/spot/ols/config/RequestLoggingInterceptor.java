package uk.ac.ebi.spot.ols.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;

@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Get memory info
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        StringBuilder params = new StringBuilder();
        Enumeration<String> parameterNames = request.getParameterNames();

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameter(paramName);
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append(paramName).append("=").append(paramValue);
        }

        logger.info("REQUEST START - {} {} - Params: [{}] - Memory: {}MB / {}MB ({}% used)",
            request.getMethod(),
            request.getRequestURI(),
            params.toString(),
            usedMemory / 1024 / 1024,
            maxMemory / 1024 / 1024,
            (usedMemory * 100) / maxMemory);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Get memory info after request
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        logger.info("REQUEST END - {} {} - Status: {} - Memory: {}MB / {}MB ({}% used)",
            request.getMethod(),
            request.getRequestURI(),
            response.getStatus(),
            usedMemory / 1024 / 1024,
            maxMemory / 1024 / 1024,
            (usedMemory * 100) / maxMemory);
    }
}
