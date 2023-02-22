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

  let otherDefinedBy = linkedEntity?.definedBy ? linkedEntity.definedBy.filter(db => db !== ontologyId) : []

	if(otherDefinedBy.length === 1) {
		// Canonical definition in 1 other ontology
		if(linkedEntity.hasLocalDefinition) {
			// Term is defined in this ontology but has a definition 1 canonical ontology
			// Show <label> <ontologyId> where <label> links to the term in THIS ontology
			// and <ontologyId> links to the term in the DEFINING ontology
			return <Fragment><Link className="link-default" to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}`}>
			{label}
			</Link>
			<Link to={`/ontologies/${linkedEntity.definedBy[0]}/${pluraliseType(linkedEntity.type) || entityType}/${encodedIri}`}>
			<span className="bg-link-default px-2 py-0 rounded-lg text-sm text-white uppercase ml-1" title={ontologyId} >
			{linkedEntity.definedBy[0]}
			</span></Link></Fragment>
		} else {
			// Term is not defined in this ontology
			// Show <label> <ontologyId> linking to the term in the DEFINING ontology
			return <Fragment><Link className="link-default" to={`/ontologies/${linkedEntity.definedBy[0]}/${pluraliseType(linkedEntity.type) || entityType}/${encodedIri}`}>
			{label}
			<span className="bg-link-default px-2 py-0 rounded-lg text-sm text-white uppercase ml-1" title={ontologyId} >
			{linkedEntity.definedBy[0]}
			</span>
			</Link>
			</Fragment>
		}
	} else if(otherDefinedBy.length > 1) {
		// Canonical definition in multiple ontologies
		if(linkedEntity.hasLocalDefinition) {
			// Term is defined in this ontology but also more than 1 canonical definition
			// Show <label><ICON> where label links to the term in THIS ontology and <ICON> links to a disambiguation page
			return <Fragment><Link className="link-default" to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}`}>
			{label}
			</Link>
			<Link className="link-default" to={`/search?iri=${encodedIri}&isDefiningOntology=true`}>
			<span className="bg-link-default px-2 py-0 rounded-lg text-sm text-white uppercase ml-1">ICON</span></Link>
			</Fragment>
		} else {
			// Term is not defined in this ontology but is defined in other ontologies
			// Show <label><ICON> linking to a disambiguation page
			return <Link className="link-default" to={`/search?iri=${encodedIri}`}>
				{label}
			<span className="bg-link-default px-2 py-0 rounded-lg text-sm text-white uppercase ml-1">ICON</span>
			</Link>
		}
	} else {
		// No canonical definition in other ontologies
		if(linkedEntity.hasLocalDefinition) {
			// Term is defined in this ontology
			// Show internal link within the ontology
			return <Link className="link-default" to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}`}>
			{label}
			</Link>
		} else {
			// Term is not defined in this ontology

			if(linkedEntity.numDefinedIn > 0) {
				// Term is defined in other ontologies
				// Show <label><ICON> linking to disambiguation page
				return <Link className="link-default" to={`/search?iri=${encodedIri}&isDefiningOntology=true`}>{label}
				<span className="bg-link-default px-2 py-0 rounded-lg text-sm text-white uppercase ml-1">ICON</span>
				</Link>
			} else {
				// Term is not defined in other ontologies
				// Show the raw IRI
				return <Link className="link-default" to={linkedEntity.url || iri}>
					{label}
				</Link>
			}
		}
	}

}


function pluraliseType(type) {
	return ({
		'class': 'classes',
		'individual': 'individuals',
		'property': 'properties',
		'ontology': 'ontologies',
	})[type]
}