import { asArray } from "../app/util";
import Reified from "./Reified";
import Thing from "./Thing";

export default abstract class Entity extends Thing {
  abstract getParents(): Reified<any>[];

  getDescriptionAsArray(): Reified<any>[] {
    return Reified.fromJson<any>(this.properties["definition"]);
  }

  hasDirectChildren(): boolean {
    return this.properties["hasDirectChildren"] === "true";
  }

  hasHierarchicalChildren(): boolean {
    return this.properties["hasHierarchicalChildren"] === "true";
  }

  getSynonyms() {
    return Reified.fromJson<any>(this.properties["synonym"]);
  }

  getAnnotationPredicate() {
    return asArray(this.properties["annotationPredicate"]);
  }

  getLabelForIri(id: string) {
    const iriToLabels = this.properties["iriToLabels"];
    return iriToLabels[id][0];
  }

  getAnnotationById(id: string) {
    return asArray(this.properties[id]);
  }

  getiriToLabels(): any {
    return this.properties["iriToLabels"];
  }

  getShortForm(): string {
    return this.properties["shortForm"];
  }
}
