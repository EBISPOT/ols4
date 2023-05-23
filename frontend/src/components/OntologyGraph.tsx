import { useEffect, useState } from "react";
import Ontology from "../model/Ontology";
import { getPaginated } from "../app/api";
import LoadingOverlay from "./LoadingOverlay";
import CytoscapeComponent from 'react-cytoscapejs';
import COSEBilkent from 'cytoscape-cose-bilkent';
import cytoscape from "cytoscape";

cytoscape.use(COSEBilkent);

const layout = { name: 'cose-bilkent', nodeDimensionsIncludeLabels: true, avoidOverlap:true, nodeRepulsion: 100000, idealEdgeLength:150, tile:false }

const styles = [
	{
		selector: 'node',
		style: {
			// textHalign: 'center',
			// textValign: 'center',
			// 'text-halign': 'center',
			// 'text-valign': 'center',
			'color': 'white',
			'background-color': '#106462',
			'padding': '4px',
			'border-radius': '2px',
			'content': 'data(label)',
			'text-halign':'center',
			'text-valign':'center',
			'width':'label',
			'height':'label',
			'shape':'roundrectangle'
		}
	},
	{
		selector: 'edge',
		style: {
			'target-arrow-shape': 'triangle',
			'width': 1
		}
	}
]

const hardcodedExcludeOntologies = [
	'bfo', 'oboinowl', 'dc', 'dcterms', 'owl', 'skos', 'ro'
]

export default function OntologyGraph() {

	let [elements,setElements] = useState<any[]>([]);

	useEffect(() => {

		async function fetchOntos() {
			let ontologies = await getPaginated<any>(
				`api/v2/ontologies?${new URLSearchParams({
					size: "1000",
				})}`)

			let excludeOntologies = new Set([
				...hardcodedExcludeOntologies
			])

			let filteredOntologies = ontologies.elements.filter(
				ont => !excludeOntologies.has(ont))

			let nodes = filteredOntologies.map(ont => {
				return {
					data: {
						id: ont.ontologyId,
						label: ont.ontologyId.toUpperCase()
					}
				}
			})

			let edges = filteredOntologies.flatMap(ont => {
				if(!ont.imports)
					return []
				return ont.imports.filter(otherId => !excludeOntologies.has(otherId)).map(otherId => {
					return {
						data: {
							source: ont.ontologyId,
							target: otherId
						}
					}
				})
			})

			setElements([...nodes, ...edges])
		}

		fetchOntos()
	}, [])

	if(elements.length === 0) {
		return <LoadingOverlay/>
	}

	return <CytoscapeComponent stylesheet={styles} className="h-96" elements={elements} layout={layout} minZoom={0.5} maxZoom={1.5} />

}
