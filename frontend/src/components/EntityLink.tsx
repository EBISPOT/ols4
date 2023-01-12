
import { Link } from "@mui/material";
import ReferencedEntities from "../model/ReferencedEntities";

export default function EntityLink(
	{ ontologyId, entityType, iri, referencedEntities }:
		{ontologyId:string, entityType:'classes'|'properties'|'individuals', iri:string, referencedEntities:ReferencedEntities }) {

	let encodedIri = encodeURIComponent(iri)

	let label = referencedEntities.getLabelForIri(iri) || iri.split('/').pop()

	return <Link href={`/ontologies/${ontologyId}/${entityType}/${encodedIri}`}>{label}</Link>

}