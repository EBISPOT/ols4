package uk.ac.ebi.owl2json.properties;

import uk.ac.ebi.owl2json.OwlNode;

public class PropertyValueRelated extends PropertyValue {

    // the class expression or Restriction from which the Related relation was derived
    //
    OwlNode classExpression;

    // the established related thing
    //
    OwlNode related;

    public PropertyValueRelated(OwlNode classExpression, OwlNode related) {
        this.classExpression = classExpression;
	this.related = related;
    }

    public OwlNode getClassExpression() {
        return this.classExpression;
    }

    public OwlNode getRelated() {
        return this.related;
    }

    public Type getType() {
        return Type.RELATED;
    }
    
    public boolean equals(PropertyValue other) {
        return other.getType() == Type.RELATED 
		&& ((PropertyValueRelated) other).classExpression.equals(classExpression)
		&& ((PropertyValueRelated) other).related.equals(related);
    }
}
