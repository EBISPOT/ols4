import Class from "./Class"
import Individual from "./Individual"
import Property from "./Property"
import Entity from "./Entity"

export function thingFromProperties(properties: any): Entity {

	let types = properties['type'] || []

	if(types.indexOf('class') !== -1)
		return new Class(properties)

	if(types.indexOf('property') !== -1)
		return new Property(properties)

	if(types.indexOf('individual') !== -1)
		return new Individual(properties)

	throw new Error('unknown entity type: ' + JSON.stringify(properties))
}
