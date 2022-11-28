import Thing from "./Thing";

export default class Ontology extends Thing {
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
  getVersionFromIri(): string {
    const versionIri =
      this.properties["http://www.w3.org/2002/07/owl#versionIRI"];
    const versionFromDate = versionIri.match(/\d{4}-\d{2}-\d{2}/);

    if (versionFromDate && versionFromDate.length > 0) {
      return versionFromDate[0];
    } else {
      const versionFromNumber = versionIri.match(/\/v[d.].*\//);
      return versionFromNumber
        ? versionFromNumber[0].replaceAll("/", "").replace("v", "")
        : "";
    }
  }
  getLoaded(): string {
    return this.properties["loaded"];
  }
}
