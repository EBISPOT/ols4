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

export default function extractEntityHierarchy(
  entities: Entity[],
  excludeRedundantEdges: boolean = false
): {
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

	// Individuals hang under their rdf:type classes, but a reified
	// hierarchicalParent (e.g. isSubCohortOf between individuals) carries its
	// own relation metadata which takes precedence.

	childRelationToParent =
		(parentRelation.getMetadata()
			&& parentRelation.getMetadata()['childRelationToParent']
			&& parentRelation.getMetadata()['childRelationToParent'][0])
		|| 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'

	parentRelationToChild =
		(parentRelation.getMetadata()
			&& parentRelation.getMetadata()['parentRelationToChild']
			&& parentRelation.getMetadata()['parentRelationToChild'][0])
		|| null

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

  // Hide the "practically redundant" edges (see GitHub issue #1252) before
  // breaking cycles: the reduction never touches edges within a cycle, so the
  // cycle breaker below still sees exactly the cycles it did before.
  if (excludeRedundantEdges) {
    removeRedundantRelations(childToParentRelations);
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

/* Transitive reduction of the loaded child -> parent graph, ignoring the
 * relation labels (is_a, part_of, ...).
 *
 * An edge child -> parent is redundant, and removed, when the child has some
 * other parent q which itself sits (transitively) below the same parent: the
 * child is then already reachable from that parent through the more specific
 * path parent -> ... -> q -> child. Given `c is_a a`, `c part_of b` and
 * `b is_a a`, the `c is_a a` edge goes and c is shown under a via b only.
 *
 * Two extra conditions only matter when the loaded entities contain a
 * hierarchical cycle: q must not be an ancestor of the parent, and the child
 * must not be an ancestor of q. Within a cycle there is no meaningfully "more
 * specific" parent, so edges between members of one are never removed; this
 * also guarantees that everything below a node stays reachable from it.
 *
 * The backend applies exactly the same rule (see redundantEdgeWitness in
 * OlsPostgresClient) when the tree asks for the children of a node with
 * excludeRedundantEdges=true. The two must agree, otherwise an entity that
 * this function places under some parent could disappear when that parent is
 * expanded, or vice versa.
 */
function removeRedundantRelations(
  childToParentRelations: Multimap<string, ParentChildRelation>
) {
  let ancestorsCache: Map<string, Set<string>> = new Map();

  // Strict ancestors of an IRI within the loaded graph (an IRI is its own
  // ancestor only when it is part of a cycle).
  function getAncestors(iri: string): Set<string> {
    let cached = ancestorsCache.get(iri);
    if (cached) return cached;

    let ancestors: Set<string> = new Set();
    let stack: string[] = [iri];
    while (stack.length > 0) {
      let current = stack.pop()!;
      for (let relation of childToParentRelations.get(current) || []) {
        let parentIri = relation.parent.getIri();
        if (!ancestors.has(parentIri)) {
          ancestors.add(parentIri);
          stack.push(parentIri);
        }
      }
    }

    ancestorsCache.set(iri, ancestors);
    return ancestors;
  }

  // Decide every edge against the original graph, then delete. Removing a
  // redundant edge never changes reachability, so deleting as we go would give
  // the same answer, but deciding first keeps the two steps obviously
  // independent of iteration order.
  let redundant: ParentChildRelation[] = [];

  for (let childIri of Array.from(childToParentRelations.keys())) {
    let relations = childToParentRelations.get(childIri) || [];
    if (relations.length < 2) continue;

    for (let relation of relations) {
      let parentIri = relation.parent.getIri();

      let isRedundant = relations.some((other) => {
        let otherIri = other.parent.getIri();
        return (
          otherIri !== parentIri &&
          getAncestors(otherIri).has(parentIri) && // parent is above other...
          !getAncestors(parentIri).has(otherIri) && // ...and not in a cycle with it
          !getAncestors(otherIri).has(childIri) // other is not in a cycle with child
        );
      });

      if (isRedundant) {
        redundant.push(relation);
      }
    }
  }

  for (let relation of redundant) {
    childToParentRelations.delete(relation.child.getIri(), relation);
  }
}

function isTop(iri) {
  return (
    iri === "http://www.w3.org/2002/07/owl#Thing" ||
    iri === "http://www.w3.org/2002/07/owl#TopObjectProperty"
  );
}
