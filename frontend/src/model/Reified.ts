export default class Reified<T> {
  value: T;
  axioms: any[] | null;

  private constructor(value: T, axioms: any[] | null) {
    this.value = value;
    this.axioms = axioms;
  }

  public static fromJson<T>(jsonNode: any): Reified<T>[] {
    if (!jsonNode) {
      return [];
    }

    if (!Array.isArray(jsonNode)) {
      jsonNode = [jsonNode];
    }

    return jsonNode.map((value: any) => {
      if (
        typeof value === "object" &&
        Array.isArray(value.type) &&
        value.type.indexOf("reification") !== -1
      ) {
        return new Reified<T>(value.value, value.axioms);
      } else {
        return new Reified<T>(value, null);
      }
    });
  }

  hasMetadata() {
    return this.axioms != null;
  }

  // Combine all of the axioms into one metadata object for the property.
  //
  // note: This means that if the same property is reified multiple times with
  // different metadata, it will all be combined in the UI. Whether this is
  // the desired behaviour is TBD.
  //
  getMetadata(): any | null {
    if (!this.axioms) {
      return null;
    }

    let properties: any = {};

    for (let axiom of this.axioms) {
      for (let k of Object.keys(axiom)) {
        let v = axiom[k];
        let existing: any[] | undefined = properties[k];
        if (existing !== undefined) {
          existing.push(v);
        } else {
          properties[k] = [v];
        }
      }
    }

    return properties;
  }
}
