import { asArray } from "../app/util";
import Thing from "./Thing";

export default abstract class Entity extends Thing {
  constructor(properties: any) {
    super(properties);
  }

  abstract getParents(): any[];

  hasChildren(): boolean {
    return this.properties["hasChildren"] === "true";
  }

  getSynonyms() {
    const synonym = this.properties["synonym"];
    if (synonym && typeof synonym === "object")
      return asArray(
        synonym.value && typeof synonym.value === "object"
          ? null // TODO handle when "value" is also an object
          : synonym.value
      );
    return asArray(synonym);
  }

  getAnnotationPredicate() {
    return asArray(this.properties["annotationPredicate"]);
  }

  getPropertyLabel(id: string) {
    const propertyLabels = this.properties["propertyLabels"];
    return propertyLabels[id];
  }

  getAnnotationById(id: string) {
    return asArray(this.properties[id]);
  }
}
