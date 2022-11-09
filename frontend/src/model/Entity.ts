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
    return asArray(this.properties["synonym"]);
  }

  getXRefs() {
    return asArray(
      this.properties["http://www.geneontology.org/formats/oboInOwl#hasDbXref"]
    );
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
