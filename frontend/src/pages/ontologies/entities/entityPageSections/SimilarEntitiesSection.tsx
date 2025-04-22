
import { Fragment, useEffect, useState } from "react";
import Entity from "../../../../model/Entity";
import { Typography } from "@mui/material";
import { getPaginated } from "../../../../app/api";
import LoadingOverlay from "../../../../components/LoadingOverlay";
import Class from "../../../../model/Class";
import EntityLink from "../../../../components/EntityLink";
import LinkedEntities from "../../../../model/LinkedEntities";
import { Link, useSearchParams } from "react-router-dom";

type SimilarResult = { entity:Entity, score:number }

export default function SimilarEntitiesSection({entity}:{entity:Entity}) {

    let [similar, setSimilar] = useState<any[]|null>(null);

    const [searchParams] = useSearchParams();
    let lang = searchParams.get("lang") || "en";

    useEffect(() => {
        setSimilar(null)
        const fetchSimilarEntities = async () => {
            let page = await getPaginated<any>(`api/v2/ontologies/${entity.getOntologyId()}/classes/${encodeURIComponent(encodeURIComponent(entity.getIri()))}/similar`)
            setSimilar(page.elements.map((s) => new Class(s)))
        };

        if(entity && entity.getOntologyId() && entity.getType() === 'class') {
            fetchSimilarEntities();
        }

    }, [entity?.getIri()])

    if(!entity || entity.getType() !== 'class') {
        return <Fragment/>
    }

    return <div>
        { !similar && <i>Loading...</i> }
        { similar && similar.length === 0 && <p>No similar {entity.getTypePlural()}</p> }
        { similar && similar.length > 0 && <ul className="list-disc list-inside">
            {similar.filter(
                (otherEntity:Entity) => {
                    return otherEntity.getIri() !== entity.getIri()
                }
            ).map((otherEntity:Entity) => {
                return (
                <li key={entity.getId()}>
                <Link
                    className="link-default"
                    to={`/ontologies/${otherEntity.getOntologyId()}/${
                        otherEntity.getTypePlural()
                    }/${encodeURIComponent(encodeURIComponent(otherEntity.getIri()))}?lang=${lang}`}
                >
                    {otherEntity.getName()}
                    <span
                    className="link-ontology px-2 py-0.5 rounded-md text-sm text-white uppercase ml-1"
                    title={otherEntity.getOntologyId().toUpperCase()}
                    >
                    {otherEntity.getOntologyId()}
                    </span>
                </Link>
                </li>
                )
            })}
        </ul>}
    </div>


}
