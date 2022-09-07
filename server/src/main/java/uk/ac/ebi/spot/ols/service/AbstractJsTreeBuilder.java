package uk.ac.ebi.spot.ols.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.*;


public abstract class AbstractJsTreeBuilder {

    private String rootName = "Thing";

    private static Logger logger = LoggerFactory.getLogger(AbstractJsTreeBuilder.class);

    protected Map<String, Set<String>> ontologyPreferredRoots = new HashMap<>();

    public AbstractJsTreeBuilder() {

    }


    public void setRootName(String rootName) {
        this.rootName = rootName;
    }


    abstract String getJsTreeParentQuery();
    abstract String getJsTreeParentQuery(ViewMode viewMode);
    abstract String getJsTreeParentSiblingQuery();
    abstract String getJsTreeParentSiblingQuery(ViewMode viewMode);
    abstract String getJsTreeChildrenQuery();
    abstract String getRootName();
    abstract String getJsTreeRoots(ViewMode viewMode);


    public Object getJsTree(Neo4jClient neo4jClient, String ontologyName, String iri, boolean sibling) {

        Session session = neo4jClient.getSession();

        logger.debug("ontologyName = " + ontologyName);
        logger.debug("iri = " + iri);
        logger.debug("sibling = " + sibling);

        Map<String, Object> paramt = new HashMap<>();
        paramt.put("0", ontologyName);
        paramt.put("1", iri);

        String query = (sibling) ? getJsTreeParentSiblingQuery() : getJsTreeParentQuery();
        Result result = session.run(query, paramt);

        setRootName(getRootName());
        Object jsTreeObject = getJsTreeObject(ontologyName, iri, result, ViewMode.ALL);

        logger.debug("Return jsTreeObject = " + jsTreeObject);
        return jsTreeObject;
    }

    public Object getJsTree(Neo4jClient neo4jClient, String ontologyName, String iri, boolean sibling, ViewMode viewMode) {

        Session session = neo4jClient.getSession();

        logger.debug("ontologyName = " + ontologyName);
        logger.debug("iri = " + iri);
        logger.debug("sibling = " + sibling);
        logger.debug("viewMode = " + viewMode);
        Map<String, Object> paramt = new HashMap<>();
        paramt.put("0", ontologyName);
        paramt.put("1", iri);

        String query = (sibling) ? getJsTreeParentSiblingQuery(viewMode) : getJsTreeParentQuery(viewMode);


        logger.debug("query = " + query);

        Result result = session.run(query, paramt);

        if (!result.hasNext()) {
            result = session.run(getJsTreeRoots(viewMode), paramt);
        }
        cacheRoots(neo4jClient, ontologyName, viewMode);
        setRootName(getRootName());
        Object jsTreeObject = getJsTreeObject(ontologyName, iri, result, viewMode);

        logger.debug("Return jsTreeObject = " + jsTreeObject);
        return jsTreeObject;
    }

    public Object getJsTreeChildren(Neo4jClient neo4jClient, String ontologyName, String iri, String parentNodeId, String lang) {

        Session session = neo4jClient.getSession();

        logger.debug("ontologyName = " + ontologyName);
        logger.debug("iri = " + iri);
        logger.debug("parentNodeId = " + parentNodeId);


        Map<String, Object> paramt = new HashMap<>();
        paramt.put("0", ontologyName);
        paramt.put("1", iri);
        String query = getJsTreeChildrenQuery();

        Result res = session.run(query, paramt);
        List<Record> rec = res.list();

        List<JsTreeObject> treeObjects = new ArrayList<>();

        int counter = 1;
        for(Record row : rec) {

            String nodeId = row.get("startId").asString();

            JsTreeObject jsTreeObject = createJsTreeObject(nodeId, ontologyName, counter, row.asMap(), parentNodeId, "_child_");

            if (jsTreeObject.isHasChildren()) {
                jsTreeObject.setChildren(true);
                jsTreeObject.getState().put("opened", false);
            }
            treeObjects.add(jsTreeObject);

            counter++;
        }

        logger.debug("Return treeObjects = " + treeObjects);
        return treeObjects;
    }

