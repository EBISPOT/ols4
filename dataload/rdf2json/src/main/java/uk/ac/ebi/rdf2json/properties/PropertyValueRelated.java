package uk.ac.ebi.rdf2json.properties;

import uk.ac.ebi.rdf2json.OntologyNode;

public class PropertyValueRelated extends PropertyValue {

    OntologyNode classExpression;
    String property;
    OntologyNode filler;

    public PropertyValueRelated(OntologyNode classExpression, String property, OntologyNode filler) {
        this.classExpression = classExpression;
        this.property = property;
        this.filler = filler;

        if(filler == null) {
            throw new RuntimeException("filler was null");
        }

    }

    public OntologyNode getClassExpression() {
        return this.classExpression;
    }

    public String getProperty() {
        return this.property;
    }

    public OntologyNode getFiller() {
        return this.filler;
    }

    public Type getType() {
        return Type.RELATED;
    }
    
    public boolean equals(PropertyValue other) {
        return other.getType() == Type.RELATED 
		&& ((PropertyValueRelated) other).classExpression.equals(classExpression)
		&& ((PropertyValueRelated) other).property.equals(property)
		&& ((PropertyValueRelated) other).filler.equals(filler);
    }

    @Override
    public String toString() {
        return "PropertyValueRelated{" +
                "classExpression=" + classExpression +
                ", property='" + property + '\'' +
                ", filler=" + filler +
                '}';
    }
}
