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
  const encodedIri = encodeURIComponent(iri);
  const label = linkedEntities.getLabelForIri(iri) || iri.split("/").pop();

  return (
    <Link className="link-default" to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}`}>
      {label}
    </Link>
  );
}
