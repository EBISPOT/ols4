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

  getRelatedFrom(): Reified<any>[] {
    return Reified.fromJson<any>(this.properties["relatedFrom"]);
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
          predicate !== "http://www.w3.org/2000/01/rdf-schema#domain" &&
          predicate !== "http://www.w3.org/2000/01/rdf-schema#range" &&
          predicate !== "http://www.w3.org/2000/01/rdf-schema#seeAlso" &&
          predicate !== "http://www.w3.org/2002/07/owl#hasKey" &&
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
}
