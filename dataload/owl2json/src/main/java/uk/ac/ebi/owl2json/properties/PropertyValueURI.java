package uk.ac.ebi.owl2json.properties;

public class PropertyValueURI extends PropertyValue {

    String uri;

    public PropertyValueURI(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return this.uri;
    }

    public Type getType() {
        return Type.URI;
    }
    
    public boolean equals(PropertyValue other) {
        return other.getType() == Type.URI && ((PropertyValueURI) other).uri.equals(uri);
    }
}
