package uk.ac.ebi.rdf2json.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import uk.ac.ebi.rdf2json.OntologyGraph;

public class PropertySet implements Comparable<PropertySet> {

    private Map<String, List<PropertyValue>> properties = new TreeMap<>();

    public void addProperty(String predicate, PropertyValue value) {
        List<PropertyValue> props = properties.get(predicate);
        if (props != null) {

    // prevent dupliacte values if same triple appears in multiple owl files
            for(PropertyValue p : props) {
                if(p.equals(value)) {
                    return;
                }
            }

            props.add(value);
        } else {
            props = new ArrayList<>();
            props.add(value);
            properties.put(predicate, props);
        }
    }

    public boolean hasProperty(String predicate) {
        return properties.containsKey(predicate);
    }

    public void annotatePropertyWithAxiom(String predicate, PropertyValue value, PropertySet axiom, OntologyGraph graph) {

        List<PropertyValue> props = properties.get(predicate);

        PropertyValue prop = null;

        if (props != null) {

            if (value.getType() == PropertyValue.Type.BNODE) {
                // bnode case, look for an isomorphic bnode
                for (PropertyValue existingValue : props) {
                    if (existingValue.getType() == PropertyValue.Type.BNODE) {
                        if (graph.areSubgraphsIsomorphic(existingValue, value)) {
                            prop = existingValue;
                            break;
                        }
                    }
                }
            } else {
                // simple case, look for an equal value to reify
                for (PropertyValue p : props) {
                    if (p instanceof PropertyValueList) {
                        for (PropertyValue propertyValueListElement: ((PropertyValueList)p).getPropertyValues()) {
                            if (propertyValueListElement.equals(value)) {
                                prop = propertyValueListElement;
                                break;
                            }
                        }
                    } else if (p.equals(value)) {
                        prop = p;
                        break;
                    }
                }
            }


            if (prop == null) {
                prop = value;
                props.add(prop);
            }
        } else {
            props = new ArrayList<>();
            prop = value;
            props.add(prop);
            properties.put(predicate, props);
        }

        prop.axioms.add(axiom);
    }

    public Set<String> getPropertyPredicates() {
        return properties.keySet();
    }

    public List<PropertyValue> getPropertyValues(String predicate) {
        return properties.get(predicate);
    }

    public PropertyValue getPropertyValue(String predicate) {
        List<PropertyValue> values = properties.get(predicate);
        if(values == null || values.size() == 0) {
            return null;
        }
        return values.get(0);
    }

    public void removeProperty(String predicate) {
        properties.remove(predicate);
    }

    public List<PropertyValue> getSortedPropertyValues(String predicate) {
        List<PropertyValue> values = properties.get(predicate);
        if (values == null) return null;
        List<PropertyValue> sorted = new ArrayList<>(values);
        sorted.sort(null);
        return sorted;
    }

    @Override
    public int compareTo(PropertySet other) {
        // Compare by sorted keys first
        List<String> thisKeys = new ArrayList<>(properties.keySet());
        List<String> otherKeys = new ArrayList<>(other.properties.keySet());
        int sizeCompare = Integer.compare(thisKeys.size(), otherKeys.size());
        if (sizeCompare != 0) return sizeCompare;

        for (int i = 0; i < thisKeys.size(); i++) {
            int keyCompare = thisKeys.get(i).compareTo(otherKeys.get(i));
            if (keyCompare != 0) return keyCompare;

            List<PropertyValue> thisValues = getSortedPropertyValues(thisKeys.get(i));
            List<PropertyValue> otherValues = other.getSortedPropertyValues(otherKeys.get(i));
            int valueSizeCompare = Integer.compare(thisValues.size(), otherValues.size());
            if (valueSizeCompare != 0) return valueSizeCompare;

            for (int j = 0; j < thisValues.size(); j++) {
                int valueCompare = thisValues.get(j).compareTo(otherValues.get(j));
                if (valueCompare != 0) return valueCompare;
            }
        }
        return 0;
    }

}

