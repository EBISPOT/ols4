export default abstract class Thing {
  properties: any;

  constructor(properties: any) {
    this.properties = properties;
  }

  getId(): string {
    return this.properties["id"];
  }

  getIri(): string {
    return this.properties["iri"];
  }

  getType(): "ontology" | "class" | "property" | "individual" {
    let types = this.properties["type"] as string[];

    for (let type of types) {
      if (
        ["ontology", "class", "property", "individual"].indexOf(type) !== -1
      ) {
        return type as any;
      }
    }

    throw new Error("unknown type");
  }

  getTypePlural(): "ontologies" | "classes" | "properties" | "individuals" {
    let type = this.getType();

    switch (type) {
      case "ontology":
        return "ontologies";
      case "class":
        return "classes";
      case "property":
        return "properties";
      case "individual":
        return "individuals";
      default:
        throw new Error("unknown type");
    }
  }

  getName(): string {
    return (
      this.properties["http://www.w3.org/2000/01/rdf-schema#label"] ||
      this.getIri()
    );
  }

  getDescription(): string {
    const definition = this.properties["definition"];
    if (Array.isArray(definition) && definition.length > 0) {
      return definition
        .map((def) => {
          if (def && typeof def === "object" && !Array.isArray(def)) {
            return def.value || "";
          } else if (def && typeof def === "string") {
            return def;
          }
          return "";
        })
        .join(" ");
    } else if (definition && typeof definition === "object") {
      return definition.value && typeof definition.value === "object"
        ? null // TODO handle when "value" is also an object: (This function should actually return Reified)
        : definition.value;
    }
    return definition || "";
  }

  getOntologyId(): string {
    return this.properties["ontologyId"];
  }
}
