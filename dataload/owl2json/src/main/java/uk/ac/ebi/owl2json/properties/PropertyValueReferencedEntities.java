
package uk.ac.ebi.owl2json.properties;

import java.util.Map;
import java.util.TreeMap;

public class PropertyValueReferencedEntities extends PropertyValue {

	// Map of entity IRIs to the specific subset of properties we include
	// in referencedEntities (just label at the moment)
	//
	Map<String, PropertySet> entityIriToProperties = new TreeMap<>();

	public PropertyValueReferencedEntities() {
	}

	public void addEntity(String entityIri, PropertySet properties) {
		entityIriToProperties.put(entityIri, properties);
	}

	public Map<String,PropertySet> getEntityIriToProperties() {
		return entityIriToProperties;
	}

	@Override
	public Type getType() {
		return Type.REFERENCED_ENTITIES;
	}

	@Override
	public boolean equals(PropertyValue other) {
		throw new RuntimeException("unimplemented");
	}


	
}
