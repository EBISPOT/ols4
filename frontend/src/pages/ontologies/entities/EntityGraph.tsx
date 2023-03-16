import { useEffect } from "react";
import Entity from "../../../model/Entity";

export default function EntityGraph({
  ontologyId,
  selectedEntity,
  entityType,
}: {
  ontologyId: string;
  selectedEntity?: Entity;
  entityType: "entities" | "classes" | "properties" | "individuals";
}) {
  useEffect(() => {
    let iri = selectedEntity?.getIri();

    if (iri) {
      window["initLegacyGraphView"](
        process.env.REACT_APP_APIURL +
          `api/ontologies/${ontologyId}/terms?iri=`,
        iri
      );
    }
  }, [ontologyId, selectedEntity, entityType]);

  return <div id="ontology_vis" />;
}
