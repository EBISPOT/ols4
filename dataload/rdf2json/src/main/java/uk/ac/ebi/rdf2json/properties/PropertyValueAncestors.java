package uk.ac.ebi.rdf2json.properties;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.helpers.AncestorsClosure;

import java.util.Set;

// Storing the ancestors in the properties of each entity (e.g. with PropertyValueURIs) causes an explosion in RAM
// usage for large hierarchical ontologies (32 GB requirement on the LSF to load ncbitaxon became 64 GB).
// So instead we store an instance of this class as the value for the ancestors property, which retrieves the
// ancestors closure on demand.
//
public class PropertyValueAncestors extends PropertyValue {

    OntologyNode node;
    String hierarchyPredicate;

    public PropertyValueAncestors(OntologyNode node, String hierarchyPredicate) {
        this.node = node;
        this.hierarchyPredicate = hierarchyPredicate;
    }

    public Set<String> getAncestors(OntologyGraph graph) {
        return AncestorsClosure.getAncestors(node, hierarchyPredicate, graph);
    }

    public PropertyValue.Type getType() {
        return PropertyValue.Type.ANCESTORS;
    }

    public boolean equals(PropertyValue other) {
        return other.getType() == PropertyValue.Type.ANCESTORS
                && ((PropertyValueAncestors) other).node.equals(node)
                && ((PropertyValueAncestors) other).hierarchyPredicate.equals(hierarchyPredicate);
    }

    @Override
    public String toString() {
        return "PropertyValueAncestors{" +
                "node=" + node +
                ", hierarchyPredicate='" + hierarchyPredicate + '\'' +
                '}';
    }
}


