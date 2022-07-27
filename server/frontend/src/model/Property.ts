import asArray from "../asArray";
import Term from "./Term";

export default class Property extends Term {

	constructor(properties:any) {
	    super(properties)
	}
    
	getParents() {
	    return asArray(this.properties['http://www.w3.org/2000/01/rdf-schema#subPropertyOf'])
	}
    
    
    }