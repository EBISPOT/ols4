package uk.ac.ebi.rdf2json.properties;

import java.util.List;
import java.util.Objects;

public class PropertyValueStringList extends PropertyValue {

    private List<PropertyValueLiteral> listOfStrings;

    public PropertyValueStringList(List<PropertyValueLiteral> listOfStrings) {
        this.listOfStrings = listOfStrings;
    }

    public List<PropertyValueLiteral> getListOfStrings() {
        return listOfStrings;
    }

    @Override
    public Type getType() {
        return Type.STRING_LIST;
    }

    @Override
    public boolean equals(PropertyValue other) {
        if (this == other)
            return true;
        if (other == null || getClass() != other.getClass())
            return false;
        if (getType() != other.getType())
            return false;
        PropertyValueStringList that = (PropertyValueStringList) other;
        return Objects.equals(listOfStrings, that.listOfStrings);
    }

}
