package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class HierarchyFlagsAnnotator {

    private static final Logger logger = LoggerFactory.getLogger(HierarchyFlagsAnnotator.class);

    public static void annotateHierarchyFlags(OntologyGraph graph) {

        long startTime3 = System.nanoTime();

        // Set of IRIs that have children
        Set<String> children = new HashSet<>();
        Set<String> hierarchicalChildren = new HashSet<>();

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

                PropertyValueList parentsList =  (PropertyValueList)c.properties.getPropertyValue(DIRECT_PARENT.getText());

                boolean hasDirectParents = false;

                if (parentsList != null && parentsList.getPropertyValues() != null) {
                    for (PropertyValue parent : parentsList.getPropertyValues()) {
                        if (parent.getType() == PropertyValue.Type.URI) {
                            String iri = ((PropertyValueURI) parent).getUri();

                            if (iri.equals("http://www.w3.org/2002/07/owl#Thing") ||
                                    iri.equals("http://www.w3.org/2002/07/owl#TopObjectProperty")) {
                                continue;
                            }

                            hasDirectParents = true;
                            children.add(iri);
                        }
                    }

                    c.properties.addProperty(HAS_DIRECT_PARENTS.getText(),
                            PropertyValueLiteral.fromBoolean(hasDirectParents ? "true" : "false"));
                } else {
                    c.properties.addProperty(HAS_DIRECT_PARENTS.getText(),
                            PropertyValueLiteral.fromBoolean("false"));
                }


		// 2. Hierarchical parents
		//

                List<PropertyValue> hierarchicalParentsList = c.properties.getPropertyValues(HIERARCHICAL_PARENT.getText());

                boolean hasHierarchicalParents = false;

                if(hierarchicalParentsList != null && hierarchicalParentsList.size() == 1 &&
                        hierarchicalParentsList.get(0) instanceof PropertyValueList ) {

                    List<PropertyValue> hierarchicalParents = ((PropertyValueList) hierarchicalParentsList.get(0)).getPropertyValues();
                    for (PropertyValue parent : hierarchicalParents) {

                        String iri = ((PropertyValueURI) parent).getUri();

                        if (iri.equals("http://www.w3.org/2002/07/owl#Thing") ||
                                iri.equals("http://www.w3.org/2002/07/owl#TopObjectProperty")) {
                                    continue;
                        }

                        hasHierarchicalParents = true;
                        hierarchicalChildren.add(iri);
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

                if(children.contains(c.uri)) {
                    c.properties.addProperty(HAS_DIRECT_CHILDREN.getText(), PropertyValueLiteral.fromBoolean("true"));
                } else {
                    c.properties.addProperty(HAS_DIRECT_CHILDREN.getText(), PropertyValueLiteral.fromBoolean("false"));
                }

                if(hierarchicalChildren.contains(c.uri)) {
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


