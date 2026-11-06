import Entity from "../../../model/Entity";
import Ontology from "../../../model/Ontology";
import extractEntityHierarchy from "./extractEntityHierarchy";
import { TreeNode } from "../ontologiesSlice";

export default function createTreeFromEntities(
  entities: Entity[],
  preferredRoots: boolean,
  ontology: Ontology,
  specificRootIri: string,
  preserveSelectedContext = false
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
      const preferredOnPath = preferredRootEntities.filter((entity) => !!entity);
      if (preferredOnPath.length || !preserveSelectedContext) rootEntities = preferredOnPath;
    }
  }

  let allNodes:TreeNode[] = []
  let nodeChildren: any = {};
  let automaticallyExpandedNodes:Set<string> = new Set()

  return {
    allNodes: allNodes,
    rootNodes: rootEntities.map((rootEntity) =>
      createTreeNode(rootEntity, undefined, new Set(), null, null)
    ),
    nodeChildren,
    automaticallyExpandedNodes
  };

  function createTreeNode(
    node: Entity,
    parent: TreeNode | undefined,
    ancestorIris: Set<string>,
    parentRelationToChild:string|null,
    childRelationToParent:string|null,
  ): TreeNode {
    // Detect actual repeated IRIs on this path, rather than treating depth as a cycle.
    const path = new Set(ancestorIris).add(node.getIri());
    const childNodes = (parentToChildRelations.get(node.getIri()) || [])
      .filter(relation => !path.has(relation.child.getIri()));

    const treeNode: TreeNode = {
      absoluteIdentity: parent
        ? parent.absoluteIdentity + ";" + node.getIri()
        : node.getIri(),
      iri: node.getIri(),
      title: node.getName(),
      expandable: node.isExpandableInTree(),
      entity: node,
      numDescendants: node.getNumDescendants(),
      numHierarchicalDescendants: node.getNumHierarchicalDescendants(),
      parentRelationToChild,
      childRelationToParent
    };

    allNodes.push(treeNode)

    nodeChildren[treeNode.absoluteIdentity] = childNodes.map((childNode) =>
      createTreeNode(childNode.child, treeNode, path, childNode.parentRelationToChild, childNode.childRelationToParent)
    );

    if(node.isExpandableInTree() && childNodes.length > 0) {
	automaticallyExpandedNodes.add(treeNode.absoluteIdentity)
    }

    return treeNode;
  }
}
