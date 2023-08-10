import { asArray } from "../app/util";
import Entity from "./Entity";
import Reified from "./Reified";

export default class Property extends Entity {
  getParents(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["http://www.w3.org/2000/01/rdf-schema#subPropertyOf"]
    );
  }
  getSuperEntities(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["http://www.w3.org/2000/01/rdf-schema#subPropertyOf"]
    );
  }
  getEquivalents(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["http://www.w3.org/2002/07/owl#equivalentProperty"]
    );
  }


  getDisjointWith() {
	return asArray(this.properties['http://www.w3.org/2002/07/owl#propertyDisjointWith'])
  }

  getInverseOf() {
    return asArray(this.properties["http://www.w3.org/2002/07/owl#inverseOf"]);
  }

  getDomain() {
    return asArray(this.properties['http://www.w3.org/2000/01/rdf-schema#domain'])
  }

  getRange() {
    return asArray(this.properties['http://www.w3.org/2000/01/rdf-schema#range'])
  }

  getPropertyChains():Reified<any>[] {
	return Reified.fromJson<any>(
		this.properties["http://www.w3.org/2002/07/owl#propertyChainAxiom"]
	)
  }

}
