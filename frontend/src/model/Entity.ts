import { asArray } from "../app/util";
import Thing from "./Thing";
import Reified from "./Reified";

export default abstract class Entity extends Thing {
  constructor(properties: any) {
    super(properties);
  }

  abstract getParents(): Reified<any>[];

  hasChildren(): boolean {
    return this.properties["hasChildren"] === "true";
  }

  getSynonyms() {
    const synonym = this.properties["synonym"];
    if (synonym && typeof synonym === "object")
      return asArray(
        synonym.value && typeof synonym.value === "object"
          ? null // TODO handle when "value" is also an object: (This function should actually return Reified)
          : synonym.value
      );
    return asArray(synonym);
  }

  getAnnotationPredicate() {
    return asArray(this.properties["annotationPredicate"]);
  }

  getLabelForIri(id: string) {
    const iriToLabel = this.properties["iriToLabel"];
    return iriToLabel[id];
  }

  getAnnotationById(id: string) {
    return asArray(this.properties[id]);
  }

  getIriToLabel():any {
	return this.properties['iriToLabel']
  }

}
