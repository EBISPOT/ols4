package uk.ac.ebi.spot.ols.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * @author Simon Jupp
 * @date 18/08/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 *
 * Build a JSTree (https://www.jstree.com) JSON serialisation of the tree
 *
 * Viewing graphs (or DAGs) as trees where terms may have multiple parents means we need to
 * use the alternate json format for jstree where you list each node and specify the parent node.
 * JsTree can't handle terms with mutliple parents, so we need to create a unique id for the multi-parent nodes.
 */
@Component
public class JsTreeBuilder {

    private String rootName = "Thing";

    // CLASS queries
    String getJsTreeClassChildren = "MATCH path = (child)-[r:SUBCLASSOF|RelatedTree]->(n:Class)\n"+
            "USING INDEX n:Class(iri)\n" +
            "WHERE n.ontology_name = {0} AND n.iri = {1}\n"+
            "UNWIND rels(path) as r1\n" +
            "RETURN distinct id(startNode(r1)) as startId, startNode(r1).iri as startIri, " + 
              "startNode(r1).label as startLabel, startNode(r1).has_children as hasChildren, r1.label as relation";

    String parentTreeQuery = "MATCH path = (n:Class)-[r:SUBCLASSOF|RelatedTree*]->(parent)\n"+
            "USING INDEX n:Class(iri)\n" +
            "WHERE n.ontology_name = {0} AND n.iri = {1}\n"+
            "UNWIND rels(path) as r1\n" +
            "RETURN distinct id(startNode(r1)) as startId, startNode(r1).iri as startIri," + 
              "startNode(r1).label as startLabel, startNode(r1).has_children as hasChildren, " + 
              "r1.label as relation, collect( distinct id(endNode(r1)) ) as parents";

    String parentSiblingTreeQuery = "MATCH path = (n:Class)-[r:SUBCLASSOF|RelatedTree*]" + 
            "->(parent)<-[r2:SUBCLASSOF|RelatedTree]-(n1:Class)\n"+
            "USING INDEX n:Class(iri)\n" +
            "WHERE n.ontology_name = {0} AND n.iri = {1}\n"+
            "UNWIND rels(path) as r1\n" +
            "WITH r1\n" +
            "WHERE startNode(r1).is_obsolete=false\n"+
            "RETURN distinct id(startNode(r1)) as startId, startNode(r1).iri as startIri, " + 
              "startNode(r1).label as startLabel, startNode(r1).has_children as hasChildren, " + 
              "r1.label as relation, collect( distinct id(endNode(r1)) ) as parents";


    // Property queries

    String getJsTreePropertyChildren = "MATCH path = (child)-[r:SUBPROPERTYOF]->(n:Property)\n"+
            "USING INDEX n:Property(iri)\n" +
            "WHERE n.ontology_name = {0} AND n.iri = {1}\n"+
            "UNWIND rels(path) as r1\n" +
            "RETURN distinct id(startNode(r1)) as startId, startNode(r1).iri as startIri, startNode(r1).label as startLabel, startNode(r1).has_children as hasChildren, r1.label as relation";

    String propertyParentTreeQuery = "MATCH path = (n:Property)-[r:SUBPROPERTYOF*]->(parent)\n"+
            "USING INDEX n:Property(iri)\n" +
            "WHERE n.ontology_name = {0} AND n.iri = {1}\n"+
            "UNWIND rels(path) as r1\n" +
            "RETURN distinct id(startNode(r1)) as startId, startNode(r1).iri as startIri, startNode(r1).label as startLabel, startNode(r1).has_children as hasChildren, r1.label as relation, collect( distinct id(endNode(r1)) ) as parents";

    String propertyParentSiblingTreeQuery = "MATCH path = (n:Property)-[r:SUBPROPERTYOF*]->(parent)<-[r2:SUBPROPERTYOF]-(n1:Property)\n"+
            "USING INDEX n:Property(iri)\n" +
            "WHERE n.ontology_name = {0} AND n.iri = {1}\n"+
            "UNWIND rels(path) as r1\n" +
            "WITH r1\n" +
            "WHERE startNode(r1).is_obsolete=false\n"+
            "RETURN distinct id(startNode(r1)) as startId, startNode(r1).iri as startIri, startNode(r1).label as startLabel, startNode(r1).has_children as hasChildren, r1.label as relation, collect( distinct id(endNode(r1)) ) as parents";

    // individual tree query
    String individualParentTreeQuery = "MATCH path = (n:Individual)-[r:INSTANCEOF|SUBCLASSOF*]->(parent)\n"+
            "USING INDEX n:Individual(iri)\n" +
            "WHERE n.ontology_name = {0} AND n.iri = {1}\n"+
            "UNWIND rels(path) as r1\n" +
            "RETURN distinct id(startNode(r1)) as startId, startNode(r1).iri as startIri, startNode(r1).label as startLabel, startNode(r1).has_children as hasChildren, r1.label as relation,collect( distinct id(endNode(r1)) ) as parents";

    String individualParentSiblingTreeQuery = "MATCH path = (n:Individual)-[r:INSTANCEOF|SUBCLASSOF*]->(parent)<-[r2:SUBCLASSOF]-(n1:Individual)\n"+
            "USING INDEX n:Individual(iri)\n" +
            "WHERE n.ontology_name = {0} AND n.iri = {1}\n"+
            "UNWIND rels(path) as r1\n" +
            "WITH r1\n" +
            "WHERE startNode(r1).is_obsolete=false\n"+
            "RETURN distinct id(startNode(r1)) as startId, startNode(r1).iri as startIri, startNode(r1).label as startLabel, startNode(r1).has_children as hasChildren, r1.label as relation, collect( distinct id(endNode(r1)) ) as parents";


    public JsTreeBuilder() {

    }

    public void setRootName(String rootName) {
        this.rootName = rootName;
    }

    public Object getIndividualJsTree(String ontologyName, String iri) {
        Map<String, Object> paramt = new HashMap<>();
        paramt.put("0", ontologyName);
        paramt.put("1", iri);

        Session session = Neo4jClient.getSession();
        Result res = session.run(individualParentTreeQuery, paramt);

        setRootName("Thing");
        return getJsTreeObject(ontologyName, iri, res);
    }

    public Object getPropertyJsTree(String ontologyName, String iri, boolean siblings) {
        Map<String, Object> paramt = new HashMap<>();
        paramt.put("0", ontologyName);
        paramt.put("1", iri);
        String query = siblings ? propertyParentSiblingTreeQuery : propertyParentTreeQuery;

        Session session = Neo4jClient.getSession();
        Result res = session.run(query, paramt);

        setRootName("TopObjectProperty");
        return getJsTreeObject(ontologyName, iri, res);
    }


    public Object getClassJsTree(String ontologyName, String iri, boolean siblings) {
        Map<String, Object> paramt = new HashMap<>();
        paramt.put("0", ontologyName);
        paramt.put("1", iri);
        String query = siblings ? parentSiblingTreeQuery : parentTreeQuery;

        Session session = Neo4jClient.getSession();
        Result res = session.run(query, paramt);

        setRootName("Thing");
        return getJsTreeObject(ontologyName, iri, res);
    }

    public Object getJsTreeClassChildren(String ontologyName, String iri, String parentNodeId) {
        return getJsTreeChildren("term", ontologyName, iri, parentNodeId);
    }

    public Object getJsTreePropertyChildren(String ontologyName, String iri, String parentNodeId) {
        return getJsTreeChildren("property", ontologyName, iri, parentNodeId);
    }

    private Object getJsTreeChildren(String type,String ontologyName, String iri, String parentNodeId) {
        Map<String, Object> paramt = new HashMap<>();
        paramt.put("0", ontologyName);
        paramt.put("1", iri);
        String query = getJsTreeClassChildren;
        if (type.equals("property")) {
            query = getJsTreePropertyChildren;
        }

        Session session = Neo4jClient.getSession();
        Result res = session.run(query, paramt);

        List<JsTreeObject> treeObjects = new ArrayList<>();

        int counter = 1;
        while (res.hasNext()) {
            Map<String, Object> row = res.next().asMap();
            String nodeId = row.get("startId").toString();
            String startIri = row.get("startIri").toString();
            String startLabel = row.get("startLabel").toString();
            String relation = row.get("relation").toString().replaceAll(" ", "_");
            boolean hasChildren = Boolean.parseBoolean(row.get("hasChildren").toString());

            String startNode = nodeId + "_child_" + counter;


            JsTreeObject jsTreeObject = new JsTreeObject(
                    startNode,
                    startIri,
                    ontologyName,
                    startLabel,
                    relation,
                    hasChildren,
                    parentNodeId
            );

            if (jsTreeObject.isHasChildren()) {
                jsTreeObject.setChildren(true);
                jsTreeObject.getState().put("opened", false);
            }
            treeObjects.add(jsTreeObject);

            counter++;
        }

        return treeObjects;
    }

    private Object getJsTreeObject(String ontologyName, String iri, Result res) {

        // create a map of the start node to the rows in the results
        Map<String, List<Map<String, Object>>> resultsMap = new HashMap<>();
        while (res.hasNext()) {
            Map<String, Object> r = res.next().asMap();
            String nodeId = r.get("startId").toString();
            if (!resultsMap.containsKey(nodeId)) {
                resultsMap.put(nodeId, new ArrayList<>());
            }
            resultsMap.get(nodeId).add(r);
        }

        Map<String, Collection<JsTreeObject>> jsTreeObjectMap = new HashMap<>();
        Collection<String> parentIds = new HashSet<>();

        for (String id : resultsMap.keySet()) {
            generateJsTreeObject(id, ontologyName, jsTreeObjectMap, resultsMap, parentIds);
        }

        // find all the nodes that are parents (i.e. should be expanded)
        // any nodes that aren't parents are leaves in the tree, we need to check these have child nodes to see if they have further children

        Collection<JsTreeObject> jsTreeObjects = new HashSet<>();
        for (String key : jsTreeObjectMap.keySet()) {
            for (JsTreeObject jsTreeObject : jsTreeObjectMap.get(key)) {

                if (jsTreeObject.getIri().equals(iri)) {
                    jsTreeObject.getState().put("selected", true);
                }

                if (!parentIds.contains(jsTreeObject.getId())) {
                    // this is a leaf node

                    if (jsTreeObject.isHasChildren()) {
                        jsTreeObject.setChildren(true);
                        jsTreeObject.getState().put("opened", false);
                    }
                }
            }

            jsTreeObjects.addAll(jsTreeObjectMap.get(key));
        }

        return jsTreeObjects;
    }

    /**
     *
     * This method walks up graph and splits parent nodes when a term has more than one.
     *
     * @param nodeId starting node
     * @param ontologyName the active ontology
     * @param jsTreeObjectMap store a map of already created jsTree Objects
     * @param resultsMap a map of the start nodes to the rows in the results table
     * @param parentIdCollector collect parent Ids that we've had to create
     */
    private void generateJsTreeObject(String nodeId, String ontologyName, Map<String, Collection<JsTreeObject>> jsTreeObjectMap, Map<String, List<Map<String, Object>>> resultsMap, Collection<String> parentIdCollector) {
        try {
            Collection<JsTreeObject> objectVersions = getJsObjectTree(nodeId, ontologyName, resultsMap, jsTreeObjectMap, parentIdCollector);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Collection<JsTreeObject> getJsObjectTree(String nodeId, String ontologyName, Map<String, List<Map<String, Object>>> resultsMap, Map<String, Collection<JsTreeObject>> jsTreeObjectMap, Collection<String> parentIdCollector) throws IOException {

        // return the object if we have seen it before
        if (jsTreeObjectMap.containsKey(nodeId)) {
            return jsTreeObjectMap.get(nodeId);
        }

        // if no key, then we are at a root
        if (!resultsMap.containsKey(nodeId)) {
            return Collections.singleton(new JsTreeObject("#", "#", ontologyName, "",rootName, false, "#"));
        }

        int x = 1;

        for (Map<String, Object> row : resultsMap.get(nodeId) ) {
            List<Integer> parentIds = new ObjectMapper().readValue(row.get("parents").toString(), List.class);

            for (Integer pid : parentIds) {

                String parentId = pid.toString();

                for (JsTreeObject parentObject : getJsObjectTree(parentId, ontologyName, resultsMap, jsTreeObjectMap, parentIdCollector)) {

                    String startIri = row.get("startIri").toString();
                    String startLabel = row.get("startLabel").toString();
                    String relation = row.get("relation").toString().replaceAll(" ", "_");
                    boolean hasChildren = Boolean.parseBoolean(row.get("hasChildren").toString());

                    String startNode = nodeId + "_" + x;


                    JsTreeObject jsTreeObject = new JsTreeObject(
                            startNode,
                            startIri,
                            ontologyName,
                            startLabel,
                            relation,
                            hasChildren,
                            parentObject.getId()
                    );

                    if (!jsTreeObjectMap.containsKey(nodeId)) {
                        jsTreeObjectMap.put(nodeId, new HashSet<>());
                    }
                    jsTreeObjectMap.get(nodeId).add(jsTreeObject);
                    parentIdCollector.add(parentObject.getId());
                    x++;
                }
            }
        }


        return jsTreeObjectMap.get(nodeId);
    }

}
