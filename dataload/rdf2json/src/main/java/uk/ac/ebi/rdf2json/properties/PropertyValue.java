package uk.ac.ebi.rdf2json.properties;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.graph.Node;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.ValidateLanguage;

public abstract class PropertyValue implements Comparable<PropertyValue> {

    public enum Type {
        LITERAL,
        URI,
        BNODE,
        ID,
	    RELATED,
	    REFERENCED_ENTITIES,
        ANCESTORS,
        LIST
    }

    // reification
    public List<PropertySet> axioms = new ArrayList<>();

    public List<PropertySet> getSortedAxioms() {
        List<PropertySet> sorted = new ArrayList<>(axioms);
        sorted.sort(null);
        return sorted;
    }

    public static PropertyValue fromJenaNode(Node node) {

       if(node.isLiteral()) {
           return new PropertyValueLiteral(
            node.getLiteralLexicalForm(),
            node.getLiteralDatatypeURI(),
            ValidateLanguage.validateLanguage(node.getLiteralLanguage())
            );
       }
       if(node.isURI()) {
           return new PropertyValueURI(node.getURI());
       }
       if(node.isBlank()) {
           return new PropertyValueBNode(node.getBlankNodeId().toString());
       }

       throw new RuntimeException("Unknown node type");
    }

    protected PropertyValue() {
    }

    public abstract Type getType();
    public abstract boolean equals(PropertyValue other);
    public abstract int compareTo(PropertyValue other);

    protected int compareByType(PropertyValue other) {
        return Integer.compare(this.getType().ordinal(), other.getType().ordinal());
    }

    protected int compareAxioms(PropertyValue other) {
        List<PropertySet> thisAxioms = getSortedAxioms();
        List<PropertySet> otherAxioms = other.getSortedAxioms();
        int sizeCompare = Integer.compare(thisAxioms.size(), otherAxioms.size());
        if (sizeCompare != 0) return sizeCompare;
        for (int i = 0; i < thisAxioms.size(); i++) {
            int cmp = thisAxioms.get(i).compareTo(otherAxioms.get(i));
            if (cmp != 0) return cmp;
        }
        return 0;
    }

}

