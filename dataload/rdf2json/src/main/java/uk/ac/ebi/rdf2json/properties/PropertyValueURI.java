package uk.ac.ebi.rdf2json.properties;

public class PropertyValueURI extends PropertyValue {

    String uri;

    public PropertyValueURI(String uri) {
        this.uri = uri;
    }

    static public PropertyValueURI fromUri(String uri) {
        return new PropertyValueURI(uri);
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

    @Override
    public String toString() {
        return "PropertyValueURI{" +
                "uri='" + uri + '\'' +
                '}';
    }
}
