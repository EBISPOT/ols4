import Entity from "./Entity";
import Reified from "./Reified";

export default class Class extends Entity {
  constructor(properties: any) {
    super(properties);
  }

  getParents(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["http://www.w3.org/2000/01/rdf-schema#subClassOf"]
    );
  }
}
