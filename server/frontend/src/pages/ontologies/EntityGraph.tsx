
import Cytoscape from 'cytoscape';
import CytoscapeComponent from 'react-cytoscapejs';
import Entity from '../../model/Entity';

import ColaLayout from 'cytoscape-cola'
import { useEffect, useRef, useState } from 'react';
import { getPaginated } from '../../api';
import { thingFromProperties } from '../../model/fromProperties';
import extractEntityHierarchy from './extractEntityHierarchy';
import Spinner from '../../components/Spinner';
Cytoscape.use(ColaLayout);

export default function EntityGraph(props:{
	ontologyId:string
	selectedEntity?:Entity,
	entityType:'entities'|'classes'|'properties'|'individuals'
}) {

	let { ontologyId, selectedEntity, entityType } = props

	let [ elements, setElements ] = useState<any[]>([])

	let cyRef = useRef<Cytoscape.Core>();

	useEffect(() => {

		async function fetchTree() {

			if(selectedEntity) {

				let doubleEncodedUri = encodeURIComponent(encodeURIComponent(selectedEntity.getUri()))

				let ancestorsPage = await getPaginated<any>(`/api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedUri}/ancestors?${new URLSearchParams({
					size: '100'
				})}`)
				
				let ancestors = ancestorsPage.elements.map(obj => thingFromProperties(obj))

				populateGraphFromEntities([ selectedEntity, ...ancestors ])

			} else {

				let page = await getPaginated<any>(`/api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
					isRoot: 'true',
					size: '100'
				})}`)

				let rootEntities = page.elements.map(obj => thingFromProperties(obj))

				// setRootNodes(rootEntities.map(entity => {
				// 	return {
				// 		uri: entity.getUri(),
				// 		absoluteIdentity: entity.getUri(),
				// 		title: entity.getName(),
				// 		expandable: entity.hasChildren(),
				// 		entity: entity
				// 	}
				// }))
				

			}
		}

		fetchTree()
		
	}, [ entityType ])

	function populateGraphFromEntities(entities:Entity[]) {

		let { rootEntities, uriToChildNodes } = extractEntityHierarchy(entities)

		let nodes:any[] = entities.map(entity => {
			return {
				data: {
					id: entity.getUri(),
					label: entity.getName(),
					position: {
						x: 50,
						y: 50
					}
				}
			}
		})

		let edges:any[] = []

		for(let parentUri of Array.from(uriToChildNodes.keys())) {
			for(let childEntity of uriToChildNodes.get(parentUri)) {
				edges.push({
					data: {
						source: parentUri,
						target: childEntity.getUri(),
						label: 'parent'
					}
				})
			}
		}

		setElements([ ...nodes, ...edges ])
	}
	
	if(!elements) {
		return <Spinner/>
	}

	return <CytoscapeComponent
		layout={{ name: 'cola' }}
		cy={(cy): void => {

			cy.on('add', 'node', _evt => {
				cy.layout({ name: 'cola' }).run()
				cy.fit()
			      })

			cyRef.current = cy
		
		}}
		elements={elements}
		style={{ width: '600px', height: '600px' }}
	/>

}



