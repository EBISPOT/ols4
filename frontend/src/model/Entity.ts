import { asArray } from "../app/util";
import Reified from "./Reified";
import Thing from "./Thing";

export default abstract class Entity extends Thing {
  abstract getParents(): Reified<any>[];
  abstract getSuperEntities(): Reified<any>[];
  abstract getEquivalents(): Reified<any>[];

  isCanonical(): boolean {
    return this.properties["isDefiningOntology"] === true;
  }

  isDeprecated(): boolean {
    return (
      this.properties["isObsolete"] === true
    );
  }

  getDeprecationVersion(): string {
    // only supports EFO for now
    return this.properties["http://www.ebi.ac.uk/efo/obsoleted_in_version"];
  }

  getDeprecationReason(): Reified<any>[] {
    return Reified.fromJson<any>(
      this.properties["http://purl.obolibrary.org/obo/IAO_0000231"]
    ).concat(
      Reified.fromJson<any>(
        this.properties["http://www.ebi.ac.uk/efo/reason_for_obsolescence"]
      )
    );
  }

  getDeprecationReplacement(): string {
    return this.properties["http://purl.obolibrary.org/obo/IAO_0100001"];
  }

  getRelatedFrom(): Reified<any>[] {
    return Reified.fromJson<any>(this.properties["relatedFrom"]);
  }

  getDescriptionAsArray(): Reified<any>[] {
    return Reified.fromJson<any>(this.properties["definition"]);
  }

  hasDirectChildren(): boolean {
    return this.properties["hasDirectChildren"] === true;
  }

  hasHierarchicalChildren(): boolean {
    return this.properties["hasHierarchicalChildren"] === true;
  }

  hasChildren(): boolean {
    return this.hasDirectChildren() || this.hasHierarchicalChildren();
  }

  getAncestorIris(): string[] {
    return asArray(this.properties["ancestor"]);
  }

  getHierarchicalAncestorIris(): string[] {
    return asArray(this.properties["hierarchicalAncestor"]);
  }

  getSynonyms() {
    return Reified.fromJson<any>(this.properties["synonym"]);
  }

  getAppearsIn(): string[] {
    return (this.properties["appearsIn"] || []) as string[];
  }

  getDefinedBy(): string[] {
    return (this.properties["definedBy"] || []) as string[];
  }

  getShortForm(): string {
    return this.properties["curie"] || this.properties["shortForm"];
  }

  getDepictedBy(): Reified<string>[] {
    return Reified.fromJson<string>(
      [ ...asArray(this.properties["http://xmlns.com/foaf/0.1/depicted_by"] || []),
        ...asArray(this.properties["http://xmlns.com/foaf/0.1/depiction"] || []) ]
    );
  }

  isPredicateFromInformalVocabulary(predicate: string): boolean {
    return predicate.startsWith("http://www.w3.org/2004/02/skos/core#") ||
        predicate.startsWith("http://purl.org/dc/terms/") ||
        predicate.startsWith("http://purl.org/dc/elements/1.1/") ||
        predicate.startsWith("http://schema.org/")
  }
  getAnnotationPredicates(): string[] {
    let definitionProperties = asArray(this.properties["definitionProperty"]);
    let synonymProperties = asArray(this.properties["synonymProperty"]);
    let hierarchicalProperties = asArray(
      this.properties["hierarchicalProperty"]
    );
    let annotationPredicates = new Set();

    for (let predicate of Object.keys(this.properties)) {
      // properties without an IRI are things that were added by rdf2json so should not
      // be included as annotations
      if (predicate.indexOf("://") === -1) continue;

      // this is handled explicitly in EntityPage
      if (predicate.startsWith("negativePropertyAssertion+")) continue;

      // this is handled explicitly in EntityPage
      if (predicate === "http://xmlns.com/foaf/0.1/depicted_by") continue;
      if (predicate === "http://xmlns.com/foaf/0.1/depiction") continue;

      // Object properties and data properties are not annotation properties, except in the case of informal vocabularies.
      if (!this.isPredicateFromInformalVocabulary(predicate)) {
        let linkedEntity = this.getLinkedEntities().get(predicate)
        if (linkedEntity != undefined && linkedEntity.type && linkedEntity.type.indexOf("objectProperty") !== -1) continue;
        if (linkedEntity != undefined && linkedEntity.type && linkedEntity.type.indexOf("dataProperty") !== -1) continue;
      }

      // If the value was already interpreted as definition/synonym/hierarchical, do
      // not include it as an annotation
      if (
        definitionProperties.indexOf(predicate) !== -1 ||
        synonymProperties.indexOf(predicate) !== -1 ||
        hierarchicalProperties.indexOf(predicate) !== -1
      ) {
        continue;
      }

      // anything in the rdf, rdfs, owl namespaces aren't displayed in the annotations section...
      if (
        predicate.startsWith("http://www.w3.org/2000/01/rdf-schema#") ||
        predicate.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#") ||
        predicate.startsWith("http://www.w3.org/2002/07/owl#")
      ) {
        // ...apart from these ones
        if (
          predicate !== "http://www.w3.org/2000/01/rdf-schema#comment" &&
          predicate !== "http://www.w3.org/2000/01/rdf-schema#seeAlso" &&
          // predicate !== "http://www.w3.org/2002/07/owl#hasKey" &&
          predicate !== "http://www.w3.org/2002/07/owl#disjointUnionOf"
        ) {
          continue;
        }
      }

      // while in general oboInOwl namespace properties are annotations, some
      // of them we don't want to display
      //
      if (
        //predicate === "http://www.geneontology.org/formats/oboInOwl#inSubset"
        predicate === "http://www.geneontology.org/formats/oboInOwl#id"
      ) {
        continue;
      }

      annotationPredicates.add(predicate);
    }

    // console.log("APs");
    // console.dir(Array.from(annotationPredicates));

    return Array.from(annotationPredicates) as string[];
  }

  getNumHierarchicalDescendants(): number {
    return this.properties["numHierarchicalDescendants"]
      ? parseInt(this.properties["numHierarchicalDescendants"])
      : 0;
  }

  getNumDescendants(): number {
    return this.properties["numDescendants"]
      ? parseInt(this.properties["numDescendants"])
      : 0;
  }

  getHierarchicalParentReificationAxioms(parentIri: string): any {
    let hierarchicalParents = Reified.fromJson<any>(
      this.properties["hierarchicalParent"]
    );

    for (let p of hierarchicalParents) {
      if (p.value === parentIri) {
        return p.getMetadata();
      }
    }
  }
}
