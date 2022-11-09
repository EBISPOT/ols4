import { Link } from "@mui/material";
import { Map as ImmutableMap, Set as ImmutableSet } from "immutable";
import { useEffect, useState } from "react";
import { getPaginated } from "../../app/api";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
import Spinner from "../../components/Spinner";
import Entity from "../../model/Entity";
import { thingFromProperties } from "../../model/fromProperties";
import extractEntityHierarchy from "./extractEntityHierarchy";
import { getAncestors, getRootEntities } from "./ontologiesSlice";

interface TreeNode {
  // the IRIs of this node and its ancestors delimited by a ;
  absoluteIdentity: string;

  iri: string;
  title: string;
  expandable: boolean;

  entity: Entity;
}

export default function EntityTree(props: {
  ontologyId: string;
  selectedEntity?: Entity;
  entityType: "entities" | "classes" | "properties" | "individuals";
}) {
  const dispatch = useAppDispatch();
  const ancestors = useAppSelector((state) => state.ontologies.ancestors);
  const rootEntities = useAppSelector((state) => state.ontologies.rootEntities);

  let { ontologyId, entityType, selectedEntity } = props;

  let [rootNodes, setRootNodes] = useState<TreeNode[]>();
  let [nodeChildren, setNodeChildren] = useState<
    ImmutableMap<String, TreeNode[]>
  >(ImmutableMap());

  let [expandedNodes, setExpandedNodes] = useState<ImmutableSet<String>>(
    ImmutableSet()
  );

  useEffect(() => {
    if (selectedEntity) {
      let entityUri = selectedEntity.getIri();
      dispatch(getAncestors({ ontologyId, entityType, entityUri }));
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
    let { rootEntities, uriToChildNodes } = extractEntityHierarchy(entities);

    let newNodeChildren = new Map<String, TreeNode[]>();
    let newExpandedNodes = new Set<String>();

    setRootNodes(rootEntities.map((rootEntity) => createTreeNode(rootEntity)));

    setNodeChildren(ImmutableMap(newNodeChildren));
    setExpandedNodes(ImmutableSet(newExpandedNodes));

    function createTreeNode(node: Entity, parent?: TreeNode): TreeNode {
      let childNodes = uriToChildNodes.get(node.getIri()) || [];

      let treeNode: TreeNode = {
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
  async function toggleNode(node) {
    if (expandedNodes.has(node.absoluteIdentity)) {
      // closing a node

      setExpandedNodes(expandedNodes.delete(node.absoluteIdentity));
    } else {
      // opening a node

      setExpandedNodes(expandedNodes.add(node.absoluteIdentity));

      let doubleEncodedUri = encodeURIComponent(encodeURIComponent(node.iri));

      let page = await getPaginated<any>(
        `/api/v2/ontologies/${ontologyId}/${node.entity.getTypePlural()}/${doubleEncodedUri}/children?${new URLSearchParams(
          {
            size: "100",
          }
        )}`
      );

      let childTerms = page.elements.map((obj) => thingFromProperties(obj));

      setNodeChildren(
        nodeChildren.set(
          node.absoluteIdentity,
          childTerms.map((term) => {
            return {
              iri: term.getIri(),
              absoluteIdentity: node.absoluteIdentity + ";" + term.getIri(),
              title: term.getName(),
              expandable: term.hasChildren(),
              entity: term,
            };
          })
        )
      );
    }
  }

  if (!rootNodes) {
    return <Spinner />;
  }

  return (
    <div id="term-tree" className="jstree jstree-1 jstree-proton" role="tree">
      <ul
        className="jstree-container-ul jstree-children jstree-no-icons"
        role="group"
      >
        {rootNodes.map((node, i) => {
          let isLast = i == rootNodes!.length - 1;
          let isExpanded = expandedNodes.has(node.absoluteIdentity);
          let termUrl = encodeURIComponent(encodeURIComponent(node.iri));
          let highlight =
            (selectedEntity && node.iri === selectedEntity.getIri()) || false;
          return (
            <TreeNode
              expandable={node.expandable}
              expanded={isExpanded}
              highlight={highlight}
              isLast={isLast}
              onClick={() => toggleNode(node)}
              key={randomString()}
            >
              <Link
                href={`/ontologies/${ontologyId}/${node.entity.getTypePlural()}/${termUrl}`}
              >
                {node.title}
              </Link>
              {isExpanded && renderNodeChildren(node)}
            </TreeNode>
          );
        })}
      </ul>
    </div>
  );

  function renderNodeChildren(node: TreeNode) {
    let children = nodeChildren.get(node.absoluteIdentity);

    if (children === undefined) {
      return <Spinner />;
    }

    return (
      <ul role="group" className="jstree-children" style={{}}>
        {children.map((childNode: TreeNode, i) => {
          let isExpanded = expandedNodes.has(childNode.absoluteIdentity);
          console.log(
            "node " + childNode.absoluteIdentity + " expanded " + isExpanded
          );
          let isLast = i == children!.length - 1;
          let termUrl = encodeURIComponent(encodeURIComponent(childNode.iri));
          let highlight =
            (selectedEntity && childNode.iri === selectedEntity.getIri()) ||
            false;
          return (
            <TreeNode
              expandable={childNode.expandable}
              expanded={isExpanded}
              highlight={highlight}
              isLast={isLast}
              onClick={() => toggleNode(childNode)}
              key={randomString()}
            >
              {/* <Link href={`/ontologies/${ontologyId}/${termType}/${termUrl}`}> */}
              <Link
                href={`/ontologies/${ontologyId}/${node.entity.getTypePlural()}/${termUrl}`}
              >
                {childNode.title}
              </Link>
              {isExpanded && renderNodeChildren(childNode)}
            </TreeNode>
          );
        })}
      </ul>
    );
  }
}

function TreeNode(props: {
  expandable: boolean;
  expanded: boolean;
  highlight: boolean;
  isLast: boolean;
  children: any;
  onClick: () => void;
}) {
  let { expandable, expanded, highlight, isLast, children, onClick } = props;

  let classes: string[] = ["jstree-node"];

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
