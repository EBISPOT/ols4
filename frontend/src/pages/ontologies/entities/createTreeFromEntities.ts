import Entity from "../../../model/Entity";
import Ontology from "../../../model/Ontology";
import extractEntityHierarchy from "./extractEntityHierarchy";
import { TreeNode } from "../ontologiesSlice";

export default function createTreeFromEntities(
  entities: Entity[],
  preferredRoots: boolean,
  ontology: Ontology,
  specificRootIri: string
): { allNodes:TreeNode[], rootNodes: TreeNode[]; automaticallyExpandedNodes:Set<string>, nodeChildren: any } {
  let { rootEntities, parentToChildRelations } = extractEntityHierarchy(entities);

  if(specificRootIri) {
    let specificRootEntity = entities.find(entity => entity.getIri() === specificRootIri)
    if (specificRootEntity) {
      rootEntities = [specificRootEntity];
    }
  }

  if (preferredRoots && !specificRootIri) {
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
  let automaticallyExpandedNodes:Set<string> = new Set()

  return {
    allNodes: allNodes,
    rootNodes: rootEntities.map((rootEntity) =>
      createTreeNode(rootEntity, undefined, 0, null, null)
    ),
    nodeChildren,
    automaticallyExpandedNodes
  };

  function createTreeNode(
    node: Entity,
    parent: TreeNode | undefined,
    debugNumIterations: number,
    parentRelationToChild:string|null,
    childRelationToParent:string|null,
  ): TreeNode {
    if (debugNumIterations > 100) {
      throw new Error("probable cyclic tree (createTreeNode)");
    }
    const childNodes = parentToChildRelations.get(node.getIri()) || [];

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
      parentRelationToChild,
      childRelationToParent
    };

    allNodes.push(treeNode)

    nodeChildren[treeNode.absoluteIdentity] = childNodes.map((childNode) =>
      createTreeNode(childNode.child, treeNode, debugNumIterations + 1, childNode.parentRelationToChild, childNode.childRelationToParent)
    );

    if(node.hasChildren() && childNodes.length > 0) {
	automaticallyExpandedNodes.add(treeNode.absoluteIdentity)
    }

    return treeNode;
  }
}
