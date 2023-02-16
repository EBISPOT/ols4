import Entity from "../../model/Entity";
import Ontology from "../../model/Ontology";
import extractEntityHierarchy from "./extractEntityHierarchy";
import { TreeNode } from "./ontologiesSlice";

export default function createTreeFromEntities(
  entities: Entity[],
  preferredRoots: boolean,
  ontology: Ontology
): { allNodes:TreeNode[], rootNodes: TreeNode[]; expandedNodes:Set<string>, nodeChildren: any } {
  let { rootEntities, uriToChildNodes } = extractEntityHierarchy(entities);

  if (preferredRoots) {
    let preferred = ontology.getPreferredRoots();
    if (preferred.length > 0) {
      let preferredRootEntities = preferred.map(
        (iri) => entities.filter((entity) => entity.getIri() === iri)[0]
      );
      rootEntities = preferredRootEntities.filter((entity) => !!entity);
    }
  }

  let allNodes:TreeNode[] = []
  let nodeChildren: any = {};
  let expandedNodes:Set<string> = new Set()

  return {
    allNodes: allNodes,
    rootNodes: rootEntities.map((rootEntity) =>
      createTreeNode(rootEntity, undefined, 0)
    ),
    nodeChildren,
    expandedNodes
  };

  function createTreeNode(
    node: Entity,
    parent: TreeNode | undefined,
    debugNumIterations: number
  ): TreeNode {
    if (debugNumIterations > 100) {
      throw new Error("probable cyclic tree (createTreeNode)");
    }
    const childNodes = uriToChildNodes.get(node.getIri()) || [];

    const treeNode: TreeNode = {
      absoluteIdentity: parent
        ? parent.absoluteIdentity + ";" + node.getIri()
        : node.getIri(),
      iri: node.getIri(),
      title: node.getName(),
      expandable: node.hasChildren(),
      entity: node,
      numDescendants:
        node.getNumHierarchicalDescendants() || node.getNumDescendants(),
    };

    allNodes.push(treeNode)

    nodeChildren[treeNode.absoluteIdentity] = childNodes.map((childNode) =>
      createTreeNode(childNode, treeNode, debugNumIterations + 1)
    );

    if(node.hasChildren() && childNodes.length > 0) {
	expandedNodes.add(treeNode.absoluteIdentity)
    }

    return treeNode;
  }
}
