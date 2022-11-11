import { Link } from "@mui/material";
import { Map as ImmutableMap, Set as ImmutableSet } from "immutable";
import { useEffect, useState } from "react";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
import LoadingOverlay from "../../components/LoadingOverlay";
import Entity from "../../model/Entity";
import extractEntityHierarchy from "./extractEntityHierarchy";
import {
  getAncestors,
  getNodeChildren,
  getRootEntities,
  TreeNode
} from "./ontologiesSlice";

export default function EntityTree(props: {
  ontologyId: string;
  selectedEntity?: Entity;
  entityType: "entities" | "classes" | "properties" | "individuals";
}) {
  const dispatch = useAppDispatch();
  const ancestors = useAppSelector((state) => state.ontologies.ancestors);
  const children = useAppSelector((state) => state.ontologies.nodeChildren);
  const rootEntities = useAppSelector((state) => state.ontologies.rootEntities);
  const loading = useAppSelector(
    (state) => state.ontologies.loadingNodeChildren
  );

  const { ontologyId, entityType, selectedEntity } = props;

  const [rootNodes, setRootNodes] = useState<TreeNode[]>();
  const [nodeChildren, setNodeChildren] = useState<
    ImmutableMap<String, TreeNode[]>
  >(ImmutableMap());

  const [expandedNodes, setExpandedNodes] = useState<ImmutableSet<String>>(
    ImmutableSet()
  );

  useEffect(() => {
    if (selectedEntity) {
      const entityIri = selectedEntity.getIri();
      dispatch(getAncestors({ ontologyId, entityType, entityIri }));
    } else {
      dispatch(getRootEntities({ ontologyId, entityType }));
    }
  }, [entityType]);

  useEffect(() => {
    if (selectedEntity) {
      populateTreeFromEntities([selectedEntity, ...ancestors]);
    }
  }, [ancestors]);

  useEffect(() => {
    setNodeChildren(nodeChildren.merge(children));
  }, [children]);

  useEffect(() => {
    setRootNodes(
      rootEntities.map((entity: Entity) => {
        return {
          iri: entity.getIri(),
          absoluteIdentity: entity.getIri(),
          title: entity.getName(),
          expandable: entity.hasChildren(),
          entity: entity,
        };
      })
    );
  }, [rootEntities]);

  function populateTreeFromEntities(entities: Entity[]) {
    const { rootEntities, uriToChildNodes } = extractEntityHierarchy(entities);

    const newNodeChildren = new Map<String, TreeNode[]>();
    const newExpandedNodes = new Set<String>();

    setRootNodes(rootEntities.map((rootEntity) => createTreeNode(rootEntity)));

    setNodeChildren(ImmutableMap(newNodeChildren));
    setExpandedNodes(ImmutableSet(newExpandedNodes));

    function createTreeNode(node: Entity, parent?: TreeNode): TreeNode {
      const childNodes = uriToChildNodes.get(node.getIri()) || [];

      const treeNode: TreeNode = {
        absoluteIdentity: parent
          ? parent.absoluteIdentity + ";" + node.getIri()
          : node.getIri(),
        iri: node.getIri(),
        title: node.getName(),
        expandable: node.hasChildren(),
        entity: node,
      };

      newNodeChildren.set(
        treeNode.absoluteIdentity,
        childNodes.map((childNode) => createTreeNode(childNode, treeNode))
      );

      if (treeNode.iri !== selectedEntity?.getIri()) {
        newExpandedNodes.add(treeNode.absoluteIdentity);
      }

      return treeNode;
    }
  }
  function toggleNode(node: any) {
    if (expandedNodes.has(node.absoluteIdentity)) {
      // closing a node
      setExpandedNodes(expandedNodes.delete(node.absoluteIdentity));
    } else {
      // opening a node
      setExpandedNodes(expandedNodes.add(node.absoluteIdentity));
      const entityTypePlural = node.entity.getTypePlural();
      const entityIri = node.iri;
      const absoluteIdentity = node.absoluteIdentity;
      dispatch(
        getNodeChildren({
          ontologyId,
          entityTypePlural,
          entityIri,
          absoluteIdentity,
        })
      );
    }
  }

  return (
    <div>
      {rootNodes ? (
        <div
          id="term-tree"
          className="jstree jstree-1 jstree-proton"
          role="tree"
        >
          {renderNodeChildren(rootNodes)}
        </div>
      ) : null}
      {loading ? <LoadingOverlay message="Loading children..." /> : null}
    </div>
  );
  function renderNodeChildren(children: TreeNode[]) {
    const sortedChildren = [...children];
    sortedChildren.sort((a, b) => {
      const titleA = a?.title ? a.title.toString().toUpperCase() : "";
      const titleB = b?.title ? b.title.toString().toUpperCase() : "";
      return titleA == titleB ? 0 : titleA > titleB ? 1 : -1;
    });
    return (
      <ul
        role="group"
        className="jstree-container-ul jstree-children jstree-no-icons"
      >
        {sortedChildren.map((childNode: TreeNode, i) => {
          const isExpanded = expandedNodes.has(childNode.absoluteIdentity);
          const isLast = i === sortedChildren!.length - 1;
          const termUrl = encodeURIComponent(encodeURIComponent(childNode.iri));
          const highlight =
            (selectedEntity && childNode.iri === selectedEntity.getIri()) ||
            false;
          return (
            <JsTreeNode
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
                href={`/ontologies/${ontologyId}/${childNode.entity.getTypePlural()}/${termUrl}`}
              >
                {childNode.title}
              </Link>
              {isExpanded &&
                renderNodeChildren(
                  nodeChildren.get(childNode.absoluteIdentity) || []
                )}
            </JsTreeNode>
          );
        })}
      </ul>
    );
  }
}

function JsTreeNode(props: {
  expandable: boolean;
  expanded: boolean;
  highlight: boolean;
  isLast: boolean;
  children: any;
  onClick: () => void;
}) {
  const { expandable, expanded, highlight, isLast, onClick } = props;
  let { children } = props;

  const classes: string[] = ["jstree-node"];

  if (expanded) {
    classes.push("jstree-open");
  } else {
    classes.push("jstree-closed");
  }

  if (!expandable) {
    classes.push("jstree-leaf");
  }

  if (isLast) {
    classes.push("jstree-last");
  }

  if (highlight) {
    children = <span className="jstree-clicked">{children}</span>;
  }

  return (
    <li role="treeitem" className={classes.join(" ")}>
      <i
        className="jstree-icon jstree-ocl"
        role="presentation"
        onClick={onClick}
      />
      {children}
    </li>
  );
}
