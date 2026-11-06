package uk.ac.ebi.spot.ols.repository;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import org.jooq.impl.DSL;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CombinedTreeChildrenIT {
    @Container static final PostgreSQLContainer<?> DB = PostgresIntegrationTestSupport.newContainer();
    static PostgresIntegrationTestSupport.ClassRepositoryHandle handle;
    static PostgresIntegrationTestSupport.IndividualRepositoryHandle individuals;
    static final String NS = "https://example.org/paging#";
    static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    @BeforeAll static void setup() throws Exception {
        PostgresIntegrationTestSupport.initializeDatabase(DB);
        try (var conn = DB.createConnection("")) {
            var sql = DSL.using(conn, SQLDialect.POSTGRES);
            for (int n = 1; n <= 2241; n++) insert(sql, "individual", String.format("A%04d", n), "A", false, true, n > 1000);
            for (int n = 1; n <= 1105; n++) insert(sql, "individual", String.format("B%04d", n), "B", false, true, true);
            insert(sql, "class", "Punned", "Mixed", false, true, true);
            insert(sql, "individual", "Punned", "Mixed", false, true, true);
            insert(sql, "class", "Subclass", "Mixed", false, true, true);
            insert(sql, "class", "Hierarchy", "Mixed", false, true, true);
            insert(sql, "individual", "BothRoutes", "Mixed", false, true, true);
            insert(sql, "individual", "HierarchyIndividual", "Mixed", false, true, true);
            insert(sql, "individual", "Imported", "Mixed", false, false, true);
            insert(sql, "individual", "Obsolete", "Mixed", true, true, true);
            // Hierarchical-only and overlapping routes, with relation metadata in JSON.
            sql.execute("UPDATE ols_entities SET direct_parents = '{}'::text[], \"filter_" + RDF_TYPE + "\" = '{}'::text[] WHERE ontology_id='paging' AND iri IN (?, ?)", NS+"Hierarchy", NS+"HierarchyIndividual");
            sql.execute("UPDATE ols_entities SET label=ARRAY['individual-only-match'] WHERE ontology_id='paging' AND iri=? AND type='OntologyIndividual'", NS+"Punned");
            // An imported connector is required to reach an eligible member.
            insert(sql, "class", "Connector", "Mixed", false, false, false);
            insert(sql, "individual", "EligibleLeaf", "Connector", false, true, true);
            sql.execute("UPDATE ols_entities SET direct_ancestors=ARRAY[?,?] WHERE ontology_id='paging' AND iri=?", NS+"Connector", NS+"Mixed", NS+"EligibleLeaf");
        }
        handle = PostgresIntegrationTestSupport.createClassRepository(DB);
        individuals = PostgresIntegrationTestSupport.createIndividualRepository(DB);
    }
    @AfterAll static void close() { if(handle != null) handle.close(); if(individuals != null) individuals.close(); }

    static void insert(org.jooq.DSLContext sql, String kind, String name, String parent, boolean obsolete, boolean nativeTerm, boolean subset) throws Exception {
        String iri=NS+name; var json=new JsonObject();
        json.addProperty("iri",iri); json.addProperty("ontologyId","paging");
        json.add("type", new com.google.gson.Gson().toJsonTree(List.of(kind)));
        json.add("label", new com.google.gson.Gson().toJsonTree(List.of(name)));
        json.addProperty("isObsolete",obsolete); json.addProperty("isDefiningOntology",nativeTerm);
        var metadata=new JsonObject(); metadata.addProperty("value",NS+parent);
        var axioms=new JsonObject(); axioms.add("childRelationToParent",new com.google.gson.Gson().toJsonTree(List.of(NS+"partOf")));
        metadata.add("axioms",axioms); metadata.add("type",new com.google.gson.Gson().toJsonTree(List.of("reification")));
        json.add("hierarchicalParent",new com.google.gson.Gson().toJsonTree(List.of(metadata)));
        var out=new ByteArrayOutputStream(); try(var gzip=new GZIPOutputStream(out)){gzip.write(json.toString().getBytes(StandardCharsets.UTF_8));}
        sql.execute("""
            INSERT INTO ols_entities (id,ontology_id,iri,type,search_type,_json,label,is_obsolete,is_defining_ontology,
                direct_parents,hierarchical_parents,direct_ancestors,hierarchical_ancestors,subset,
                "filter_http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
            VALUES (?,'paging',?,?,?,?,?::text[],?,?,?::text[],?::text[],?::text[],?::text[],?::text[],?::text[])
            """, "paging+"+kind+"+"+iri,iri,kind.equals("class")?"OntologyClass":"OntologyIndividual",kind,out.toByteArray(),
            new String[]{name},obsolete,nativeTerm,new String[]{NS+parent},new String[]{NS+parent},new String[]{NS+parent},new String[]{NS+parent},
            subset?new String[]{NS+"S",NS+"S2"}:new String[0],kind.equals("individual")?new String[]{NS+parent}:new String[0]);
    }
    static Page<JsonElement> page(String parent,int page,int size,String search,List<String> subsets,boolean imported,boolean obsolete,boolean context){
        return handle.repository().getTreeChildrenByOntologyId("paging",PageRequest.of(page,size),NS+parent,obsolete,imported,search,subsets,context,"en",new JsonTransformOptions());
    }
    static List<String> iris(Page<JsonElement> page){return page.stream().map(e->e.getAsJsonObject().get("iri").getAsString()).toList();}
    static Page<JsonElement> legacy(String parent, int page, int size, String search, List<String> subsets,
            boolean imported, boolean obsolete, boolean context) throws Exception {
        return individuals.repository().getIndividualsOfClass("paging", NS+parent, PageRequest.of(page,size),
                obsolete, imported, subsets, context, search, "en", new JsonTransformOptions());
    }
    @Test void legacyIndividualSearchFiltersBeforeCountingAndPagingAndCanBeCleared() throws Exception {
        var result=legacy("A",0,10,"a2241",List.of(NS+"S",NS+"S2"),false,false,true);
        assertThat(iris(result)).containsExactly(NS+"A2241");
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        var beyond=legacy("A",1,10,"a2241",List.of(NS+"S"),false,false,true);
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(1);
        var cleared=legacy("A",224,10,"",List.of(),true,false,false);
        assertThat(iris(cleared)).containsExactly(NS+"A2241");
        assertThat(cleared.getTotalElements()).isEqualTo(2241);
        assertThat(legacy("A",0,10,"   ",List.of(),true,false,false).getTotalElements()).isEqualTo(2241);
        assertThat(legacy("B",0,10,"A2241",List.of(),true,false,false).getTotalElements()).isZero();
        assertThat(legacy("A",0,10,"A0001",List.of(NS+"S"),true,false,true).getTotalElements()).isZero();
        assertThat(legacy("Mixed",0,10,"Imported",List.of(),false,false,false).getTotalElements()).isZero();
        assertThat(iris(legacy("Mixed",0,10,"IMPORTED",List.of(),true,false,false))).containsExactly(NS+"Imported");
        assertThat(legacy("Mixed",0,10,"Obsolete",List.of(),true,false,false).getTotalElements()).isZero();
        assertThat(iris(legacy("Mixed",0,10,"Obsolete",List.of(),true,true,false))).containsExactly(NS+"Obsolete");
        // Both routes use literal case-insensitive label containment, not IRI or tsquery matching.
        assertThat(iris(legacy("A",0,10,"2241",List.of(),true,false,false)))
                .containsExactlyElementsOf(iris(page("A",0,10,"2241",List.of(),true,false,false)));
        assertThat(legacy("A",0,10,"%",List.of(),true,false,false).getTotalElements()).isZero();
    }
    @Test void lateIndividualsAreReachableByPaginationWithNoSubclassRows() {
        var result=page("A",100,10,null,List.of(),true,false,true);
        assertThat(result.getTotalElements()).isEqualTo(2241);
        assertThat(iris(result)).hasSize(10).startsWith(NS+"A1001").endsWith(NS+"A1010");
        assertThat(iris(page("A",224,10,null,List.of(),true,false,true))).containsExactly(NS+"A2241");
        assertThat(iris(page("B",100,10,null,List.of(),true,false,true))).startsWith(NS+"B1001");
    }
    @Test void unionDeduplicatesRoutesAndPunningWithClassWinnerAndStablePages() {
        var all=page("Mixed",0,20,null,List.of(),true,false,true);
        assertThat(all.getTotalElements()).isEqualTo(7);
        assertThat(iris(all)).containsExactly(NS+"Connector",NS+"Hierarchy",NS+"Punned",NS+"Subclass",NS+"BothRoutes",NS+"HierarchyIndividual",NS+"Imported");
        var punned=all.stream().filter(e->e.getAsJsonObject().get("iri").getAsString().equals(NS+"Punned")).findFirst().orElseThrow();
        assertThat(punned.getAsJsonObject().getAsJsonArray("type").get(0).getAsString()).isEqualTo("class");
        assertThat(punned.getAsJsonObject().get("hierarchicalParent").toString()).contains("partOf");
        assertThat(page("Mixed",0,10,"individual-only-match",List.of(),true,false,true).getTotalElements()).isZero();
        assertThat(iris(page("Mixed",0,10,"Punned",List.of(),true,false,true))).containsExactly(NS+"Punned");
        var pages=new ArrayList<String>(); for(int n=0;n<4;n++) pages.addAll(iris(page("Mixed",n,2,null,List.of(),true,false,true)));
        assertThat(pages).containsExactlyElementsOf(iris(all)).doesNotHaveDuplicates();
    }
    @Test void subsetUnionSearchAndVisibilityApplyToTheCombinedPageAndCount() {
        var subset=page("A",100,10,null,List.of(NS+"S",NS+"S2"),false,false,true);
        assertThat(subset.getTotalElements()).isEqualTo(1241);
        assertThat(iris(subset)).startsWith(NS+"A2001");
        assertThat(iris(page("A",0,10,"a2241",List.of(NS+"S"),false,false,true))).containsExactly(NS+"A2241");
        var nativePage=page("Mixed",0,20,null,List.of(NS+"S"),false,false,true);
        assertThat(iris(nativePage)).contains(NS+"Connector").doesNotContain(NS+"Imported",NS+"Obsolete");
        assertThat(iris(page("Mixed",0,20,null,List.of(NS+"S"),false,false,false))).doesNotContain(NS+"Connector");
        assertThat(iris(page("Mixed",0,20,"obsolete",List.of(),true,true,false))).containsExactly(NS+"Obsolete");
        assertThat(page("A",999,10,"missing",List.of(),true,false,true).getTotalElements()).isZero();
    }
}
