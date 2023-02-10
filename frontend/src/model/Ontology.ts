import { asArray } from "../app/util";
import Reified from "./Reified";
import Thing from "./Thing";

export default class Ontology extends Thing {
  getOntologyId(): string {
    return this.properties["ontologyId"];
  }
  getName(): string {
    let names = Reified.fromJson<string>(
      this.properties["http://purl.org/dc/elements/1.1/title"] ||
      this.properties["title"] ||
      ""
    );
    return names[0].value || this.getOntologyId()
  }
  getDescription(): string {
    let descriptions = Reified.fromJson<string>(
      this.properties["http://purl.org/dc/elements/1.1/description"] ||
      this.properties["description"] ||
      ""
    );
    return descriptions[0].value || ''
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
    if(!versionIri)
      return ""
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
  getAnnotationPredicates():string[] {

	  let annotationPredicates = new Set()

	  for (let predicate of Object.keys(this.properties)) {

		  // properties without an IRI are things that were added by owl2json so should not
		  // be included as annotations
		  if (predicate.indexOf('://') === -1)
			  continue;

		// anything in the rdf, rdfs, owl namespaces aren't considered annotations
		if (! (
			predicate.startsWith("http://www.w3.org/2000/01/rdf-schema#") ||
			predicate.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#") ||
			predicate.startsWith("http://www.w3.org/2002/07/owl#")
		)) {
		  annotationPredicates.add(predicate)
		}
	}

	  return Array.from(annotationPredicates) as string[]
  }
  getPreferredRoots():string[] {
    return asArray( this.properties["hasPreferredRoot"] );
  }
  getLanguages():string[] {
    return asArray( this.properties["language"] );
  }
}
