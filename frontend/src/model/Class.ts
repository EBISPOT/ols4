import { asArray } from "../app/util";
import Entity from "./Entity";
import Reified from "./Reified";

export default class Class extends Entity {
  getParents(): Reified<any>[] {
    // Match the tree's direct/hierarchical union, preferring relationship metadata.
    const parents = [
      ...Reified.fromJson<any>(this.properties["hierarchicalParent"]),
      ...Reified.fromJson<any>(this.properties["directParent"]),
    ];
    const seenIris = new Set<string>();
    return parents.filter(parent => {
      if (seenIris.has(parent.value)) return false;
      seenIris.add(parent.value);
      return true;
    });
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

  getUnionOf() {
    return asArray(this.properties['http://www.w3.org/2002/07/owl#unionOf'])
  }

  getHasKey() {
    return asArray(this.properties['http://www.w3.org/2002/07/owl#hasKey'])
  }
}
