
package uk.ac.ebi.rdf2json.properties;

public class PropertyValueLiteral extends PropertyValue {
    
    String value;
    String datatype;
    String lang;

    public PropertyValueLiteral(String value, String datatype, String lang) {
        this.value = value;
        this.lang = lang;
        this.datatype = datatype;
    }

    static public PropertyValueLiteral fromString(String str) {
        return new PropertyValueLiteral(str, "http://www.w3.org/2001/XMLSchema#string", "");
    }

    static public PropertyValueLiteral fromBoolean(String str) {
        return new PropertyValueLiteral(str, "http://www.w3.org/2001/XMLSchema#boolean", "");
    }

    public Type getType() {
        return Type.LITERAL;
    }

    public String getValue() {
        return this.value;
    }

    public String getDatatype() {
        return this.datatype;
    }

    public String getLang() {
        return this.lang;
    }

    public boolean equals(PropertyValue other) {
        return other.getType() == Type.LITERAL &&
                ((PropertyValueLiteral) other).value.equals(value) &&
                ((PropertyValueLiteral) other).datatype.equals(datatype) &&
                ((PropertyValueLiteral) other).lang.equals(lang);
    }
}
