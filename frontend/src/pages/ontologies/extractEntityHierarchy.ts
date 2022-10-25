
import Multimap from "multimap"
import Entity from "../../model/Entity"
import assert from "assert";


/* Unflattens a list of entities into:
 *	- A list of root entities
 * 	- A mapping of IRI to list of child entities
 * 
 * Used by EntityTree and EntityGraph
 */
export default function extractEntityHierarchy(
	entities:Entity[]
):({
	rootEntities:Entity[],
	iriToChildNodes:Multimap<string, Entity>
}) {
	let iriToNode: Map<string, Entity> = new Map()
	let iriToChildNodes: Multimap<string, Entity> = new Multimap()
	let iriToParentNodes: Multimap<string, Entity> = new Multimap()

	for (let entity of entities) {
		iriToNode.set(entity.getIri(), entity)
	}

	for (let entity of entities) {

		let parents = entity.getParents()
			// not interested in bnode subclassofs like restrictions etc
			.filter(parent => typeof parent === 'string')
			.map(parentIri => iriToNode.get(parentIri))
			.filter(parent => parent !== undefined)

		for (let parent of parents) {
			assert(parent)
			iriToChildNodes.set(parent.getIri(), entity)
			iriToParentNodes.set(entity.getIri(), parent)
		}
	}

	let rootEntities = entities.filter((node) => {
		return (iriToParentNodes.get(node.getIri()) || []).length === 0
	})

	return { rootEntities, iriToChildNodes }
}


