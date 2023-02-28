import { Fragment } from "react";
import { Link } from "react-router-dom";
import LinkedEntities from "../model/LinkedEntities";
import SearchIcon from '@mui/icons-material/Search';

export default function EntityLink({
  ontologyId,
  entityType,
  iri,
  linkedEntities,
}: {
  ontologyId: string;
  entityType: "classes" | "properties" | "individuals" | "ontologies";
  iri: string;
  linkedEntities: LinkedEntities;
}) {

  const label = linkedEntities.getLabelForIri(iri) || iri.split("/").pop() || iri;
  const linkedEntity = linkedEntities.get(iri);

	if(!linkedEntity) {
		// So far only known occurrence of this branch is for owl:Thing
		return <Link className="link-default" to={iri}>
			{label}
		</Link>
	}

  let otherDefinedBy = linkedEntity?.definedBy ? linkedEntity.definedBy.filter(db => db !== ontologyId) : []
  const encodedIri = encodeURIComponent(encodeURIComponent(linkedEntity?.iri || iri));

	if(otherDefinedBy.length === 1) {
		// Canonical definition in 1 other ontology
		if(linkedEntity.hasLocalDefinition) {
			// Term is defined in this ontology but has a definition 1 canonical ontology
			// Show <label> <ontologyId> where <label> links to the term in THIS ontology
			// and <ontologyId> links to the term in the DEFINING ontology
			return <Fragment>
				<Link className="link-default" to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}`}>
				{label}
				</Link>
				<Link to={`/ontologies/${linkedEntity.definedBy[0]}/${pluraliseType(linkedEntity.type) || entityType}/${encodedIri}`}>
					<span className="mx-1 link-ontology px-2 py-0 rounded-lg text-sm text-white uppercase ml-1" title={ontologyId} >
					{linkedEntity.definedBy[0]}
					</span>
				</Link>
			</Fragment>
		} else {
			// Term is not defined in this ontology
			// Show <label> <ontologyId> linking to the term in the DEFINING ontology
			return <Fragment>
				<Link className="link-default" to={`/ontologies/${linkedEntity.definedBy[0]}/${pluraliseType(linkedEntity.type) || entityType}/${encodedIri}`}>
					{label}
				</Link>
				<Link to={`/ontologies/${linkedEntity.definedBy[0]}/${pluraliseType(linkedEntity.type) || entityType}/${encodedIri}`}>
					<span className="link-ontology px-2 py-0 rounded-lg text-sm text-white uppercase ml-1" title={ontologyId} >
						{linkedEntity.definedBy[0]}
					</span>
				</Link>
			</Fragment>
		}
	} else if(otherDefinedBy.length > 1) {
		// Canonical definition in multiple ontologies
		if(linkedEntity.hasLocalDefinition) {
			// Term is defined in this ontology but also more than 1 canonical definition
			// Show <label><ONTOLOGY> where each ONTOLOGY button links to the term in that defining ontology
			return <Fragment>
				<Link className="link-default" to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}`}>
				{iri}
				</Link>
				{
					linkedEntity.otherDefinedBy.map(definedBy => {
						return <Link to={`/ontologies/${definedBy}/${entityType}/${encodedIri}`}>
							<span className="link-ontology px-2 py-0 rounded-lg text-sm text-white uppercase ml-1" title={definedBy}>
							{definedBy}
							</span>
						</Link>

					})
				}
			</Fragment>
		} else {
			// Term is not defined in this ontology but is defined in other ontologies
			// Show <label><ICON> linking to a disambiguation page
			return <Fragment>
				<Link className="link-default" to={`/search?iri=${encodedIri}`}>
					{iri}
				</Link>
				<Link to={`/search?iri=${encodedIri}`}>
					<span className="link-ontology py-0 rounded-lg text-sm text-white ml-1 px-2"><SearchIcon/> {otherDefinedBy.length} ontologies</span>
				</Link>
			</Fragment>
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

			if(linkedEntity.numAppearsIn > 0) {
				// Term appears in other ontologies
				// Show <label><ICON> linking to disambiguation page
				return <Fragment>
					<Link className="link-default" to={`/search?iri=${encodedIri}&isDefiningOntology=true`}>
						{iri}
					</Link>
					<Link to={`/search?iri=${encodedIri}&isDefiningOntology=true`}>
						<span className="mx-1 link-ontology px-2 py-0 rounded-lg text-sm text-white"><SearchIcon /> {linkedEntity.numAppearsIn} ontologies</span>
					</Link>
				</Fragment>
			} else {
				// Term is not defined in other ontologies
				// Show the raw IRI
				return <Link className="link-default" to={linkedEntity.url || iri}>
					{label}
				</Link>
			}
		}
	}

	throw new Error('unknown entity link')

}


function pluraliseType(type) {
	return ({
		'class': 'classes',
		'individual': 'individuals',
		'property': 'properties',
		'ontology': 'ontologies',
	})[type]
}