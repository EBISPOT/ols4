package uk.ac.ebi.owl2json.properties;

import uk.ac.ebi.owl2json.OwlNode;

public class PropertyValueRelated extends PropertyValue {

    OwlNode classExpression;
    String property;
    OwlNode filler;

    public PropertyValueRelated(OwlNode classExpression, String property, OwlNode filler) {
        this.classExpression = classExpression;
        this.property = property;
        this.filler = filler;
    }

    public OwlNode getClassExpression() {
        return this.classExpression;
    }

    public String getProperty() {
        return this.property;
    }

    public OwlNode getFiller() {
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
}
