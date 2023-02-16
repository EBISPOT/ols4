

import React, { Fragment, useEffect, useState } from "react";
import Entity from "../../model/Entity";
import { Link } from "react-router-dom";
import { Set as ImmutableSet, Map as ImmutableMap } from 'immutable'
import Multimap from 'multimap'
import assert from "assert";
import { thingFromProperties } from "../../model/fromProperties";
import extractEntityHierarchy from "./extractEntityHierarchy";
import { getPaginated } from "../../app/api";
import LoadingOverlay from "../../components/LoadingOverlay";
import Node from "../../components/Node";
import { randomString } from "../../app/util";
import { FormControl, RadioGroup, FormControlLabel, Radio } from "@mui/material";
import Ontology from "../../model/Ontology";

interface TreeNode {

	// the URIs of this node and its ancestors delimited by a ;
	absoluteIdentity:string

	iri:string
	title:string
	expandable:boolean

	entity:Entity

	numDescendants:number
}

export default function EntityTree(props:{
	ontology:Ontology,
	selectedEntity?:Entity,
	entityType:'entities'|'classes'|'properties'|'individuals',
	lang:string
}) {
	let { ontology, entityType, selectedEntity, lang } = props

	let [ rootNodes, setRootNodes ] = useState<TreeNode[]>()
	let [ nodeChildren, setNodeChildren ] = useState<ImmutableMap<string,TreeNode[]>>(ImmutableMap())

	let [ expandedNodes, setExpandedNodes ] = useState<ImmutableSet<string>>(ImmutableSet())

	const [preferredRoots, setPreferredRoots] = useState<boolean>(ontology.getPreferredRoots().length > 0);
	const [loading, setLoading] = useState<boolean>(true)

	useEffect(() => {

		async function fetchTree() {

			if(selectedEntity) {

				let doubleEncodedUri = encodeURIComponent(encodeURIComponent(selectedEntity.getIri()))
				let ancestorsEndpoint = entityType === 'classes' ? 'hierarchicalAncestors' : 'ancestors'

				let ancestorsPage = await getPaginated<any>(`api/v2/ontologies/${ontology.getOntologyId()}/${entityType}/${doubleEncodedUri}/${ancestorsEndpoint}?${new URLSearchParams({
					size: '100'
				})}`)
				
				let ancestors = ancestorsPage.elements.map(obj => thingFromProperties(obj))

				populateTreeFromEntities([ selectedEntity, ...ancestors ])

			} else {

				let page = await getPaginated<any>(`api/v2/ontologies/${ontology.getOntologyId()}/${entityType}?${new URLSearchParams({
					hasHierarchicalParent: 'false',
					size: '100'
				})}`)

				let rootEntities = page.elements.map(obj => thingFromProperties(obj))

				setRootNodes(rootEntities.map(entity => {
					return {
						iri: entity.getIri(),
						absoluteIdentity: entity.getIri(),
						title: entity.getName(),
						expandable: entity.hasChildren(),
						entity: entity,
						numDescendants: entity.getNumHierarchicalDescendants() || entity.getNumDescendants()
					}
				}))
				

			}

			for (let alreadyExpanded of expandedNodes.toArray()) {

				// get children of already expanded nodes

				let alreadyExpandedIri = alreadyExpanded.split(';').pop() as string
				let doubleEncodedUri = encodeURIComponent(encodeURIComponent(alreadyExpandedIri))
				let childrenEndpoint = entityType === 'classes' ? 'hierarchicalChildren' : 'children'

				let page = await getPaginated<any>(`api/v2/ontologies/${ontology.getOntologyId()}/${entityType}/${doubleEncodedUri}/${childrenEndpoint}?${new URLSearchParams({
					size: '100'
				})}`)

				let childEntities = page.elements.map(obj => thingFromProperties(obj))

				setNodeChildren(
					nodeChildren.set(alreadyExpanded, childEntities.map(entity => {
						return {
							iri: entity.getIri(),
							absoluteIdentity: alreadyExpanded,
							title: entity.getName(),
							expandable: entity.hasChildren(),
							entity: entity,
							numDescendants: entity.getNumHierarchicalDescendants() || entity.getNumDescendants()
						}
					}))
				)
			}

			setLoading(false)
		}

		fetchTree()
		
	}, [ entityType ])

	function populateTreeFromEntities(entities: Entity[]) {

		let { rootEntities, uriToChildNodes } = extractEntityHierarchy(entities)

		if (preferredRoots) {
			let preferred = ontology.getPreferredRoots()
			if (preferred.length > 0) {
				let preferredRootEntities = preferred.map(iri => entities.filter(entity => entity.getIri() === iri)[0])
				rootEntities = preferredRootEntities.filter(entity => !!entity)
			}
		}

		let newNodeChildren = new Map<string, TreeNode[]>()
		let newExpandedNodes = new Set<string>()

		setRootNodes(
			rootEntities.map((rootEntity) => createTreeNode(rootEntity))
		)

		setNodeChildren(ImmutableMap(newNodeChildren))
		setExpandedNodes(ImmutableSet(newExpandedNodes))

		function createTreeNode(node: Entity, parent?: TreeNode): TreeNode {

			let childNodes = uriToChildNodes.get(node.getIri()) || []

			let treeNode: TreeNode = {
				absoluteIdentity: parent ? parent.absoluteIdentity + ';' + node.getIri() : node.getIri(),
				iri: node.getIri(),
				title: node.getName(),
				expandable: node.hasChildren(),
				entity: node,
				numDescendants: node.getNumHierarchicalDescendants() || node.getNumDescendants()
			}

			newNodeChildren.set(
				treeNode.absoluteIdentity,
				childNodes.map(childNode => createTreeNode(childNode, treeNode))
			)

			if(treeNode.iri !== selectedEntity?.getIri()) {
				newExpandedNodes.add(treeNode.absoluteIdentity)
			}

			return treeNode
		}

	}
	async function toggleNode(node) {

		if(expandedNodes.has(node.absoluteIdentity)) {

			// closing a node

			setExpandedNodes(expandedNodes.delete(node.absoluteIdentity))

		} else {

			// opening a node

			setExpandedNodes(expandedNodes.add(node.absoluteIdentity))
			setLoading(true)

			let doubleEncodedUri = encodeURIComponent(encodeURIComponent(node.iri))
			let childrenEndpoint = entityType === 'classes' ? 'hierarchicalChildren' : 'children'

			let page = await getPaginated<any>(`api/v2/ontologies/${ontology.getOntologyId()}/${node.entity.getTypePlural()}/${doubleEncodedUri}/${childrenEndpoint}?${new URLSearchParams({
				size: '100'
			})}`)

			let childEntities = page.elements.map(obj => thingFromProperties(obj))

			setNodeChildren(
				nodeChildren.set(node.absoluteIdentity, childEntities.map(entity => {
					return {
						iri: entity.getIri(),
						absoluteIdentity: node.absoluteIdentity + ';' + entity.getIri(),
						title: entity.getName(),
						expandable: entity.hasChildren(),
						entity: entity,
						numDescendants: entity.getNumHierarchicalDescendants() || entity.getNumDescendants()
					}
				}))
			)

			setLoading(false)
		}
	}

	if(!rootNodes) {
		return  <LoadingOverlay message="Loading tree..." /> 
	}

      function renderNodeChildren(
	children: TreeNode[],
	debugNumIterations: number
      ) {
	if (debugNumIterations > 100) {
	  throw new Error("probable cyclic tree (renderNodeChildren)");
	}
	const childrenCopy = [...children];
	childrenCopy.sort((a, b) => {
	  const titleA = a?.title ? a.title.toString().toUpperCase() : "";
	  const titleB = b?.title ? b.title.toString().toUpperCase() : "";
	  return titleA === titleB ? 0 : titleA > titleB ? 1 : -1;
	});
	const childrenSorted = childrenCopy.filter(
	  (child, i, arr) =>
	    !i || child.absoluteIdentity !== arr[i - 1].absoluteIdentity
	);
	return (
	  <ul
	    role="group"
	    className="jstree-container-ul jstree-children jstree-no-icons"
	  >
	    {childrenSorted.map((childNode: TreeNode, i) => {
	      const isExpanded = expandedNodes.has(childNode.absoluteIdentity);
	      const isLast = i === childrenSorted!.length - 1;
	      const termUrl = encodeURIComponent(encodeURIComponent(childNode.iri));
	      const highlight =
		(selectedEntity && childNode.iri === selectedEntity.getIri()) ||
		false;
	      return (
		<Node
		  expandable={childNode.expandable}
		  expanded={isExpanded}
		  highlight={highlight}
		  isLast={isLast}
		  onClick={() => {
		    toggleNode(childNode);
		  }}
		  key={randomString()}
		>
		  <Link
		    to={`/ontologies/${ontology.getOntologyId()}/${childNode.entity.getTypePlural()}/${termUrl}`}
		  >
		    {childNode.title}
		  </Link>
		  {
		    childNode.numDescendants > 0 &&
		      <span style={{color:'gray'}}>{' (' + childNode.numDescendants.toLocaleString() + ')'}</span>
		  }
		  {isExpanded &&
		    renderNodeChildren(
		      nodeChildren.get(childNode.absoluteIdentity) || [],
		      debugNumIterations + 1
		    )}
		</Node>
	      );
	    })}
	  </ul>
	);
      }

      return <Fragment>
      <div style={{position: 'relative'}}>
	  {ontology.getPreferredRoots().length > 0 &&
	  <div style={{position:'absolute', right:0, top:0}}>
		  <FormControl>
			  <RadioGroup
				  name="radio-buttons-group"
				  value={preferredRoots ? "true": "false"}
			  >
				  <FormControlLabel value="true" onClick={() => setPreferredRoots(true)} control={<Radio  />} label="Preferred roots" />
				  <FormControlLabel value="false"  onClick={() => setPreferredRoots(false)}  control={<Radio/>} label="All classes" />
			  </RadioGroup>
		  </FormControl>
		  </div>}
	{rootNodes ? (
	  <div className="px-3 jstree jstree-1 jstree-proton" role="tree">
	    {renderNodeChildren(rootNodes, 0)}
	  </div>
	) : null}
	{loading ? <LoadingOverlay message="Loading children..." /> : null}
      </div>
    </Fragment>
}
