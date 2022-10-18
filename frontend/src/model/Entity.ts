import Thing from "./Thing"

export default abstract class Entity extends Thing {

	constructor(properties:any) {
		super(properties)
	}


	hasChildren():boolean {
	    return this.properties['hasChildren'] === "true"
	}

	abstract getParents():any[]
}