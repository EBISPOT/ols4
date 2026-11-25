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

  // Traverse each IRI once. Shared ancestors in a diamond are already checked
  // when complete, and only reaching an active ancestor requires removing an edge.
  const active = new Set<string>();
  const complete = new Set<string>();
  function breakCycles(iri: string) {
    if (complete.has(iri)) return;
    active.add(iri);
    for (const relation of [...(childToParentRelations.get(iri) || [])]) {
      const parent = relation.parent.getIri();
      if (active.has(parent)) childToParentRelations.delete(iri, relation);
      else breakCycles(parent);
    }
    active.delete(iri);
    complete.add(iri);
  }

  for (const entity of entities) breakCycles(entity.getIri());

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
