import { Fragment } from "react";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function addEntityLinksToText(text:string, linkedEntities:LinkedEntities, ontologyId:string, currentEntity:Entity, entityType:"ontologies"|"classes"|"properties"|"individuals") {

	let linksToSplice:Array<{start:number, end:number, link:JSX.Element}> = []

	for(let entityId of Object.keys(linkedEntities.linkedEntities)) {

		for(let n = text.indexOf(entityId, 0); n !== -1; n = text.indexOf(entityId, n)) {

			linksToSplice.push({
				start: n,
				end: n + entityId.length,
				link: <EntityLink
					ontologyId={ontologyId}
					currentEntity={currentEntity}
					entityType={entityType}
					iri={entityId}
					linkedEntities={linkedEntities}
				/>});

			n += entityId.length
		}
	}

	if(linksToSplice.length === 0)
		return text;

	linksToSplice.sort((a, b) => a.start - b.start);
	console.dir(linksToSplice);

	let res:JSX.Element[] = []

	let n = 0;

	for(let link of linksToSplice) {
		res.push(<Fragment>{text.substring(n, link.start)}</Fragment>);
		res.push(link.link);
		n = link.end;
	}

	res.push(<Fragment>{text.slice(n)}</Fragment>);

	return res;
}