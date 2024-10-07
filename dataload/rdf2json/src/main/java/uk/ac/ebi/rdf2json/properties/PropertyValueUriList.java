package uk.ac.ebi.rdf2json.properties;

import java.util.List;
import java.util.Objects;

public class PropertyValueUriList extends PropertyValue {

    private List<PropertyValueURI> listOfUris;


    public PropertyValueUriList(List<PropertyValueURI> listOfUris) {
        this.listOfUris = listOfUris;
    }

    public List<PropertyValueURI> getListOfUris() {
        return listOfUris;
    }

    @Override
    public Type getType() {
        return Type.URI_LIST;
    }

    @Override
    public boolean equals(PropertyValue other) {
        if (this == other)
            return true;
        if (other == null || getClass() != other.getClass())
            return false;
        if (getType() != other.getType())
            return false;
        PropertyValueUriList that = (PropertyValueUriList) other;
        return Objects.equals(listOfUris, that.listOfUris);
    }


}
