import Thing from "./Thing";
export default class Ontology extends Thing {
  constructor(properties: any) {
    super(properties);
  }

  getOntologyId(): string {
    return this.properties["ontologyId"];
  }
  getName(): string {
    return (
      this.properties["http://purl.org/dc/elements/1.1/title"] ||
      this.properties["title"] ||
      ""
    );
  }
  getDescription(): string {
    return (
      this.properties["http://purl.org/dc/elements/1.1/description"] ||
      this.properties["description"] ||
      ""
    );
  }
  getNumEntities(): number {
    return parseInt(this.properties["numberOfEntities"]);
  }
  getNumClasses(): number {
    return parseInt(this.properties["numberOfClasses"]);
  }
  getNumProperties(): number {
    return parseInt(this.properties["numberOfProperties"]);
  }
  getNumIndividuals(): number {
    return parseInt(this.properties["numberOfIndividuals"]);
  }
  getLogoURL(): string {
    return this.properties["depicted_by"] || undefined;
  }
  getOntologyPurl(): string {
    return this.properties["ontology_purl"];
  }
  getVersionIri(): string {
    return this.properties["http://www.w3.org/2002/07/owl#versionIRI"];
  }
  getVersion(): string {
    return this.properties["http://www.w3.org/2002/07/owl#versionInfo"];
  }
  getLoaded(): string {
    return this.properties["loaded"];
  }
}
