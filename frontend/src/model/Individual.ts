import Entity from "./Entity"

export default class Individual extends Entity {
    
	constructor(properties:any) {
		super(properties)
	}
    
	getParents() {
		return []
	}
    }