    private void cacheRoots(Neo4jClient neo4jClient, String ontologyName, ViewMode viewMode) {
        switch (viewMode){
            case ALL:
                break;
            case PREFERRED_ROOTS:
                cachePreferredRoots(neo4jClient, ontologyName);
                break;
            default:
                logger.error("Unknown viewMode = " + viewMode);
        }
    }

    private void cachePreferredRoots(Neo4jClient neo4jClient, String ontologyName) {

        if (!ontologyPreferredRoots.containsKey(ontologyName)) {

            Session session = neo4jClient.getSession();

            Set<String> preferredRootsSet = new HashSet<>();

            Map<String, Object> parameterMap = new HashMap<>();
            parameterMap.put("0", ontologyName);

            String query = getJsTreeRoots(ViewMode.PREFERRED_ROOTS);
            logger.debug("query = " + query);
            Result result = session.run(query, parameterMap);

            while (result.hasNext()) {
                Record r= result.next();
                String nodeId = r.get("startId").asString();
                preferredRootsSet.add(nodeId);
            }
            ontologyPreferredRoots.put(ontologyName, preferredRootsSet);
        }
    }

    /**
     * Creates a map of maps with the id as key to the map of maps. For each key a map is created that stores a row from
     * the result as key value pairs.
     *
     *
     * @param ontologyName
     * @param iri
     * @param result
     * @return
     */
    private Object getJsTreeObject(String ontologyName, String iri, Result result, ViewMode viewMode) {
        logger.debug("ontologyName = " + ontologyName);
        logger.debug("iri = " + iri);

        // create a map of the start node to the rows in the results
        Map<String, List<Map<String, Object>>> resultsMap = createMapOfListOfMapQueryResults(result);

        Map<String, Collection<JsTreeObject>> jsTreeObjectMap = new HashMap<>();
        Collection<String> parentIds = new HashSet<>();

        for (String id : resultsMap.keySet()) {
            generateJsTreeObject(id, ontologyName, jsTreeObjectMap, resultsMap, parentIds, viewMode);
        }

        // find all the nodes that are parents (i.e. should be expanded)
        // any nodes that aren't parents are leaves in the tree, we need to check these have child nodes to see if they
        // have further children

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

        logger.debug("Return jsTreeObjects = " + jsTreeObjects);
        return jsTreeObjects;
    }

    /**
     * Returns a map of list of maps containing the result. The node id is the key to the generated map.
     *
     * @param result
     * @return
     */
    private Map<String, List<Map<String, Object>>> createMapOfListOfMapQueryResults(Result result) {
        Map<String, List<Map<String, Object>>> resultsMap = new HashMap<>();
        while (result.hasNext()) {
            Record r = result.next();
            String nodeId = r.get("startId").asString();
            if (!resultsMap.containsKey(nodeId)) {
                resultsMap.put(nodeId, new ArrayList<>());
            }
            resultsMap.get(nodeId).add(r.asMap());
        }
        return resultsMap;
    }

