
import { Fragment, useEffect, useState } from "react";
import Entity from "../../../../model/Entity";
import { Typography } from "@mui/material";
import { getPaginated } from "../../../../app/api";
import LoadingOverlay from "../../../../components/LoadingOverlay";
import Class from "../../../../model/Class";
import EntityLink from "../../../../components/EntityLink";
import LinkedEntities from "../../../../model/LinkedEntities";
import { Link, useSearchParams } from "react-router-dom";
import { Warning } from "@mui/icons-material";

type SimilarResult = { entity:Entity, score:number }

export default function SimilarEntitiesSection({entity}:{entity:Entity}) {

    let [similar, setSimilar] = useState<any[]|null>(null);

    const [searchParams] = useSearchParams();
    let lang = searchParams.get("lang") || "en";

    useEffect(() => {
        setSimilar(null)
        const fetchSimilarEntities = async () => {
            let page = await getPaginated<any>(`api/v2/ontologies/${entity.getOntologyId()}/${entity.getTypePlural()}/${encodeURIComponent(encodeURIComponent(entity.getIri()))}/llm_similar`)
            setSimilar(page.elements.map((s) => new Class(s)))
        };

        if(entity && entity.getOntologyId() && (entity.getType() === 'class' || entity.getType() === 'property')) {
            fetchSimilarEntities();
        }

    }, [entity?.getIri()])

    if(!entity || (entity.getType() !== 'class' && entity.getType() !== 'property')) {
        return <Fragment/>
    }

    return <div>
        { !similar && <i>Loading...</i> }
        { similar && similar.length === 0 && <p>No similar {entity.getTypePlural()} found</p> }
        { similar && similar.length > 0 && <Fragment><ul className="list-disc list-inside">
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
        </ul>
        <p className="text-xs text-gray-500 pt-2">
            <i className="icon icon-common icon-exclamation-triangle icon-spacer" />
              Similarity results are derived from LLM embeddings and have not been manually curated. Model: <Link className="link-default" to="https://platform.openai.com/docs/models/text-embedding-3-small"><code>text-embedding-3-small</code></Link>
        </p></Fragment>
        }
    </div>


}
