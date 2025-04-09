
import { Fragment, useEffect, useState } from "react";
import Entity from "../../../../model/Entity";
import { Typography } from "@mui/material";
import { getPaginated } from "../../../../app/api";
import LoadingOverlay from "../../../../components/LoadingOverlay";
import Class from "../../../../model/Class";
import EntityLink from "../../../../components/EntityLink";
import LinkedEntities from "../../../../model/LinkedEntities";

type SimilarResult = { entity:Entity, score:number }

export default function SimilarEntitiesSection({entity}:{entity:Entity}) {

    let [similar, setSimilar] = useState<any[]>([]);

    useEffect(() => {
        const fetchSimilarEntities = async () => {
            let page = await getPaginated<any>(`/api/ontologies/${entity.getOntologyId()}/entities/${entity.getId()}/similar`, {
            })
            setSimilar(page.elements.map((s) => ({entity:new Class(s.entity), score:s.score})))
        };

        if(entity.getType() === 'class') {
            fetchSimilarEntities();
        }
    });

    if(entity.getType() === 'class') {
        return <Fragment/>
    }

    return <div>
        <Typography variant="h5" component="h2" className="font-bold">
            Similar {entity.getTypePlural()}
        </Typography>
        { !similar && <LoadingOverlay /> }
        { similar && similar.length === 0 && <p>No similar {entity.getTypePlural()}</p> }
        { similar && similar.length > 0 && <ul className="list-disc list-inside">
            {similar.map((s:SimilarResult) => {
                let otherEntity = s.entity
                let tempLinkedEntities = new LinkedEntities([s.entity])
                return (
                    <li>
                        <EntityLink ontologyId={otherEntity.getOntologyId()} currentEntity={entity} entityType={otherEntity.getTypePlural()} iri={otherEntity.getIri()} linkedEntities={tempLinkedEntities} />
                    </li>
                )
            })}
        </ul>}
    </div>


}