    /**
     *
     * This method walks up a graph and splits parent nodes when a term has more than one parent.
     * This method updates the {@code jsTreeObjectMap} with {@link JsTreeObject}s.
     *
     * @param nodeId starting node
     * @param ontologyName the active ontology
     * @param jsTreeObjectMap store a map of already created jsTree Objects
     * @param resultsMap a map of the start nodes to the rows in the results table
     * @param parentIdCollector collect parent Ids that we've had to create
     */
    private void generateJsTreeObject(String nodeId, String ontologyName,
                                      Map<String, Collection<JsTreeObject>> jsTreeObjectMap,
                                      Map<String, List<Map<String, Object>>> resultsMap,
                                      Collection<String> parentIdCollector,
                                      ViewMode viewMode) {
        try {
            Collection<JsTreeObject> objectVersions = getJsObjectTree(nodeId, ontologyName, resultsMap, jsTreeObjectMap,
                    parentIdCollector, viewMode, new HashSet<String>());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isRootNode(String nodeId, String ontologyName, Map<String, List<Map<String, Object>>> resultsMap,
                               ViewMode viewMode)
     throws IOException {
        logger.debug("nodeId = " + nodeId);
        logger.debug("viewMode = " + viewMode);
        boolean isRootNode = false;
        switch (viewMode){
            case ALL:
                isRootNode = !resultsMap.containsKey(nodeId);
                logger.debug("Return isRootNode = " + isRootNode);
                break;
            case PREFERRED_ROOTS:
                Set<String> preferredRoots = ontologyPreferredRoots.get(ontologyName);
                isRootNode = ontologyPreferredRoots.containsKey(ontologyName) && preferredRoots.contains(nodeId);
                break;
            default:
                logger.error("Unknown viewMode = " + viewMode);
        }
        logger.debug("Return isRootNode = " + isRootNode);
        return isRootNode;
    }

    /**
     *
     *
     * @param nodeId
     * @param ontologyName
     * @param resultsMap
     * @param jsTreeObjectMap
     * @param parentIdCollector
     * @return
     * @throws IOException
     */
    private Collection<JsTreeObject> getJsObjectTree(String nodeId, String ontologyName,
                                                     Map<String, List<Map<String, Object>>> resultsMap,
                                                     Map<String, Collection<JsTreeObject>> jsTreeObjectMap,
                                                     Collection<String> parentIdCollector,
                                                     ViewMode viewMode, Set<String> visited) throws IOException {
        logger.debug("Get tree for node " + nodeId + "; visited " + String.join(",", visited));

        visited.add(nodeId);

        // return the object if we have seen it before
            // note (JM): this is not really cycle protection: while it might prevent infinite recursion
            // here in the case of a cycle, it would result in a cyclic tree being returned to jstree and
            // cause infinite recursion client-side instead.
        if (jsTreeObjectMap.containsKey(nodeId)) {
            return jsTreeObjectMap.get(nodeId);
        }

        if (isRootNode(nodeId, ontologyName, resultsMap, viewMode)) {
            switch (viewMode) {
                case ALL:
                    return Collections.singleton(new JsTreeObject("#", "#", ontologyName, "", rootName,
                            false, "#"));
                case PREFERRED_ROOTS:
                   return Collections.singleton(createJsTreeNode(nodeId, ontologyName, jsTreeObjectMap, parentIdCollector, 1,
                            resultsMap.get(nodeId).get(0),"#", "_"));
            }
        }

        int x = 1;

        for (Map<String, Object> row : resultsMap.get(nodeId) ) {
            List<Integer> parentIds = new ObjectMapper().readValue(row.get("parents").toString(), List.class);

            for (Integer pid : parentIds) {

                String parentId = pid.toString();

                logger.debug("Node " + nodeId + " has parent: " + parentId);

                if(visited.contains(parentId)) {
                    logger.debug("Detected cycle: Already visited parent " + parentId);
                    continue;
                }

                for (JsTreeObject parentObject : getJsObjectTree(parentId, ontologyName, resultsMap, jsTreeObjectMap,
                        parentIdCollector, viewMode, new HashSet<String>(visited))) {

                    createJsTreeNode(nodeId, ontologyName, jsTreeObjectMap, parentIdCollector, x, row,
                            parentObject.getId(), "_");
                    x++;
                }
            }
        }

        return jsTreeObjectMap.get(nodeId);
    }

    private JsTreeObject createJsTreeNode(String nodeId, String ontologyName,
                                          Map<String, Collection<JsTreeObject>> jsTreeObjectMap,
                                          Collection<String> parentIdCollector, int x, Map<String, Object> row,
                                          String parentObjectId, String nodeLabelInsert) {

        JsTreeObject jsTreeObject = createJsTreeObject(nodeId, ontologyName, x, row, parentObjectId, nodeLabelInsert);

        if (!jsTreeObjectMap.containsKey(nodeId)) {
            jsTreeObjectMap.put(nodeId, new HashSet<>());
        }
        jsTreeObjectMap.get(nodeId).add(jsTreeObject);
        parentIdCollector.add(parentObjectId);
        return jsTreeObject;
    }

    private JsTreeObject createJsTreeObject(String nodeId, String ontologyName, int x,
                                            Map<String, Object> row, String parentObjectId, String nodeLabelInsert) {

        String startIri = row.get("startIri").toString();
        String startLabel = row.get("startLabel").toString();
        String relation = row.get("relation").toString().replaceAll(" ", "_");
        boolean hasChildren = Boolean.parseBoolean(row.get("hasChildren").toString());

        String startNode = nodeId + nodeLabelInsert + x;

        return new JsTreeObject(
                startNode,
                startIri,
                ontologyName,
                startLabel,
                relation,
                hasChildren,
                parentObjectId
        );
    }
}
