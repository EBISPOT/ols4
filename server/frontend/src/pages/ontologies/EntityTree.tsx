


import React, { useEffect, useState } from "react";
import OlsDatatable, { Column } from "../../components/OlsDatatable";
import { getPaginated } from "../../api";
import Entity from "../../model/Entity";
import Spinner from "../../components/Spinner";
import { Link } from "@mui/material";
import { Set as ImmutableSet, Map as ImmutableMap } from 'immutable'
import Multimap from 'multimap'
import assert from "assert";
import { thingFromProperties } from "../../model/fromProperties";

interface TreeNode {

	// the URIs of this node and its ancestors delimited by a ;
	absoluteIdentity:string

	uri:string
	title:string
	expandable:boolean

	entity:Entity
}

export default function EntityTree(props:{
	ontologyId:string
	startingNode?:Entity,
	entityType:'entities'|'classes'|'properties'|'individuals'
}) {
	let { ontologyId, entityType, startingNode } = props

	let [ rootNodes, setRootNodes ] = useState<TreeNode[]>()
	let [ nodeChildren, setNodeChildren ] = useState<ImmutableMap<String,TreeNode[]>>(ImmutableMap())

	let [ expandedNodes, setExpandedNodes ] = useState<ImmutableSet<String>>(ImmutableSet())

	useEffect(() => {

		async function fetchTree() {

			if(startingNode) {

				let doubleEncodedUri = encodeURIComponent(encodeURIComponent(startingNode.getUri()))

				let ancestorsPage = await getPaginated<any>(`/api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedUri}/ancestors?${new URLSearchParams({
					size: '100'
				})}`)
				
				let ancestors = ancestorsPage.elements.map(obj => thingFromProperties(obj))

				populateTreeFromTermNodes([ startingNode, ...ancestors ])

			} else {

				let page = await getPaginated<any>(`/api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
					isRoot: 'true',
					size: '100'
				})}`)

				let rootEntities = page.elements.map(obj => thingFromProperties(obj))

				setRootNodes(rootEntities.map(entity => {
					return {
						uri: entity.getUri(),
						absoluteIdentity: entity.getUri(),
						title: entity.getName(),
						expandable: entity.hasChildren(),
						entity: entity
					}
				}))
				

			}
		}

		fetchTree()
		
	}, [ entityType ])

	function populateTreeFromTermNodes(termNodes: Entity[]) {

		let uriToNode: Map<string, Entity> = new Map()
		let uriToChildNodes: Multimap<string, Entity> = new Multimap()
		let uriToParentNodes: Multimap<string, Entity> = new Multimap()

		for (let node of termNodes) {
			uriToNode.set(node.getUri(), node)
		}

		for (let node of termNodes) {
			let parents = node.getParents()
				// not interested in bnode subclassofs like restrictions etc
				.filter(parent => typeof parent === 'string')
				.map(parentUri => uriToNode.get(parentUri))
				.filter(parent => parent !== undefined)

			for (let parent of parents) {
				assert(parent)
				uriToChildNodes.set(parent.getUri(), node)
				uriToParentNodes.set(node.getUri(), parent)
			}
		}

		let rootTermNodes = termNodes.filter((node) => {
			return (uriToParentNodes.get(node.getUri()) || []).length === 0
		})

		setRootNodes(
			rootTermNodes.map((rootTerm) => createTreeNode(rootTerm))
		)

		function createTreeNode(node: Entity, parent?: TreeNode): TreeNode {

			let childNodes = uriToChildNodes.get(node.getUri()) || []

			let treeNode: TreeNode = {
				absoluteIdentity: parent ? parent.absoluteIdentity + ';' + node.getUri() : node.getUri(),
				uri: node.getUri(),
				title: node.getName(),
				expandable: node.hasChildren(),
				entity: node
			}

			setNodeChildren(
				nodeChildren.set(treeNode.absoluteIdentity, childNodes.map(
					childNode => createTreeNode(childNode, treeNode)))
			)

			setExpandedNodes(
				expandedNodes.add(treeNode.absoluteIdentity)
			)

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

			let doubleEncodedUri = encodeURIComponent(encodeURIComponent(node.uri))

			let page = await getPaginated<any>(`/api/v2/ontologies/${ontologyId}/${node.entity.getTypePlural()}/${doubleEncodedUri}/children?${new URLSearchParams({
				size: '100'
			})}`)

			let childTerms = page.elements.map(obj => thingFromProperties(obj))

			setNodeChildren(
				nodeChildren.set(node.absoluteIdentity, childTerms.map(term => {
					return {
						uri: term.getUri(),
						absoluteIdentity: node.absoluteIdentity + ';' + term.getUri(),
						title: term.getName(),
						expandable: term.hasChildren(),
						entity: term
					}
				}))
			)
		}
	}

	if(!rootNodes) {
		return <Spinner/>
	}

	return 	<div id="term-tree" className="jstree jstree-1 jstree-proton" role="tree">
	<ul className="jstree-container-ul jstree-children jstree-no-icons" role="group">
		{rootNodes.map((node, i) => {
			let isLast = i == (rootNodes!.length - 1)
			let isExpanded = expandedNodes.has(node.absoluteIdentity)
			let termUrl = encodeURIComponent(encodeURIComponent(node.uri))
			return <TreeNode
				expandable={node.expandable}
				expanded={isExpanded}
				isLast={isLast}
				onClick={() => toggleNode(node)}
				>
					<Link href={`/ontologies/${ontologyId}/${node.entity.getTypePlural()}/${termUrl}`}>
					{node.title}
					</Link>
					{ isExpanded && renderNodeChildren(node) }
				</TreeNode>
		})}
	</ul>
	</div>

	function renderNodeChildren(node:TreeNode) {

		let children = nodeChildren.get(node.absoluteIdentity)

		if(children === undefined) {
			return <Spinner/>
		}

 		return <ul role="group" className="jstree-children" style={{}}>
			{
				children.map((childNode:TreeNode, i) => {
					let isExpanded = expandedNodes.has(childNode.absoluteIdentity)
					let isLast = i == (children!.length - 1)
					let termUrl = encodeURIComponent(encodeURIComponent(childNode.uri))
					return <TreeNode
						expandable={childNode.expandable}
						expanded={isExpanded}
						isLast={isLast}
						onClick={() => toggleNode(childNode)}
						>
							{/* <Link href={`/ontologies/${ontologyId}/${termType}/${termUrl}`}> */}
							<Link href={`/ontologies/${ontologyId}/${node.entity.getTypePlural()}/${termUrl}`}>
								{childNode.title}
							</Link>
							{ isExpanded && renderNodeChildren(childNode) }
						</TreeNode>
				})
			}
		</ul>
	}
}

function TreeNode(props:{ expandable:boolean, expanded:boolean, isLast:boolean, children:any, onClick:()=>void }) {

	let { expandable, expanded, isLast, children, onClick } = props

	let classes:string[] = [ 'jstree-node' ]

	if(expanded) {
		classes.push('jstree-open')
	} else {
		classes.push('jstree-closed')
	}

	if(!expandable) {
		classes.push('jstree-leaf')
	}

	if(isLast) {
		classes.push('jstree-last')
	}

	return <li role="treeitem" className={classes.join(' ')}>
		<i className="jstree-icon jstree-ocl" role="presentation" onClick={onClick} />
		{children}
	</li>
}



