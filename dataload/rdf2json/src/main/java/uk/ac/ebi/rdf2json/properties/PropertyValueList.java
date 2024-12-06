package uk.ac.ebi.rdf2json.properties;

import java.util.List;

public class PropertyValueList extends PropertyValue {

    private List<PropertyValue> propertyValues;

    public PropertyValueList(List propertyValues) {
        this.propertyValues = propertyValues;
    }

    public List<PropertyValue> getPropertyValues() {
        return propertyValues;
    }

    @Override
    public Type getType() {
        return Type.LIST;
    }
//    public PropertyValueList<T> addOrUpdateProperty(T newPropertyValue) {
//        Optional<PropertyValue> propertyValueOptional = find(newPropertyValue);
//        if (propertyValueOptional.isPresent()) {
//            (propertyValueOptional.get()).axioms = newPropertyValue.axioms;
//        } else
//            listOfPropertyValues.add(newPropertyValue);
//        return this;
//    }
//
//    public Optional<PropertyValue> find(PropertyValue propertyValueToFind) {
//        for (PropertyValue propertyValue: listOfPropertyValues) {
//            if (propertyValue.equals(propertyValueToFind))
//                return Optional.of(propertyValue);
//        }
//        return Optional.empty();
//    }

    @Override
    public boolean equals(PropertyValue propertyValue) {
        if (propertyValue.getType() != Type.LIST)
            return false;
        else if (getClass() != propertyValue.getClass())
            return false;
        else if (propertyValues.size() != ((PropertyValueList)propertyValue).getPropertyValues().size())
            return false;
        else {
            List listToCompare = ((PropertyValueList)propertyValue).getPropertyValues();
            for (int i = 0; i<propertyValues.size(); i++) {
                if (!propertyValues.get(i).equals(listToCompare.get(i)))
                    return false;
            }
        }
        return true;
    }
}
