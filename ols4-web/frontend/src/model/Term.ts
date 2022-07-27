
import asArray from "../asArray"
import Entity from "./Entity"

export default abstract class Term extends Entity {
    
	constructor(properties:any) {
		super(properties)
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

	hasChildren():boolean {
	    return this.properties['hasChildren'] === "true"
	}
    
	abstract getParents():any[]
    
    }

