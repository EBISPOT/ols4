package uk.ac.ebi.owl2json;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;

import java.util.*;

public class OwlNode {

    public enum NodeType {
        ONTOLOGY,
        CLASS,
        PROPERTY,
        NAMED_INDIVIDUAL,
        ANNOTATION,
        AXIOM,
        RESTRICTION,
        RDF_LIST
    }

    public String uri;
    public Set<NodeType> types = new TreeSet<>();
//    List<OwlNode> parents;
    public PropertySet properties = new PropertySet();

    public class Property {
        public Node value;

        public Property(Node value) {
            this.value = value;
        }

        // further properties (for reification)
        public PropertySet properties = null;
    }

    public class PropertySet {

        public Map<String, List<Property>> properties = new TreeMap<>();

        public void addProperty(String predicate, Node value) {
            List<Property> props = properties.get(predicate);
            if (props != null) {

		// prevent dupliacte values if same triple appears in multiple owl files
               for(Property p : props) {
                   if(p.value.equals(value)) {
                       return;
                   }
               }


                props.add(new Property(value));
            } else {
                props = new ArrayList<>();
                props.add(new Property(value));
                properties.put(predicate, props);
            }
        }

        public void annotateProperty(String predicate, Node value, String predicate2, Node value2, OwlTranslator translator) {

            List<Property> props = properties.get(predicate);

            Property prop = null;

            if (props != null) {

		if(value.isBlank()) {
			// bnode case, look for an isomorphic bnode
			for(Property p : props) {
				Node existingValue = p.value;
				if(existingValue.isBlank()) {
					if(translator.areSubgraphsIsomorphic(existingValue, p.value)) {
						prop = p;
						break;
					}
				}
			}
		} else {
			// simple case, look for an equal value to reify
			for(Property p : props) {
				if(p.value.equals(value)) {
					prop = p;
					break;
				}
			}
		}
                if(prop == null) {
                    prop = new Property(value);
                    props.add(prop);
                }
            } else {
                props = new ArrayList<>();
                prop = new Property(value);
                props.add(prop);
                properties.put(predicate, props);
            }

            if(prop.properties == null) {
                prop.properties = new PropertySet();
            }

            prop.properties.addProperty(predicate2, value2);
        }


    }

}


