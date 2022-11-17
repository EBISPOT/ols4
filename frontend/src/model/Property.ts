import { asArray } from "../app/util";
import Entity from "./Entity";

export default class Property extends Entity {
  constructor(properties: any) {
    super(properties);
  }

  getParents() {
    const subPropOf =
      this.properties["http://www.w3.org/2000/01/rdf-schema#subPropertyOf"];
    if (Array.isArray(subPropOf)) {
      return subPropOf.map((obj) => {
        if (obj && typeof obj === "object")
          return obj.value && typeof obj.value === "object"
            ? null // TODO handle when "value" is also an object
            : obj.value;
        return obj;
      });
    } else if (subPropOf && typeof subPropOf === "object") {
      return asArray(subPropOf.value);
    }
    return asArray(subPropOf);
  }
}
