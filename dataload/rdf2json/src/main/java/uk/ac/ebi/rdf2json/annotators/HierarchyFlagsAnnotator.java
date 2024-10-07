package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;
import uk.ac.ebi.rdf2json.properties.PropertyValueUriList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class HierarchyFlagsAnnotator {

    private static final Logger logger = LoggerFactory.getLogger(HierarchyFlagsAnnotator.class);

    public static void annotateHierarchyFlags(OntologyGraph graph) {

        long startTime3 = System.nanoTime();

        // Set of IRIs that have children
        Set<String> hasChildren = new HashSet<>();
        Set<String> hasHierarchicalChildren = new HashSet<>();

        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);

            if (c.types.contains(OntologyNode.NodeType.CLASS) ||
                    c.types.contains(OntologyNode.NodeType.PROPERTY) ||
                    c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;


		// 1. Direct parents (subClassOf)
		//

                List<PropertyValue> propertyValueUriList =  c.properties.getPropertyValues(DIRECT_PARENT.getText());

                boolean hasDirectParents = false;

                if (propertyValueUriList != null && propertyValueUriList.size()>0) {
                    PropertyValueUriList parents = (PropertyValueUriList)propertyValueUriList.get(0);

                    if (parents != null && parents.getListOfUris() != null) {

                        for (PropertyValueURI parent : parents.getListOfUris()) {

                            String iri = parent.getUri();

                            if (iri.equals("http://www.w3.org/2002/07/owl#Thing") ||
                                    iri.equals("http://www.w3.org/2002/07/owl#TopObjectProperty")) {
                                continue;
                            }

                            hasDirectParents = true;
                            hasChildren.add(iri);
                        }
                    }

                    c.properties.addProperty(HAS_DIRECT_PARENTS.getText(),
                            PropertyValueLiteral.fromBoolean(hasDirectParents ? "true" : "false"));
                }


		// 2. Hierarchical parents
		//

                List<PropertyValue> hierarchicalParents = c.properties.getPropertyValues("hierarchicalParent");

                boolean hasHierarchicalParents = false;

                if(hierarchicalParents != null) {
                    for (PropertyValue parent : hierarchicalParents) {

                        String iri = ((PropertyValueURI) parent).getUri();

                        if (iri.equals("http://www.w3.org/2002/07/owl#Thing") ||
                                iri.equals("http://www.w3.org/2002/07/owl#TopObjectProperty")) {
                                    continue;
                        }

                        hasHierarchicalParents = true;
                        hasHierarchicalChildren.add(iri);
                    }
                }

                c.properties.addProperty(HAS_HIERARCHICAL_PARENTS.getText(), PropertyValueLiteral.fromBoolean(hasHierarchicalParents ? "true" : "false"));
            }
        }

        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);

            if (c.types.contains(OntologyNode.NodeType.CLASS) ||
                    c.types.contains(OntologyNode.NodeType.PROPERTY) ||
                    c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                if(hasChildren.contains(c.uri)) {
                    c.properties.addProperty(HAS_DIRECT_CHILDREN.getText(), PropertyValueLiteral.fromBoolean("true"));
                } else {
                    c.properties.addProperty(HAS_DIRECT_CHILDREN.getText(), PropertyValueLiteral.fromBoolean("false"));
                }

                if(hasHierarchicalChildren.contains(c.uri)) {
                    c.properties.addProperty(HAS_HIERARCHICAL_CHILDREN.getText(), PropertyValueLiteral.fromBoolean("true"));
                } else {
                    c.properties.addProperty(HAS_HIERARCHICAL_CHILDREN.getText(), PropertyValueLiteral.fromBoolean("false"));
                }
            }
        }

        long endTime3 = System.nanoTime();
        logger.info("annotate hierarchy flags: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }
}


