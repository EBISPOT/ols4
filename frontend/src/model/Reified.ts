export default class Reified<T> {
  value: T;
  axioms: any[]|null

  private constructor(value: T, axioms: any[]|null) {
    this.value = value;
    this.axioms = axioms
  }

  public static fromJson<T>(jsonNode: any): Reified<T>[] {
    if (!jsonNode) {
      return [];
    }

    if (!Array.isArray(jsonNode)) {
      jsonNode = [jsonNode];
    }

    return jsonNode.map((value: any) => {

	if(value.type.contains("reification")) {
		return new Reified<T>(value.value, value.axioms)
	} else {
		return new Reified<T>(value, null)
	}

    });
  }

  getMetadata():any|null {

	if(!this.axioms) {
		return null;
	}

	let metadata:any = {}

	for(let axiom of this.axioms) {
		for(let k of Object.keys(axiom)) {
			let v = axiom[k]
			let existing:any[]|undefined = metadata[k]
			if(existing !== undefined) {
				existing.push(v)
			} else {
				metadata[k] = [v]
			}
		}
	}

	return metadata;
  }
}
