import { asArray } from "../app/util";
import Entity from "./Entity";
import Reified from "./Reified";

export default class Individual extends Entity {
  getParents() {
    return Reified.fromJson<any>(
      this.properties["directParent"]
    );
  }
  getEquivalents() {
    return [];
  }
  getSuperEntities(): Reified<any>[] {
    return Reified.fromJson<any>([])
  }

  getDifferentFrom() {
    return asArray(
      this.properties["http://www.w3.org/2002/07/owl#differentFrom"]
    );
  }

  getSameAs() {
    return asArray(this.properties["http://www.w3.org/2002/07/owl#sameAs"]);
  }

  getIndividualTypes(): string[] {
    const rdfTypes: any = this.getRdfTypes();

    if (!rdfTypes || !Array.isArray(rdfTypes)) {
      return [];
    }

    return rdfTypes.filter(
      (t: any) =>
        t !== "http://www.w3.org/2002/07/owl#NamedIndividual" &&
		( (! (typeof t === 'string') || !t.startsWith("http://www.w3.org/2000/01/rdf-schema#")))
    );
  }

}
