import { asArray } from "../app/util";
import Reified from "./Reified";
import Thing from "./Thing";

export default abstract class Entity extends Thing {
  abstract getParents(): Reified<any>[];

  hasChildren(): boolean {
    return this.properties["hasChildren"] === "true";
  }

  getSynonyms() {
    return Reified.fromJson<any>(this.properties["synonym"]);
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

  getIriToLabel(): any {
    return this.properties["iriToLabel"];
  }
}
