import { Fragment } from "react";
import { Link } from "react-router-dom";
import { randomString } from "../../../../app/util";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Image3D from "../../../../components/Image3D";

export default function addLinksToText(
  text: string,
  linkedEntities: LinkedEntities,
  ontologyId: string,
  currentEntity: Entity | undefined,
  entityType: "ontologies" | "classes" | "properties" | "individuals"
) {
  let linksToSplice: Array<{ start: number; end: number; link: JSX.Element }> =
    [];

  for (let entityId of Object.keys(linkedEntities.linkedEntities)) {
    for (
      let n = text.indexOf(entityId, 0);
      n !== -1;
      n = text.indexOf(entityId, n)
    ) {
      linksToSplice.push({
        start: n,
        end: n + entityId.length,
        link: (
          <EntityLink
            key={ontologyId + entityId}
            ontologyId={ontologyId}
            currentEntity={currentEntity}
            entityType={entityType}
            iri={entityId}
            linkedEntities={linkedEntities}
          />
        ),
      });

      n += entityId.length;
    }
  }

  const urlRe = /[A-z]+:\/\/[^\s]+/g;
  for (let match = urlRe.exec(text); match; match = urlRe.exec(text)) {
    const url = match[0];
    // console.log("found match " + url);
    linksToSplice.push({
      start: match.index,
      end: match.index + url.length,
      link: (
        <span>
            <Link
              key={url + randomString()}
              to={url}
              className="link-default pr-1"
              target="_blank"
              rel="noopener noreferrer"
            >
              {url}
            </Link>
        </span>
      ),
    });
  }

  removeOverlapping: for (let n = 0; n < linksToSplice.length; ) {
    for (let n2 = 0; n2 < linksToSplice.length; ++n2) {
      let spliceA = linksToSplice[n];
      let spliceB = linksToSplice[n2];

      if (spliceA === spliceB) continue;

      // The splices overlap if neither ends before the other starts
      if (spliceA.end >= spliceB.start && spliceB.end >= spliceA.start) {
        // console.log("Removing overlapping");
        linksToSplice.splice(n2, 1);
        continue removeOverlapping;
      }
    }
    ++n;
  }

  if (linksToSplice.length === 0) return text;

  // linksToSplice.sort((a, b) => a.start - b.start);
  // console.dir(linksToSplice);
  let res: JSX.Element[] = [];
  let n = 0;

  for (let link of linksToSplice) {
    res.push(
      <Fragment key={text.substring(n, link.start) + randomString()}>
        {text.substring(n, link.start)}
      </Fragment>
    );
    res.push(link.link);
    n = link.end;
  }
  res.push(
    <Fragment key={text.slice(n) + randomString()}>{text.slice(n)}</Fragment>
  );

  return res;
}
