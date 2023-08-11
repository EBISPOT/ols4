import { asArray } from "../app/util";
import Entity from "./Entity";
import Reified from "./Reified";

export default class Class extends Entity {
  getParents(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["hierarchicalParent"]
    );
  }
  getSuperEntities(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["http://www.w3.org/2000/01/rdf-schema#subClassOf"]
    );
  }
  getEquivalents(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["http://www.w3.org/2002/07/owl#equivalentClass"]
    );
  }

  getDisjointWith() {
	return asArray(this.properties['http://www.w3.org/2002/07/owl#disjointWith'])
  }

  getHasKey() {
    return asArray(this.properties['http://www.w3.org/2002/07/owl#hasKey'])
  }
}
