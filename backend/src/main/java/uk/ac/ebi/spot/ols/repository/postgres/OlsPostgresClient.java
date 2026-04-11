package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class OlsPostgresClient {

    @Autowired
    PostgresClient postgresClient;

    Gson gson = new Gson();

    private static final Logger logger = LoggerFactory.getLogger(OlsPostgresClient.class);

    // Hierarchy edge types that use materialized ancestor arrays
    private static final Set<String> DIRECT_PARENT_EDGES = Set.of("directParent");
    private static final Set<String> HIERARCHICAL_PARENT_EDGES = Set.of("hierarchicalParent");

    public long getDatabaseNodeCount() {
        return postgresClient.returnNodeCount();
    }

    public Page<JsonElement> getAll(String type, Map<String, String> properties, Pageable pageable) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("type = ?");
        params.add(type);

        for (var entry : properties.entrySet()) {
            if ("id".equals(entry.getKey())) {
                where.append(" AND id = ?");
                params.add(entry.getValue());
            } else if ("iri".equals(entry.getKey())) {
                where.append(" AND iri = ?");
                params.add(entry.getValue());
            } else if ("ontologyId".equals(entry.getKey())) {
                where.append(" AND ontology_id = ?");
                params.add(entry.getValue());
            }
        }

        String sql = "SELECT _json, iri FROM ols_entities WHERE " + where;
        String countSql = "SELECT count(*) FROM ols_entities WHERE " + where;

        return postgresClient.queryPaginated(sql, countSql, params.toArray(), pageable);
    }

    public JsonElement getOne(String type, Map<String, String> properties) {
        Page<JsonElement> results = getAll(type, properties, PageRequest.of(0, 10));

        if (results.getTotalElements() != 1) {
            throw new RuntimeException("expected exactly one result for getOne, but got " + results.getTotalElements());
        }

        return results.getContent().iterator().next();
    }

    public Page<JsonElement> traverseOutgoingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> targetNodeProps, Pageable pageable) {

        // Check if this is a hierarchy traversal using edges table
        // (directParent/hierarchicalParent edges exist in the edges table)
        String sql = buildEdgeTraversalSql(id, edgeIRIs, edgeProps, targetNodeProps, true);
        String countSql = buildEdgeTraversalCountSql(id, edgeIRIs, edgeProps, targetNodeProps, true);
        Object[] params = buildEdgeTraversalParams(id, edgeIRIs, edgeProps, targetNodeProps);

        return postgresClient.queryPaginated(sql, countSql, params, pageable);
    }

    public Page<JsonElement> traverseIncomingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> sourceNodeProps, Pageable pageable, String searchQuery) {

        String sql = buildEdgeTraversalSql(id, edgeIRIs, edgeProps, sourceNodeProps, false);
        String countSql = buildEdgeTraversalCountSql(id, edgeIRIs, edgeProps, sourceNodeProps, false);

        // Build params (reuse shared helper for correct type conversion)
        Object[] baseParams = buildEdgeTraversalParams(id, edgeIRIs, edgeProps, sourceNodeProps);
        List<Object> paramList = new ArrayList<>(Arrays.asList(baseParams));

        // Add search query condition if present
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            sql += " AND EXISTS(SELECT 1 FROM unnest(e2.label) l WHERE lower(l) LIKE '%' || lower(?) || '%')";
            countSql += " AND EXISTS(SELECT 1 FROM unnest(e2.label) l WHERE lower(?) LIKE '%' || lower(?) || '%')";
            // Actually, the count and data queries need the same conditions
            // Let me rebuild properly

            paramList.add(searchQuery);
        }

        return postgresClient.queryPaginated(sql, countSql, paramList.toArray(), pageable);
    }

    public Page<JsonElement> traverseIncomingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> sourceNodeProps, Pageable pageable) {
        return traverseIncomingEdges(type, id, edgeIRIs, edgeProps, sourceNodeProps, pageable, null);
    }

    public Page<JsonElement> recursivelyTraverseOutgoingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> targetNodeProps, Pageable pageable) {

        // For hierarchy edges, use materialized ancestor arrays
        if (isDirectParentTraversal(edgeIRIs)) {
            return ancestorQuery(id, "direct_ancestors", targetNodeProps, pageable);
        } else if (isHierarchicalParentTraversal(edgeIRIs)) {
            return ancestorQuery(id, "hierarchical_ancestors", targetNodeProps, pageable);
        }

        // For non-hierarchy edges (e.g. rdf:type + rdfs:subClassOf), use recursive CTE
        return recursiveEdgeTraversal(id, edgeIRIs, edgeProps, targetNodeProps, true, pageable);
    }

    public Page<JsonElement> recursivelyTraverseIncomingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> sourceNodeProps, Pageable pageable) {

        // For hierarchy edges, use materialized ancestor arrays (reverse direction)
        if (isDirectParentTraversal(edgeIRIs)) {
            return descendantQuery(id, "direct_ancestors", sourceNodeProps, pageable);
        } else if (isHierarchicalParentTraversal(edgeIRIs)) {
            return descendantQuery(id, "hierarchical_ancestors", sourceNodeProps, pageable);
        }

        // For non-hierarchy edges, use recursive CTE
        return recursiveEdgeTraversal(id, edgeIRIs, edgeProps, sourceNodeProps, false, pageable);
    }


    // --- Hierarchy queries using materialized ancestor arrays ---

    private Page<JsonElement> ancestorQuery(String id, String ancestorColumn,
            Map<String, String> targetNodeProps, Pageable pageable) {
        // Get the entity's ancestor IRIs and ontology_id, then look up matching entities
        List<Object> params = new ArrayList<>();
        params.add(id);

        StringBuilder targetFilter = buildNodePropFilter("e2", targetNodeProps, params);

        String sql = "SELECT e2._json, e2.iri FROM ols_entities e1 "
                + "JOIN ols_entities e2 ON e2.iri = ANY(e1." + ancestorColumn + ") AND e2.ontology_id = e1.ontology_id "
                + "WHERE e1.id = ?" + targetFilter;
        String countSql = "SELECT count(*) FROM ols_entities e1 "
                + "JOIN ols_entities e2 ON e2.iri = ANY(e1." + ancestorColumn + ") AND e2.ontology_id = e1.ontology_id "
                + "WHERE e1.id = ?" + targetFilter;

        return postgresClient.queryPaginated(sql, countSql, params.toArray(), pageable);
    }

    private Page<JsonElement> descendantQuery(String id, String ancestorColumn,
            Map<String, String> sourceNodeProps, Pageable pageable) {
        // Find entities whose ancestor array contains my IRI, in the same ontology
        List<Object> params = new ArrayList<>();
        params.add(id);

        StringBuilder sourceFilter = buildNodePropFilter("e2", sourceNodeProps, params);

        String sql = "SELECT e2._json, e2.iri FROM ols_entities e1 "
                + "JOIN ols_entities e2 ON e1.iri = ANY(e2." + ancestorColumn + ") AND e2.ontology_id = e1.ontology_id "
                + "WHERE e1.id = ?" + sourceFilter;
        String countSql = "SELECT count(*) FROM ols_entities e1 "
                + "JOIN ols_entities e2 ON e1.iri = ANY(e2." + ancestorColumn + ") AND e2.ontology_id = e1.ontology_id "
                + "WHERE e1.id = ?" + sourceFilter;

        return postgresClient.queryPaginated(sql, countSql, params.toArray(), pageable);
    }


    // --- Edge table traversal ---

    private String buildEdgeTraversalSql(String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> nodeProps, boolean outgoing) {
        // outgoing: start_id = me, return end entity
        // incoming: end_id = me, return start entity
        String meColumn = outgoing ? "e.start_id" : "e.end_id";
        String otherColumn = outgoing ? "e.end_id" : "e.start_id";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT e2._json, e2.iri FROM ols_edges e ");
        sql.append("JOIN ols_entities e2 ON ").append(otherColumn).append(" = e2.id ");
        sql.append("WHERE ").append(meColumn).append(" = ? AND e.type = ANY(?)");

        for (var entry : edgeProps.entrySet()) {
            sql.append(" AND ? = ANY(e.property)");
        }

        for (var entry : nodeProps.entrySet()) {
            if ("isObsolete".equals(entry.getKey())) {
                sql.append(" AND e2.is_obsolete = ?");
            }
        }

        return sql.toString();
    }

    private String buildEdgeTraversalCountSql(String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> nodeProps, boolean outgoing) {
        String meColumn = outgoing ? "e.start_id" : "e.end_id";
        String otherColumn = outgoing ? "e.end_id" : "e.start_id";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT count(DISTINCT e2.id) FROM ols_edges e ");
        sql.append("JOIN ols_entities e2 ON ").append(otherColumn).append(" = e2.id ");
        sql.append("WHERE ").append(meColumn).append(" = ? AND e.type = ANY(?)");

        for (var entry : edgeProps.entrySet()) {
            sql.append(" AND ? = ANY(e.property)");
        }

        for (var entry : nodeProps.entrySet()) {
            if ("isObsolete".equals(entry.getKey())) {
                sql.append(" AND e2.is_obsolete = ?");
            }
        }

        return sql.toString();
    }

    private Object[] buildEdgeTraversalParams(String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> nodeProps) {
        List<Object> params = new ArrayList<>();
        params.add(id);
        params.add(edgeIRIs.toArray(String[]::new));

        for (var entry : edgeProps.entrySet()) {
            params.add(entry.getValue());
        }

        for (var entry : nodeProps.entrySet()) {
            if ("isObsolete".equals(entry.getKey())) {
                params.add("true".equals(entry.getValue()));
            }
        }

        return params.toArray();
    }


    // --- Recursive CTE for non-hierarchy multi-hop edges ---

    private Page<JsonElement> recursiveEdgeTraversal(String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> nodeProps, boolean outgoing, Pageable pageable) {

        String sql = "WITH RECURSIVE traverse AS ("
                + "  SELECT " + (outgoing ? "end_id" : "start_id") + " AS target_id FROM ols_edges "
                + "  WHERE " + (outgoing ? "start_id" : "end_id") + " = ? AND type = ANY(?)"
                + "  UNION ALL"
                + "  SELECT e." + (outgoing ? "end_id" : "start_id") + " FROM traverse t "
                + "  JOIN ols_edges e ON " + (outgoing ? "e.start_id" : "e.end_id") + " = t.target_id"
                + "  WHERE e.type = ANY(?)"
                + ") "
                + "SELECT DISTINCT e._json, e.iri FROM ols_entities e WHERE e.id IN (SELECT DISTINCT target_id FROM traverse)";

        String countSql = "WITH RECURSIVE traverse AS ("
                + "  SELECT " + (outgoing ? "end_id" : "start_id") + " AS target_id FROM ols_edges "
                + "  WHERE " + (outgoing ? "start_id" : "end_id") + " = ? AND type = ANY(?)"
                + "  UNION ALL"
                + "  SELECT e." + (outgoing ? "end_id" : "start_id") + " FROM traverse t "
                + "  JOIN ols_edges e ON " + (outgoing ? "e.start_id" : "e.end_id") + " = t.target_id"
                + "  WHERE e.type = ANY(?)"
                + ") "
                + "SELECT count(DISTINCT target_id) FROM traverse";

        String[] edgeTypes = edgeIRIs.toArray(String[]::new);
        Object[] params = new Object[]{ id, edgeTypes, edgeTypes };
        Object[] countParams = new Object[]{ id, edgeTypes, edgeTypes };

        return postgresClient.queryPaginated(sql, countSql, params, pageable);
    }


    // --- Node property filters ---

    private StringBuilder buildNodePropFilter(String alias, Map<String, String> nodeProps, List<Object> params) {
        StringBuilder filter = new StringBuilder();
        for (var entry : nodeProps.entrySet()) {
            if ("isObsolete".equals(entry.getKey())) {
                filter.append(" AND ").append(alias).append(".is_obsolete = ?");
                params.add("true".equals(entry.getValue()));
            }
        }
        return filter;
    }

    private boolean isDirectParentTraversal(List<String> edgeIRIs) {
        return edgeIRIs.size() == 1 && DIRECT_PARENT_EDGES.contains(edgeIRIs.get(0));
    }

    private boolean isHierarchicalParentTraversal(List<String> edgeIRIs) {
        return edgeIRIs.size() == 1 && HIERARCHICAL_PARENT_EDGES.contains(edgeIRIs.get(0));
    }


    // --- Vector search methods ---

    public static class SimilarResult {
        public JsonElement entity;
        public double score;
    }

    public Page<JsonElement> getSimilar(String type, String iri, Pageable pageable, String modelName) {
        String embeddingColumn = "\"embeddings_" + modelName + "\"";

        // Find the defining entity's embedding, then search for similar
        String sql = "SELECT e2._json, (1 - (e1." + embeddingColumn + " <=> e2." + embeddingColumn + ")) AS score "
                + "FROM ols_entities e1, ols_entities e2 "
                + "WHERE e1.iri = ? AND e1.type = ? AND e1.is_obsolete = false "
                + "AND ARRAY['true'] <@ e1.\"isDefiningOntology\" IS NOT TRUE "  // skip this check, use the column
                + "AND e1." + embeddingColumn + " IS NOT NULL "
                + "AND e2.type = ? AND e2." + embeddingColumn + " IS NOT NULL "
                + "AND e2.id != e1.id "
                + "ORDER BY e1." + embeddingColumn + " <=> e2." + embeddingColumn + " "
                + "LIMIT ?";

        // Simplified: use HNSW index for nearest neighbor
        String nnSql = "WITH source AS ("
                + "  SELECT " + embeddingColumn + " AS vec FROM ols_entities "
                + "  WHERE iri = ? AND type = ? AND " + embeddingColumn + " IS NOT NULL "
                + "  LIMIT 1"
                + ") "
                + "SELECT e._json, (1 - (e." + embeddingColumn + " <=> s.vec)) AS score "
                + "FROM ols_entities e, source s "
                + "WHERE e.type = ? AND e." + embeddingColumn + " IS NOT NULL "
                + "ORDER BY e." + embeddingColumn + " <=> s.vec "
                + "LIMIT ?";

        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(nnSql)) {
            stmt.setString(1, iri);
            stmt.setString(2, type);
            stmt.setString(3, type);
            stmt.setInt(4, pageable.getPageSize());

            List<JsonElement> results = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var json = JsonParser.parseString(PostgresClient.decompressJson(rs, "_json")).getAsJsonObject();
                    json.addProperty("score", rs.getDouble("score"));
                    results.add(json);
                }
            }
            return new PageImpl<>(results, pageable, results.size());
        } catch (SQLException e) {
            throw new RuntimeException("getSimilar failed", e);
        }
    }

    public double getSimilarity(String type, String iri, String iri2, String modelName) {
        String embeddingColumn = "\"embeddings_" + modelName + "\"";

        String sql = "SELECT 1 - (a." + embeddingColumn + " <=> b." + embeddingColumn + ") AS score "
                + "FROM ols_entities a, ols_entities b "
                + "WHERE a.iri = ? AND a.type = ? AND a." + embeddingColumn + " IS NOT NULL "
                + "AND b.iri = ? AND b.type = ? AND b." + embeddingColumn + " IS NOT NULL "
                + "LIMIT 1";

        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, iri);
            stmt.setString(2, type);
            stmt.setString(3, iri2);
            stmt.setString(4, type);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("score");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("getSimilarity failed", e);
        }
        throw new ResourceNotFoundException("entity not found");
    }

    public List<Double> getEmbeddingVector(String type, String iri, String modelName) {
        String embeddingColumn = "\"embeddings_" + modelName + "\"";

        String sql = "SELECT " + embeddingColumn + "::text AS embeddings FROM ols_entities "
                + "WHERE iri = ? AND type = ? AND " + embeddingColumn + " IS NOT NULL LIMIT 1";

        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, iri);
            stmt.setString(2, type);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String vecStr = rs.getString("embeddings");
                    // Parse pgvector [0.1,0.2,...] format
                    vecStr = vecStr.trim();
                    if (vecStr.startsWith("[")) vecStr = vecStr.substring(1);
                    if (vecStr.endsWith("]")) vecStr = vecStr.substring(0, vecStr.length() - 1);
                    return Arrays.stream(vecStr.split(","))
                            .map(String::trim)
                            .map(Double::parseDouble)
                            .collect(Collectors.toList());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("getEmbeddingVector failed", e);
        }
        throw new ResourceNotFoundException("entity not found");
    }

    public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable, String modelName) {
        return searchByVector(type, vector, pageable, modelName, true);
    }

    public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable, String modelName, boolean includeCurations) {
        String embeddingColumn = "\"embedding_" + modelName + "\"";
        int limit = pageable.getPageSize();

        // Build vector literal
        String vecLiteral = "[" + vector.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT en._json, min_dist AS score FROM (");

        // Label embeddings
        sql.append("  SELECT en.id, en._json, emb.").append(embeddingColumn).append(" <=> ?::vector AS min_dist ");
        sql.append("  FROM ols_embedding_nodes emb ");
        sql.append("  JOIN ols_entities en ON emb.entity_id = en.id ");
        sql.append("  WHERE emb.type = 'LabelEmbedding' AND emb.").append(embeddingColumn).append(" IS NOT NULL ");
        sql.append("  AND en.type = ? ");

        if (includeCurations) {
            sql.append("  UNION ALL ");
            sql.append("  SELECT en.id, en._json, emb.").append(embeddingColumn).append(" <=> ?::vector AS min_dist ");
            sql.append("  FROM ols_embedding_nodes emb ");
            sql.append("  JOIN ols_entities en ON emb.entity_id = en.id ");
            sql.append("  WHERE emb.type = 'CurationEmbedding' AND emb.").append(embeddingColumn).append(" IS NOT NULL ");
            sql.append("  AND en.type = ? ");
        }

        sql.append(") sub GROUP BY sub.id, sub._json ORDER BY MIN(min_dist) ASC LIMIT ?");

        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            stmt.setObject(idx++, vecLiteral, Types.OTHER);
            stmt.setString(idx++, type);
            if (includeCurations) {
                stmt.setObject(idx++, vecLiteral, Types.OTHER);
                stmt.setString(idx++, type);
            }
            stmt.setInt(idx++, limit);

            List<JsonElement> results = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var json = JsonParser.parseString(PostgresClient.decompressJson(rs, "_json")).getAsJsonObject();
                    json.addProperty("score", 1.0 - rs.getDouble("score"));
                    results.add(json);
                }
            }
            return new PageImpl<>(results, pageable, results.size());
        } catch (SQLException e) {
            throw new RuntimeException("searchByVector failed", e);
        }
    }

    public Page<JsonElement> searchByVectorInOntology(String type, List<Double> vector, Pageable pageable,
            String modelName, String ontologyId, boolean isDefiningOntology) {
        return searchByVectorInOntology(type, vector, pageable, modelName, ontologyId, isDefiningOntology, true);
    }

    public Page<JsonElement> searchByVectorInOntology(String type, List<Double> vector, Pageable pageable,
            String modelName, String ontologyId, boolean isDefiningOntology, boolean includeCurations) {
        String embeddingColumn = "\"embedding_" + modelName + "\"";
        int limit = pageable.getPageSize();
        String vecLiteral = "[" + vector.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";

        StringBuilder sql = new StringBuilder();

        if (isDefiningOntology) {
            sql.append("SELECT sub._json, MIN(sub.dist) AS score FROM (");
            sql.append("  SELECT en._json, en.id, emb.").append(embeddingColumn).append(" <=> ?::vector AS dist ");
            sql.append("  FROM ols_embedding_nodes emb ");
            sql.append("  JOIN ols_entities en ON emb.entity_id = en.id ");
            sql.append("  WHERE emb.type = 'LabelEmbedding' AND emb.").append(embeddingColumn).append(" IS NOT NULL ");
            sql.append("  AND en.type = ? AND en.ontology_id = ? ");

            if (includeCurations) {
                sql.append("  UNION ALL ");
                sql.append("  SELECT en._json, en.id, emb.").append(embeddingColumn).append(" <=> ?::vector AS dist ");
                sql.append("  FROM ols_embedding_nodes emb ");
                sql.append("  JOIN ols_entities en ON emb.entity_id = en.id ");
                sql.append("  WHERE emb.type = 'CurationEmbedding' AND emb.").append(embeddingColumn).append(" IS NOT NULL ");
                sql.append("  AND en.type = ? AND en.ontology_id = ? ");
            }

            sql.append(") sub GROUP BY sub.id, sub._json ORDER BY MIN(sub.dist) ASC LIMIT ?");
        } else {
            // Non-defining: find defining entity, then match target entity by IRI in the target ontology
            sql.append("SELECT sub._json, MIN(sub.dist) AS score FROM (");
            sql.append("  SELECT target._json, target.id, emb.").append(embeddingColumn).append(" <=> ?::vector AS dist ");
            sql.append("  FROM ols_embedding_nodes emb ");
            sql.append("  JOIN ols_entities defining ON emb.entity_id = defining.id ");
            sql.append("  JOIN ols_entities target ON target.iri = defining.iri AND target.type = defining.type ");
            sql.append("  WHERE emb.type = 'LabelEmbedding' AND emb.").append(embeddingColumn).append(" IS NOT NULL ");
            sql.append("  AND target.ontology_id = ? ");

            if (includeCurations) {
                sql.append("  UNION ALL ");
                sql.append("  SELECT target._json, target.id, emb.").append(embeddingColumn).append(" <=> ?::vector AS dist ");
                sql.append("  FROM ols_embedding_nodes emb ");
                sql.append("  JOIN ols_entities defining ON emb.entity_id = defining.id ");
                sql.append("  JOIN ols_entities target ON target.iri = defining.iri AND target.type = defining.type ");
                sql.append("  WHERE emb.type = 'CurationEmbedding' AND emb.").append(embeddingColumn).append(" IS NOT NULL ");
                sql.append("  AND target.ontology_id = ? ");
            }

            sql.append(") sub GROUP BY sub.id, sub._json ORDER BY MIN(sub.dist) ASC LIMIT ?");
        }

        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (isDefiningOntology) {
                stmt.setObject(idx++, vecLiteral, Types.OTHER);
                stmt.setString(idx++, type);
                stmt.setString(idx++, ontologyId.toLowerCase());
                if (includeCurations) {
                    stmt.setObject(idx++, vecLiteral, Types.OTHER);
                    stmt.setString(idx++, type);
                    stmt.setString(idx++, ontologyId.toLowerCase());
                }
            } else {
                stmt.setObject(idx++, vecLiteral, Types.OTHER);
                stmt.setString(idx++, ontologyId.toLowerCase());
                if (includeCurations) {
                    stmt.setObject(idx++, vecLiteral, Types.OTHER);
                    stmt.setString(idx++, ontologyId.toLowerCase());
                }
            }
            stmt.setInt(idx++, limit);

            List<JsonElement> results = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var json = JsonParser.parseString(PostgresClient.decompressJson(rs, "_json")).getAsJsonObject();
                    json.addProperty("score", 1.0 - rs.getDouble("score"));
                    results.add(json);
                }
            }
            return new PageImpl<>(results, pageable, results.size());
        } catch (SQLException e) {
            throw new RuntimeException("searchByVectorInOntology failed", e);
        }
    }

    public List<String> getEmbeddingModels() {
        String sql = "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'ols_entities' AND column_name LIKE 'embeddings\\_%'";

        List<String> models = new ArrayList<>();
        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String colName = rs.getString("column_name");
                String modelName = colName.substring("embeddings_".length());
                if (!modelName.contains("pca16")) {
                    models.add(modelName);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("getEmbeddingModels failed", e);
        }
        return models;
    }
}
