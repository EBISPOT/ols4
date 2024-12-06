import { Fragment } from "react";
import { Link } from "react-router-dom";
import { randomString } from "../../../../app/util";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function addLinksToText(
  text: string,
  linkedEntities: LinkedEntities,
  ontologyId: string,
  currentEntity: Entity | undefined,
  entityType: "ontologies" | "classes" | "properties" | "individuals"
) {

  let linksToSplice: Array<{ start: number; end: number; link: JSX.Element }> = [];
  let urlMatches: Array<{ start: number; end: number }> = []; // To store the ranges of URLs

  // First, find all URLs and record their ranges
  const urlRe = /[A-z]+:\/\/[^\s]+/g;
  for (let match = urlRe.exec(text); match; match = urlRe.exec(text)) {
    const url = match[0];
    linksToSplice.push({
      start: match.index,
      end: match.index + url.length,
      link: (
          <span key={url + randomString()}>
          <Link
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
    urlMatches.push({ start: match.index, end: match.index + url.length });
  }

  // Then, process entity IDs
  for (let entityId of Object.keys(linkedEntities.linkedEntities)) {
    for (
      let n = text.indexOf(entityId, 0);
      n !== -1;
      n = text.indexOf(entityId, n)
    ) {
      // We need to handle this case when entity ID is part of a URL and it then gets linked to an entity but
      // resulting url is broken. So, we need to keep the URL as is if the entity ID is part of a URL.
      // Check if the entity ID is within any URL range
      let isWithinURL = urlMatches.some(
          (urlRange) =>
              n >= urlRange.start && n + entityId.length <= urlRange.end
      );
      if (isWithinURL) {
        // Skip this entity ID because it's part of a URL
        n += entityId.length;
        continue;
      }

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

  // Remove overlapping links by sorting and keeping the first one
  linksToSplice.sort((a, b) => a.start - b.start);

  // Remove overlaps
  for (let i = 0; i < linksToSplice.length - 1; i++) {
    const current = linksToSplice[i];
    const next = linksToSplice[i + 1];
    if (current.end > next.start) {
      // Overlap detected, remove the next link
      linksToSplice.splice(i + 1, 1);
      i--; // Adjust index after removal
    }
  }

  if (linksToSplice.length === 0) return text;

  // Build the final result
  let res: JSX.Element[] = [];
  let lastIndex = 0;

  for (let link of linksToSplice) {
    if (lastIndex < link.start) {
      res.push(
          <Fragment key={randomString()}>
            {text.substring(lastIndex, link.start)}
          </Fragment>
      );
    }
    res.push(link.link);
    lastIndex = link.end;
  }

  if (lastIndex < text.length) {
    res.push(
        <Fragment key={text.substring(lastIndex) + randomString()}>
          {text.substring(lastIndex)}
        </Fragment>
    );
  }

  return res;
}
