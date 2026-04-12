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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class OlsPostgresClient {

    private static final Pattern SAFE_MODEL_NAME = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private static String sanitizeEmbeddingColumn(String modelName) {
        if (modelName == null || !SAFE_MODEL_NAME.matcher(modelName).matches()) {
            throw new IllegalArgumentException("Invalid embedding model name: " + modelName);
        }
        return "\"embeddings_" + modelName + "\"";
    }

    private static String sanitizeEmbeddingNodeColumn(String modelName) {
        if (modelName == null || !SAFE_MODEL_NAME.matcher(modelName).matches()) {
            throw new IllegalArgumentException("Invalid embedding model name: " + modelName);
        }
        return "\"embedding_" + modelName + "\"";
    }

    @Autowired
    PostgresClient postgresClient;

    Gson gson = new Gson();

    private static final Logger logger = LoggerFactory.getLogger(OlsPostgresClient.class);

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

    // --- Semantic hierarchy queries ---

    public Page<JsonElement> getDirectParents(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "direct_parents", nodeProps, pageable);
    }

    public Page<JsonElement> getDirectChildren(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "direct_parents", nodeProps, pageable, null);
    }

    public Page<JsonElement> getDirectChildren(String id, Map<String, String> nodeProps, Pageable pageable, String search) {
        return lookupArraySources(id, "direct_parents", nodeProps, pageable, search);
    }

    public Page<JsonElement> getHierarchicalParents(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "hierarchical_parents", nodeProps, pageable);
    }

    public Page<JsonElement> getHierarchicalChildren(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "hierarchical_parents", nodeProps, pageable, null);
    }

    public Page<JsonElement> getAncestors(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "direct_ancestors", nodeProps, pageable);
    }

    public Page<JsonElement> getDescendants(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "direct_ancestors", nodeProps, pageable, null);
    }

    public Page<JsonElement> getHierarchicalAncestors(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "hierarchical_ancestors", nodeProps, pageable);
    }

    public Page<JsonElement> getHierarchicalDescendants(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "hierarchical_ancestors", nodeProps, pageable, null);
    }

    public Page<JsonElement> getRelatedTo(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "related_to", nodeProps, pageable);
    }

    public Page<JsonElement> getRelatedFrom(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "related_to", nodeProps, pageable, null);
    }


    // --- Private helpers ---

    private Page<JsonElement> lookupArrayTargets(String id, String column,
            Map<String, String> nodeProps, Pageable pageable) {
        List<Object> params = new ArrayList<>();
        params.add(id);

        StringBuilder filter = buildNodePropFilter("e2", nodeProps, params);

        String sql = "SELECT e2._json, e2.iri FROM ols_entities e1 "
                + "JOIN ols_entities e2 ON e2.iri = ANY(e1." + column + ") AND e2.ontology_id = e1.ontology_id "
                + "WHERE e1.id = ?" + filter;
        String countSql = "SELECT count(*) FROM ols_entities e1 "
                + "JOIN ols_entities e2 ON e2.iri = ANY(e1." + column + ") AND e2.ontology_id = e1.ontology_id "
                + "WHERE e1.id = ?" + filter;

        return postgresClient.queryPaginated(sql, countSql, params.toArray(), pageable);
    }

    private Page<JsonElement> lookupArraySources(String id, String column,
            Map<String, String> nodeProps, Pageable pageable, String search) {
        List<Object> params = new ArrayList<>();
        params.add(id);

        StringBuilder filter = buildNodePropFilter("e2", nodeProps, params);

        String searchFilter = "";
        if (search != null && !search.trim().isEmpty()) {
            searchFilter = " AND EXISTS(SELECT 1 FROM unnest(e2.label) l WHERE lower(l) LIKE '%' || lower(?) || '%')";
            params.add(search);
        }

        String sql = "SELECT e2._json, e2.iri FROM ols_entities e1 "
                + "JOIN ols_entities e2 ON e1.iri = ANY(e2." + column + ") AND e2.ontology_id = e1.ontology_id "
                + "WHERE e1.id = ?" + filter + searchFilter;
        String countSql = "SELECT count(*) FROM ols_entities e1 "
                + "JOIN ols_entities e2 ON e1.iri = ANY(e2." + column + ") AND e2.ontology_id = e1.ontology_id "
                + "WHERE e1.id = ?" + filter + searchFilter;

        return postgresClient.queryPaginated(sql, countSql, params.toArray(), pageable);
    }

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


    // --- Vector search methods ---

    public static class SimilarResult {
        public JsonElement entity;
        public double score;
    }

    public Page<JsonElement> getSimilar(String type, String iri, Pageable pageable, String modelName) {
        String embeddingColumn = sanitizeEmbeddingColumn(modelName);

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
        String embeddingColumn = sanitizeEmbeddingColumn(modelName);

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
        String embeddingColumn = sanitizeEmbeddingColumn(modelName);

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
        String embeddingColumn = sanitizeEmbeddingNodeColumn(modelName);
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
        String embeddingColumn = sanitizeEmbeddingNodeColumn(modelName);
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
