import {
  FormControl,
  FormControlLabel,
  Radio,
  RadioGroup
} from "@mui/material";
import { Fragment, useEffect } from "react";
import { Link } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
import LoadingOverlay from "../../components/LoadingOverlay";
import Node from "../../components/Node";
import Entity from "../../model/Entity";
import Ontology from "../../model/Ontology";
import {
  closeNode,
  disablePreferredRoots,
  enablePreferredRoots,
  getAncestors,
  getNodeChildren,
  getRootEntities,
  openNode,
  resetTree,
  TreeNode
} from "./ontologiesSlice";

export default function EntityTree({
  ontology,
  entityType,
  selectedEntity,
  lang,
}: {
  ontology: Ontology;
  selectedEntity?: Entity;
  entityType: "entities" | "classes" | "properties" | "individuals";
  lang: string;
}) {
  const dispatch = useAppDispatch();
  // const ancestors = useAppSelector((state) => state.ontologies.ancestors);
  const nodeChildren = useAppSelector((state) => state.ontologies.nodeChildren);
  const rootNodes = useAppSelector((state) => state.ontologies.rootNodes);
  const loading = useAppSelector(
    (state) => state.ontologies.loadingNodeChildren
  );
  const preferredRoots = useAppSelector(
    (state) => state.ontologies.preferredRoots
  );
  const expandedNodes = useAppSelector(
    (state) => state.ontologies.expandedNodes
  );

  // const [rootNodes, setRootNodes] = useState<TreeNode[]>();
  // const [nodeChildren, setNodeChildren] = useState<
  //   ImmutableMap<String, TreeNode[]>
  // >(ImmutableMap());
  // const [expandedNodes, setExpandedNodes] = useState<ImmutableSet<String>>(
  //   ImmutableSet()
  // );

  const toggleNode = (node: any) => {
    if (expandedNodes.indexOf(node.absoluteIdentity) !== -1) {
      // closing a node
      dispatch(closeNode(node));
    } else {
      // opening a node
      dispatch(openNode(node));
      const entityIri = node.iri;
      const absoluteIdentity = node.absoluteIdentity;
      dispatch(
        getNodeChildren({
          ontologyId: ontology.getOntologyId(),
          entityTypePlural: entityType,
          entityIri,
          absoluteIdentity,
          lang,
        })
      );
    }
  };

  useEffect(() => {
    if (selectedEntity) {
      const entityIri = selectedEntity.getIri();
      dispatch(
        getAncestors({
          ontologyId: ontology.getOntologyId(),
          entityType,
          entityIri,
          lang,
        })
      );
    } else {
      dispatch(
        getRootEntities({
          ontologyId: ontology.getOntologyId(),
          entityType,
          preferredRoots,
          lang,
        })
      );
    }

    // for(let alreadyExpanded of expandedNodes.toArray()) {
    //   dispatch(
    //     getNodeChildren({
    //       ontologyId: ontology.getOntologyId(),
    //       entityTypePlural: entityType,
    //       entityIri: alreadyExpanded.split(';').pop(),
    //       absoluteIdentity: alreadyExpanded,
    // lang
    //     })
    //   )
    // }
  }, [dispatch, entityType, selectedEntity, ontology, preferredRoots, lang]);

  // useEffect(() => {
  //   setNodeChildren(nodeChildren.merge(children));
  // }, [nodeChildren, children, lang]);

  // useEffect(() => {
  //   setRootNodes(
  //     rootEntities.map((entity: Entity) => {
  //       return {
  //         iri: entity.getIri(),
  //         absoluteIdentity: entity.getIri(),
  //         title: entity.getName(),
  //         expandable: entity.hasChildren(),
  //         entity: entity,
  //         numDescendants:
  //           entity.getNumHierarchicalDescendants() ||
  //           entity.getNumDescendants(),
  //       };
  //     })
  //   );
  // }, [rootEntities, lang]);

  useEffect(() => {
    dispatch(resetTree());
  }, [ontology, selectedEntity]);

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
          const isExpanded =
            expandedNodes.indexOf(childNode.absoluteIdentity) !== -1;
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
              {childNode.numDescendants > 0 && (
                <span style={{ color: "gray" }}>
                  {" (" + childNode.numDescendants.toLocaleString() + ")"}
                </span>
              )}
              {isExpanded &&
                renderNodeChildren(
                  nodeChildren[childNode.absoluteIdentity] || [],
                  debugNumIterations + 1
                )}
            </Node>
          );
        })}
      </ul>
    );
  }
  return (
    <Fragment>
      <div style={{ position: "relative" }}>
        {ontology.getPreferredRoots().length > 0 && (
          <div style={{ position: "absolute", right: 0, top: 0 }}>
            <FormControl>
              <RadioGroup
                name="radio-buttons-group"
                value={preferredRoots ? "true" : "false"}
              >
                <FormControlLabel
                  value="true"
                  onClick={() => dispatch(enablePreferredRoots())}
                  control={<Radio />}
                  label="Preferred roots"
                />
                <FormControlLabel
                  value="false"
                  onClick={() => dispatch(disablePreferredRoots())}
                  control={<Radio />}
                  label="All classes"
                />
              </RadioGroup>
            </FormControl>
          </div>
        )}
        {rootNodes ? (
          <div className="px-3 jstree jstree-1 jstree-proton" role="tree">
            {renderNodeChildren(rootNodes, 0)}
          </div>
        ) : null}
        {loading ? <LoadingOverlay message="Loading children..." /> : null}
      </div>
    </Fragment>
  );
}
