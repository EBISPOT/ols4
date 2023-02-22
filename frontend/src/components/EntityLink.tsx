import { Fragment } from "react";
import { Link } from "react-router-dom";
import LinkedEntities from "../model/LinkedEntities";

export default function EntityLink({
  ontologyId,
  entityType,
  iri,
  linkedEntities,
}: {
  ontologyId: string;
  entityType: "classes" | "properties" | "individuals";
  iri: string;
  linkedEntities: LinkedEntities;
}) {
  const encodedIri = encodeURIComponent(encodeURIComponent(iri));
  const label = linkedEntities.getLabelForIri(iri) || iri.split("/").pop();
  const linkedEntity = linkedEntities.get(iri);

  if(linkedEntity) {
	if(linkedEntity.definedBy && linkedEntity.definedBy.length === 1) {
		if(linkedEntity.definedBy[0] === ontologyId) {
			return (
				<Link className="link-default" to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}`}>
					{label}
				</Link>
			);
		} else {
			if(linkedEntity.definedIn && linkedEntity.definedIn.indexOf(ontologyId) !== -1) {
				// defined by another ontology but imported into this ontology
				return <Fragment><Link className="link-default" to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}`}>
				{label}
				</Link>
				<Link to={`/ontologies/${linkedEntity.definedBy[0]}/${pluraliseType(linkedEntity.type) || entityType}/${encodedIri}`}>
				<span
				className="bg-link-default px-2 py-0 rounded-lg text-sm text-white uppercase ml-1"
				title={ontologyId}
				>
				{linkedEntity.definedBy[0]}
				</span></Link></Fragment>
			} else {
				// defined by another ontology and not imported into this ontology
				return <Fragment><Link className="link-default" to={`/ontologies/${linkedEntity.definedBy[0]}/${pluraliseType(linkedEntity.type) || entityType}/${encodedIri}`}>
				{label}
				<span
				className="bg-link-default px-2 py-0 rounded-lg text-sm text-white uppercase ml-1"
				title={ontologyId}
				>
				{linkedEntity.definedBy[0]}
				</span>
				</Link>
			</Fragment>
			}
		}
	} else if(linkedEntity.url) {
		return (
			<Link className="link-default" to={linkedEntity.url}>
				{label}
			</Link>
		);
	} else {
		<Link className="link-default" to={iri}>
			{label}
		</Link>
	}
  }

return (
	<Link className="link-default" to={iri}>
		{label}
	</Link>
);
}


function pluraliseType(type) {
	return ({
		'class': 'classes',
		'individual': 'individuals',
		'property': 'properties',
		'ontology': 'ontologies',
	})[type]
}