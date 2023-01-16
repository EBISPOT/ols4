import { asArray } from "../app/util";
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
    const types = this.properties["type"] as string[];

    for (const type of types) {
      if (
        ["ontology", "class", "property", "individual"].indexOf(type) !== -1
      ) {
        return type as any;
      }
    }

    throw new Error("unknown type");
  }

  getTypePlural(): "ontologies" | "classes" | "properties" | "individuals" {
    const type = this.getType();

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

  getRdfTypes(): string[] {
    return this.properties[
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
    ] as string[];
  }

  getName(): string {
    const label = Reified.fromJson<any>(
      this.properties["http://www.w3.org/2000/01/rdf-schema#label"]
    );
    if (label && label.length > 0) {
      return label[0].value;
    }
    return this.getIri();
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

  getLabelForIri(id: string) {
    const referencedEntities = this.properties["referencedEntities"];
    if (referencedEntities) {
      const label = referencedEntities[id]?.label;
      return Array.isArray(label) ? label[0] : label;
    } else {
      return undefined;
    }
  }

  getAnnotationById(id: string):Reified<any>[] {
    return Reified.fromJson(asArray(this.properties[id]))
  }
}
