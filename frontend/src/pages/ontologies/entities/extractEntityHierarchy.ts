import assert from "assert";
import Multimap from "multimap";
import Entity from "../../../model/Entity";

/* Unflattens a list of entities into:
 *	- A list of root entities
 * 	- A mapping of URI to list of child entities
 *
 * Used by EntityTree and EntityGraph
 */

 interface ParentChildRelation {
	parent:Entity,
	child:Entity
	parentRelationToChild:string|null
	childRelationToParent:string|null
 }

export default function extractEntityHierarchy(entities: Entity[]): {
  rootEntities: Entity[];
  parentToChildRelations: Multimap<string, ParentChildRelation>;
} {
  let childToParentRelations: Multimap<string, ParentChildRelation> = new Multimap();

  let uriToNode: Map<string, Entity> = new Map();
  for (let entity of entities) {
    uriToNode.set(entity.getIri(), entity);
  }

  for (let entity of entities) {
    if (isTop(entity.getIri())) continue;

    for (let parentRelation of entity.getParents()) {

      let parentIri = parentRelation.value;
      let parentEntity = uriToNode.get(parentIri);

      if (isTop(parentIri)) continue;

      if(! (parentEntity instanceof Entity)) {
	continue;
      }

      var parentRelationToChild, childRelationToParent

      if(entity.getType() === 'individual') {

	// In the case of individuals, the child->parent relationship is always
	// rdf:type and there is no explicit parent->child relationship.

	childRelationToParent = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'
	parentRelationToChild = null

      } else if(entity.getType() == 'class') {

	// In the case of classes, the relations are provided in the metadata.

	parentRelationToChild = 
		parentRelation.getMetadata()
			&& parentRelation.getMetadata()['parentRelationToChild'] 
			&& parentRelation.getMetadata()['parentRelationToChild'][0];

	childRelationToParent = 
		parentRelation.getMetadata()
			&& parentRelation.getMetadata()['childRelationToParent'] 
			&& parentRelation.getMetadata()['childRelationToParent'][0];

      } else {

	// In the case of properties, there are no relations to show.
	// (it would always be just subPropertyOf)
	//

      }

      let relation = {
	parent: parentEntity,
	child: entity,
	parentRelationToChild,
	childRelationToParent
      }

      childToParentRelations.set(entity.getIri(), relation);
    }
  }

  var breakCycles = function (currentIri:string, visitedIris: Set<string>) {
    visitedIris.add(currentIri);

    let parentRelations = childToParentRelations.get(currentIri);
    if (parentRelations) {
      for (let parentRelation of parentRelations) {
        if (visitedIris.has(parentRelation.parent.getIri())) {
          // we already saw this parent, remove it
	  childToParentRelations.delete(currentIri, parentRelation)
        } else {
          breakCycles(parentRelation.parent.getIri(), new Set(visitedIris));
        }
      }
    }
  };

  // break cycles starting from leaf entities
  for (let entity of entities) {
    breakCycles(entity.getIri(), new Set());
  }

  // Remove "practically redundant" hierarchical parent edges from the tree
  // view. These edges are not OWL-redundant (removing them changes
  // entailment), so we do NOT remove them from the underlying data / API —
  // only from the rendered tree. Concretely: for a child c with hierarchical
  // parents P, an edge (c -> p) is redundant if p is reachable from another
  // parent q in P via the hierarchical-parent graph (and the converse is not
  // also true, so cycles between p and q are left intact). This matches the
  // canonical example from the issue: given `c is_a a`, `c part_of b`,
  // `b is_a a`, the edge `c -> a` is hidden from the tree because c already
  // appears under a via b.
  removeRedundantHierarchicalParents(childToParentRelations);

  let parentToChildRelations: Multimap<string, ParentChildRelation> = new Multimap();

  for(let childIri of Array.from(childToParentRelations.keys())) {
  	let relations = childToParentRelations.get(childIri);
	for(let r of relations) {
		parentToChildRelations.set(r.parent.getIri(), r)
	}
  }

  let rootEntities = entities.filter((node) => {
    if (isTop(node.getIri())) return false;
    return (childToParentRelations.get(node.getIri()) || []).length === 0;
  });

  return { rootEntities, parentToChildRelations };
}

function isTop(iri) {
  return (
    iri === "http://www.w3.org/2002/07/owl#Thing" ||
    iri === "http://www.w3.org/2002/07/owl#TopObjectProperty"
  );
}

/**
 * Performs a transitive reduction over the child->parent relation graph
 * loaded for the tree view. For each child c with multiple parents, any
 * parent p that is reachable from another parent q (via a chain of
 * child->parent edges within the loaded subgraph) is considered redundant
 * for tree-browser display purposes and the (c -> p) edge is removed.
 *
 * To avoid wiping out both members of a mutual cycle (p reachable from q
 * AND q reachable from p), the edge to p is only removed when q is NOT
 * also reachable from p, so cycles are left intact.
 *
 * Operates in-place on `childToParentRelations`.
 */
function removeRedundantHierarchicalParents(
  childToParentRelations: Multimap<string, ParentChildRelation>
) {
  // Cache of strict hierarchical ancestors (via childToParentRelations) per IRI
  // within the loaded subgraph. Built lazily and memoised across all children.
  let ancestorCache: Map<string, Set<string>> = new Map();

  function getAncestors(iri: string): Set<string> {
    let cached = ancestorCache.get(iri);
    if (cached) return cached;
    let ancestors: Set<string> = new Set();
    let stack: string[] = [iri];
    while (stack.length > 0) {
      let current = stack.pop()!;
      let parentRelations = childToParentRelations.get(current);
      if (!parentRelations) continue;
      for (let r of parentRelations) {
        let pIri = r.parent.getIri();
        if (!ancestors.has(pIri)) {
          ancestors.add(pIri);
          stack.push(pIri);
        }
      }
    }
    ancestorCache.set(iri, ancestors);
    return ancestors;
  }

  for (let childIri of Array.from(childToParentRelations.keys())) {
    let relations = childToParentRelations.get(childIri);
    if (!relations || relations.length < 2) continue;

    // Snapshot parent IRIs and precompute ancestor closures for each.
    let parentIris: string[] = relations.map((r) => r.parent.getIri());
    let uniqueParents = Array.from(new Set(parentIris));
    if (uniqueParents.length < 2) continue;

    let parentAncestors: Map<string, Set<string>> = new Map();
    for (let p of uniqueParents) {
      parentAncestors.set(p, getAncestors(p));
    }

    let redundant: Set<string> = new Set();
    for (let pi of uniqueParents) {
      let piAncestors = parentAncestors.get(pi)!;
      for (let pj of uniqueParents) {
        if (pi === pj) continue;
        let pjAncestors = parentAncestors.get(pj)!;
        // pi is reachable from pj, and pj is not also reachable from pi
        // (so we are not in a hierarchical cycle between pi and pj).
        if (pjAncestors.has(pi) && !piAncestors.has(pj)) {
          redundant.add(pi);
          break;
        }
      }
    }

    if (redundant.size > 0) {
      for (let r of Array.from(relations)) {
        if (redundant.has(r.parent.getIri())) {
          childToParentRelations.delete(childIri, r);
        }
      }
    }
  }
}
