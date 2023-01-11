import { asArray } from "../app/util";
import ReferencedEntities from "./ReferencedEntities";
import Reified from "./Reified";
import Thing from "./Thing";

export default abstract class Entity extends Thing {
  abstract getParents(): Reified<any>[];
  abstract getEquivalents(): Reified<any>[];

  getRelatedFrom(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["relatedFrom"]
    );
  }

  getDescriptionAsArray(): Reified<any>[] {
    return Reified.fromJson<any>(this.properties["definition"]);
  }

  hasDirectChildren(): boolean {
    return this.properties["hasDirectChildren"] === "true";
  }

  hasHierarchicalChildren(): boolean {
    return this.properties["hasHierarchicalChildren"] === "true";
  }

  getSynonyms() {
    return Reified.fromJson<any>(this.properties["synonym"]);
  }

  getReferencedEntities(): ReferencedEntities {
    return new ReferencedEntities( this.properties["referencedEntities"] || {} );
  }

  getShortForm(): string {
    return this.properties["shortForm"];
  }


  getAnnotationPredicates():string[] {
    let definitionProperties = this.properties['definitionProperty']
    let synonymProperties = this.properties['synonymProperty']
    let hierarchicalProperties = this.properties['hierarchicalProperty']
    let annotationPredicates = new Set()

    /*
    TODO
    This logic is copied from OLS3 as used by the API compatibility layer
    We will probably want to change it for the OLS4 frontend
    */

    for(let predicate of Object.keys(this.properties)) {

            // properties without an IRI are things that were added by owl2json so should not
            // be included as annotations
            if(predicate.indexOf('://') === -1)
                continue;

            // If the value was already interpreted as definition/synonym/hierarchical, do
            // not include it as an annotation
            if (definitionProperties.indexOf(predicate) !== -1 ||
                    synonymProperties.indexOf(predicate) !== -1 ||
                    hierarchicalProperties.indexOf(predicate) !== -1) {
                continue;
            }

            // anything in the rdf, rdfs, owl namespaces aren't considered annotations...
            if(predicate.startsWith("http://www.w3.org/2000/01/rdf-schema#") ||
                    predicate.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#") ||
                    predicate.startsWith("http://www.w3.org/2002/07/owl#")) {

                // ...apart from these ones
                if(predicate !== "http://www.w3.org/2000/01/rdf-schema#comment"
                        && predicate !== "http://www.w3.org/2000/01/rdf-schema#seeAlso") {
                    continue;
                }
            }

	    // while in general oboInOwl namespace properties are annotations, inSubset is not
            //
            if(predicate === "http://www.geneontology.org/formats/oboInOwl#inSubset") {
                continue;
	    }

	    annotationPredicates.add(predicate)
    }

    console.log('APs')
    console.dir(Array.from(annotationPredicates))

    return Array.from(annotationPredicates) as string[]
  }
}
