import { asArray } from "../app/util";
import Entity from "./Entity";

export default class Class extends Entity {
  constructor(properties: any) {
    super(properties);
  }

  getParents() {
    return asArray(
      this.properties["http://www.w3.org/2000/01/rdf-schema#subClassOf"]
    );
  }
}
