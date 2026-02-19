package uk.ac.ebi.spot.ols.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A servlet filter that injects the server icon into MCP InitializeResult responses.
 * 
 * The MCP specification (draft) supports an `icons` field in the Implementation interface
 * for serving server icons as base64-encoded data URIs. Since the Java SDK doesn't yet
 * support this field natively, this filter modifies the JSON response to add it.
 * 
 * @see <a href="https://modelcontextprotocol.io/specification/draft/schema#icon">MCP Icon Specification</a>
 */
@Component
public class McpIconFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(McpIconFilter.class);
    
    private static final String ICON_PATH = "static/icon-small.png";
    
    private String iconDataUri;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        loadIcon();
    }

    private void loadIcon() {
        try {
            ClassPathResource resource = new ClassPathResource(ICON_PATH);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    byte[] iconBytes = is.readAllBytes();
                    String base64Icon = Base64.getEncoder().encodeToString(iconBytes);
                    iconDataUri = "data:image/png;base64," + base64Icon;
                    logger.info("MCP server icon loaded successfully ({} bytes)", iconBytes.length);
                }
            } else {
                logger.warn("MCP server icon not found at classpath:{}", ICON_PATH);
            }
        } catch (IOException e) {
            logger.error("Failed to load MCP server icon", e);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // Only intercept POST initialize requests to inject the server icon.
        // Other MCP requests (tools/list, tools/call, etc.) use SSE streaming
        // which must not be buffered, so we let them pass through directly.
        if (iconDataUri != null && "POST".equalsIgnoreCase(httpRequest.getMethod())) {
            // Wrap the request so we can read the body without consuming it
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(httpRequest);
            String body = cachedRequest.getCachedBody();
            
            if (body.contains("\"method\"") && body.contains("\"initialize\"")
                    && !body.contains("\"notifications/initialized\"")) {
                // This is an initialize request - wrap response to inject icon
                ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);
                
                chain.doFilter(cachedRequest, responseWrapper);
                
                // Get the response content
                byte[] content = responseWrapper.getContentAsByteArray();
                String responseBody = new String(content, StandardCharsets.UTF_8);
                
                // Inject icon into initialize response
                if (responseBody.contains("\"serverInfo\"") && responseBody.contains("\"protocolVersion\"")) {
                    responseBody = injectIconsIntoResponse(responseBody);
                }
                
                // Write the (potentially modified) response
                byte[] modifiedContent = responseBody.getBytes(StandardCharsets.UTF_8);
                httpResponse.setContentType(responseWrapper.getContentType());
                httpResponse.setContentLength(modifiedContent.length);
                httpResponse.getOutputStream().write(modifiedContent);
                httpResponse.getOutputStream().flush();
                return;
            }
            
            // Not an initialize request - pass through with cached request
            chain.doFilter(cachedRequest, response);
            return;
        }
        
        chain.doFilter(request, response);
    }

    /**
     * An HttpServletRequest wrapper that caches the request body so it can be
     * read multiple times (once for inspection, once by the downstream handler).
     */
    private static class CachedBodyHttpServletRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] cachedBody;
        
        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }
        
        public String getCachedBody() {
            return new String(cachedBody, StandardCharsets.UTF_8);
        }
        
        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() { return bais.read(); }
                
                @Override
                public int read(byte[] b, int off, int len) { return bais.read(b, off, len); }
                
                @Override
                public boolean isFinished() { return bais.available() == 0; }
                
                @Override
                public boolean isReady() { return true; }
                
                @Override
                public void setReadListener(ReadListener listener) { }
            };
        }
        
        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Injects the icons array into the serverInfo object of an MCP response.
     * 
     * The MCP specification defines Icon as:
     * {
     *   src: string;      // data: URI with Base64-encoded image data
     *   mimeType?: string;
     *   sizes?: string[];
     *   theme?: "light" | "dark";
     * }
     */
    private String injectIconsIntoResponse(String responseBody) {
        // Build the icons array according to MCP spec
        String iconsJson = String.format(
            "\"icons\":[{\"src\":\"%s\",\"mimeType\":\"image/png\",\"sizes\":[\"64x64\"]}]",
            iconDataUri
        );
        
        // Find the serverInfo object and inject icons into it
        // Look for "serverInfo":{...} and add icons inside it
        int serverInfoStart = responseBody.indexOf("\"serverInfo\"");
        if (serverInfoStart == -1) {
            return responseBody;
        }
        
        // Find the opening brace after "serverInfo":
        int braceStart = responseBody.indexOf("{", serverInfoStart);
        if (braceStart == -1) {
            return responseBody;
        }
        
        // Inject icons as the first property inside serverInfo
        StringBuilder modified = new StringBuilder();
        modified.append(responseBody.substring(0, braceStart + 1));
        modified.append(iconsJson);
        
        // Check if there are more properties after the opening brace
        String remaining = responseBody.substring(braceStart + 1);
        if (!remaining.trim().startsWith("}")) {
            modified.append(",");
        }
        modified.append(remaining);
        
        String result = modified.toString();
        logger.debug("Injected MCP server icon into InitializeResult response");
        return result;
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    /**
     * A simple response wrapper that captures the response content.
     */
    private static class ContentCachingResponseWrapper extends HttpServletResponseWrapper {
        
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        public ContentCachingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (outputStream == null) {
                outputStream = new CachingServletOutputStream(buffer);
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8));
            }
            return writer;
        }

        public byte[] getContentAsByteArray() {
            if (writer != null) {
                writer.flush();
            }
            return buffer.toByteArray();
        }

        private static class CachingServletOutputStream extends ServletOutputStream {
            private final ByteArrayOutputStream buffer;

            public CachingServletOutputStream(ByteArrayOutputStream buffer) {
                this.buffer = buffer;
            }

            @Override
            public void write(int b) throws IOException {
                buffer.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // Not implemented for synchronous operation
            }
        }
    }
}
