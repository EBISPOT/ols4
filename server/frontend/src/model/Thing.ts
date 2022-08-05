
export default abstract class Thing {

	properties:any
    
	constructor(properties:any) {
	    this.properties = properties
	}

	getId():string {
		return this.properties['id']
	}

	getUri():string {
		return this.properties['uri']
	}

	getType():'ontology'|'class'|'property'|'individual' {

		let types = this.properties['type'] as string[]

		for(let type of types) {
			if(['ontology','class','property','individual'].indexOf(type) !== -1) {
				return type as any
			}
		}
		

		throw new Error('unknown type')
	}

	getName():string {
	    return this.properties['http://www.w3.org/2000/01/rdf-schema#label']
			|| this.getUri()
	}

	getDescription():string {
		return this.properties['definition'] || ''
	}

	getOntologyId():string {
	    return this.properties['ontologyId']
	}
}