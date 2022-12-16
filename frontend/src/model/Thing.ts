import Reified from "./Reified";

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
    const label = this.properties["http://www.w3.org/2000/01/rdf-schema#label"];
    if (typeof label !== "object") {
      return label || this.getIri();
    } else {
      return label.value || this.getIri();
    }
  }

  getDescription(): string {
    const definition = Reified.fromJson<any>(this.properties["definition"]);
    if (definition && definition.length > 0) {
      return definition.map((def) => def.value || "").join(" ");
    }
    return "";
  }

  getOntologyId(): string {
    return this.properties["ontologyId"];
  }
}
