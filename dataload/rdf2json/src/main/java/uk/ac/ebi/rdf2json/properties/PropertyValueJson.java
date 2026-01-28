
package uk.ac.ebi.rdf2json.properties;

import com.google.gson.JsonElement;

public class PropertyValueJson extends PropertyValue {
    
    public JsonElement value;

    public PropertyValueJson(JsonElement value) {
        this.value = value;
    }

    public Type getType() {
        return Type.JSON;
    }

    public boolean equals(PropertyValue other) {
        return other.getType() == Type.JSON &&
                ((PropertyValueJson) other).value.equals(value);
    }

    @Override
    public String toString() {
        return "PropertyValueJson{" +
                "value=" + value +
                '}';
    }
}
