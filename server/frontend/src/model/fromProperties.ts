import Class from "./Class"
import Individual from "./Individual"
import Property from "./Property"
import Term from "./Term"

export function termFromProperties(properties: any): Term {

	let types = properties['type'] || []

	if(types.indexOf('class') !== -1)
		return new Class(properties)

	if(types.indexOf('property') !== -1)
		return new Property(properties)

	if(types.indexOf('individual') !== -1)
		return new Individual(properties)

	throw new Error('unknown term type: ' + JSON.stringify(properties))
}
