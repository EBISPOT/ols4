import { asArray } from "../app/util";
import Entity from "./Entity";

export default class Individual extends Entity {
  getParents() {
    return [];
  }
  getEquivalents() {
    return [];
  }


  getDifferentFrom() {
	return asArray(this.properties['http://www.w3.org/2002/07/owl#differentFrom'])
  }

  getSameAs() {
	return asArray(this.properties['http://www.w3.org/2002/07/owl#sameAs'])
  }
}
