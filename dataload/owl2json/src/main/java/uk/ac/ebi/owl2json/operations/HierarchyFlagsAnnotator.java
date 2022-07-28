package uk.ac.ebi.owl2json.operations;

import org.apache.jena.graph.NodeFactory;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlTranslator;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HierarchyFlagsAnnotator {

    public static void annotateHierarchyFlags(OwlTranslator translator) {

        long startTime3 = System.nanoTime();

        for(String id : translator.nodes.keySet()) {
            OwlNode c = translator.nodes.get(id);

		    if (c.types.contains(OwlNode.NodeType.CLASS) ||
				c.types.contains(OwlNode.NodeType.PROPERTY) ||
				c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                c.properties.addProperty("hasChildren",
                        translator.hasChildren.contains(c.uri) ?
                            NodeFactory.createLiteral("true") :
                            NodeFactory.createLiteral("false")
                );

                c.properties.addProperty("isRoot",
                        translator.hasParents.contains(c.uri) ?
                                NodeFactory.createLiteral("false") :
                                NodeFactory.createLiteral("true")
                );
            }
        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate hierarchy flags: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }
}


