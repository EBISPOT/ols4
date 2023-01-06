import Entity from "./Entity";
import Reified from "./Reified";

export default class Class extends Entity {
  getParents(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["http://www.w3.org/2000/01/rdf-schema#subClassOf"]
    );
  }
  getEquivalents(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["http://www.w3.org/2002/07/owl#equivalentClass"]
    );
  }
}
