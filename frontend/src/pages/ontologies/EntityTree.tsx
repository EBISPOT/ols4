import {
	Checkbox,
  FormControl,
  FormControlLabel,
  Radio,
  RadioGroup
} from "@mui/material";
import { Fragment, useCallback, useEffect } from "react";
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
  showObsolete,
  hideObsolete,
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

	console.log('Rendering tree')

  const dispatch = useAppDispatch();
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

  const showObsoleteEnabled = useAppSelector((state => state.ontologies.showObsolete));

  const toggleNode = (node: any) => {
    if (expandedNodes.indexOf(node.absoluteIdentity) !== -1) {
      dispatch(closeNode(node));
    } else {
      dispatch(openNode(node));
    }
  };

  useEffect(() => {
	console.log('!!!! Dispatching resetTree')
    dispatch(resetTree());
  }, [dispatch, ontology.getOntologyId(), entityType, selectedEntity?.getIri(), showObsoleteEnabled]);

  useEffect(() => {

	console.log('!!!! Dispatching API call')

    if (selectedEntity) {
      const entityIri = selectedEntity.getIri();
      dispatch(
        getAncestors({
          ontologyId: ontology.getOntologyId(),
          entityType,
          entityIri,
          lang,
	  showObsoleteEnabled
        })
      );
    } else {
      dispatch(
        getRootEntities({
          ontologyId: ontology.getOntologyId(),
          entityType,
          preferredRoots,
          lang,
	  showObsoleteEnabled
        })
      );
    }

  }, [dispatch, entityType, selectedEntity?.getIri(), ontology.getOntologyId(), preferredRoots, lang, showObsoleteEnabled]);

  useEffect(() => {

	let nodesMissingChildren:string[] = expandedNodes.filter(absoluteIdentity => !nodeChildren[absoluteIdentity]?.length)

	// console.log('!!!! Getting missing node children: ' + JSON.stringify(nodesMissingChildren));

	for(let absId of nodesMissingChildren) {
		dispatch(
			getNodeChildren({
				ontologyId: ontology.getOntologyId(),
				entityTypePlural: entityType,
				entityIri: absId.split(';').pop(),
				absoluteIdentity: absId,
				lang,
				showObsolete
			})
		)
	}

  }, [dispatch, expandedNodes, nodeChildren]);

  let toggleShowObsolete = useCallback(() => {

	if(showObsoleteEnabled) 
		dispatch(hideObsolete())
	else
		dispatch(showObsolete())

  }, [ showObsoleteEnabled ]);

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
		<TreeLink ontology={ontology} entity={childNode.entity} title={childNode.title} />
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
          <div style={{ position: "absolute", right: 0, top: 0 }}>

		 <FormControlLabel control={<Checkbox value={showObsoleteEnabled} onClick={toggleShowObsolete} />} label="Show obsolete terms" />

		{ontology.getPreferredRoots().length > 0 && (
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
		)}
          </div>
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

function TreeLink({ontology,entity,title}:{ontology:Ontology,entity:Entity,title:string}) {

          let encodedIri = encodeURIComponent(encodeURIComponent(entity.getIri()));

	  let definedBy:string[] = entity.getDefinedBy()

	  if(definedBy.indexOf(ontology.getOntologyId()) !== -1)
		definedBy = []; // don't show definedBy links for terms in current ontology

	return <Link
                to={`/ontologies/${ontology.getOntologyId()}/${entity.getTypePlural()}/${encodedIri}`}
              >
                {title}
		{
			definedBy.length > 0 &&
				definedBy.map(definingOntology => {
				return <Link to={`/ontologies/${definingOntology}/${entity.getTypePlural()}/${encodedIri}`}>
					<span className="mx-1 link-ontology px-2 py-0 rounded-lg text-sm text-white uppercase ml-1" title={definingOntology} >
					{definingOntology}
					</span>
				</Link>

				})
		}
              </Link>

}