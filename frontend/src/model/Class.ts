import { asArray } from "../app/util";
import Entity from "./Entity";

export default class Class extends Entity {
  constructor(properties: any) {
    super(properties);
  }

  getParents() {
    const subClassOf =
      this.properties["http://www.w3.org/2000/01/rdf-schema#subClassOf"];
    if (Array.isArray(subClassOf)) {
      return subClassOf.map((obj) => {
        if (obj && typeof obj === "object")
          return obj.value && typeof obj.value === "object"
            ? null // TODO handle when "value" is also an object
            : obj.value;
        return obj;
      });
    } else if (subClassOf && typeof subClassOf === "object") {
      return asArray(subClassOf.value);
    }
    return asArray(subClassOf);
  }
}
