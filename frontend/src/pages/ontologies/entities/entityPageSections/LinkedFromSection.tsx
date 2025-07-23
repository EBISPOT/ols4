
import { Fragment, useEffect, useState } from "react";
import { randomString } from "../../../../app/util";
import EntityLink from "../../../../components/EntityLink";
import Class from "../../../../model/Class";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";
import { Link, useSearchParams } from "react-router-dom";
import { getPaginated, Page } from "../../../../app/api";

export default function LinkedFromSection({entity, linkedEntities}:{entity:Entity, linkedEntities:LinkedEntities}) {

    let [linkedfrom, setLinkedFrom] = useState<Page<any>|null>(null);

    const [searchParams] = useSearchParams();
    let lang = searchParams.get("lang") || "en";

    useEffect(() => {
        setLinkedFrom(null)
        const fetchLinkedFromEntities = async () => {
            let page = await getPaginated<any>(`api/v2/ontologies/${entity.getOntologyId()}/entities`, { linksTo: entity.getIri(), size: '5' })
            setLinkedFrom(page)
        };
        fetchLinkedFromEntities();

    }, [entity?.getOntologyId(), entity?.getIri()])

    if(!entity) {
        return <Fragment/>
    }

    return <div>
        <div className="font-bold">Linked from</div>
        { !linkedfrom && <i>Loading...</i> }
        { linkedfrom && linkedfrom.numElements === 0 && <Fragment/> }
        { linkedfrom && linkedfrom.numElements > 0 && <Fragment> <ul className="list-disc list-inside">
            {linkedfrom.elements.map(
                (elem) => {
                    return new Entity(elem)
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

        {linkedfrom.totalElements > 5 && (
            <Link
              className="link-default italic"
              to={`/search?linksTo=${encodeURIComponent(entity.getIri())}&lang=${lang}`}
            >
                + {linkedfrom.totalElements - 5}
            </Link>
        )}
        </Fragment>
        }
    </div>

}
