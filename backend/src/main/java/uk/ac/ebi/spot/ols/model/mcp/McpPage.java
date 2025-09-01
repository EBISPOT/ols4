
package uk.ac.ebi.spot.ols.model.mcp;

import java.util.List;

public record McpPage<T>(
    List<T> items,
    int pageNum,
    int pageSize,
    long totalElements,
    int totalPages
) {}
