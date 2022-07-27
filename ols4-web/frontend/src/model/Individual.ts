import Term from "./Term"

export default class Individual extends Term {
    
	constructor(properties:any) {
		super(properties)
	}
    
	getParents() {
		return []
	}
    }

