import { asArray, randomString } from "../app/util";
import LinkedEntities from "../model/LinkedEntities";
import EntityLink from "./EntityLink";

export default function ClassExpression({
	ontologyId,
  expr,
  entityType,
  linkedEntities,
}: {
	ontologyId:string,
  expr: any;
  entityType?:'classes'|'properties'|'individuals',
  linkedEntities:LinkedEntities;
}) {
	entityType = entityType || 'classes'

  if (typeof expr !== "object") {
    // expr is just an IRI
    return <EntityLink ontologyId={ontologyId} entityType={entityType} iri={expr} linkedEntities={linkedEntities} />
  }

  linkedEntities = linkedEntities.mergeWith(expr.linkedEntities)

  ///
  /// 1. owl:Class expressions
  ///
  const intersectionOf = asArray(
    expr["http://www.w3.org/2002/07/owl#intersectionOf"]
  );
  if (intersectionOf.length > 0) {
    let nodes: JSX.Element[] = [
      <span key={randomString()} className="text-neutral-default">
        &#40;
      </span>,
    ];

    for (const subExpr of intersectionOf) {
      if (nodes.length > 1) {
        nodes.push(
          <span
            key={randomString()}
            className="px-1 text-neutral-default italic"
          >
            and
          </span>
        );
      }
      nodes.push(
        <ClassExpression
          key={randomString()}
	  ontologyId={ontologyId}
	  entityType={'classes'}
          expr={subExpr}
          linkedEntities={linkedEntities}
        />
      );
    }

    nodes.push(
      <span key={randomString()} className="text-neutral-default">
        &#41;
      </span>
    );

    return <span>{nodes}</span>;
  }

  const unionOf = asArray(expr["http://www.w3.org/2002/07/owl#unionOf"]);
  if (unionOf.length > 0) {
    let nodes: JSX.Element[] = [
      <span key={randomString()} className="text-neutral-default">
        &#40;
      </span>,
    ];

    for (const subExpr of unionOf) {
      if (nodes.length > 1) {
        nodes.push(
          <span
            key={randomString()}
            className="px-1 text-neutral-default italic"
          >
            or
          </span>
        );
      }
      nodes.push(
        <ClassExpression
          key={randomString()}
	  ontologyId={ontologyId}
	  entityType={'classes'}
          expr={subExpr}
          linkedEntities={linkedEntities}
        />
      );
    }

    nodes.push(
      <span key={randomString()} className="text-neutral-default">
        &#41;
      </span>
    );

    return <span>{nodes}</span>;
  }

  const complementOf = asArray(
    expr["http://www.w3.org/2002/07/owl#complementOf"]
  )[0];
  if (complementOf) {
    return (
      <span>
        <span className="pr-1 text-neutral-default italic">not</span>
        <ClassExpression
	  ontologyId={ontologyId}
	  entityType={'classes'}
	  expr={complementOf}
	  linkedEntities={linkedEntities} />
      </span>
    );
  }

  const oneOf = asArray(expr["http://www.w3.org/2002/07/owl#oneOf"]);
  if (oneOf.length > 0) {
    let nodes: JSX.Element[] = [
      <span key={randomString()} className="text-neutral-default">
        &#123;
      </span>,
    ];

    for (const subExpr of oneOf) {
      if (nodes.length > 1) {
        nodes.push(
          <span key={randomString()} className="text-neutral-default">
            &#44;&nbsp;
          </span>
        );
      }
      nodes.push(
        <ClassExpression
          key={randomString()}
	  ontologyId={ontologyId}
	  entityType={'individuals'}
          expr={subExpr}
          linkedEntities={linkedEntities}
        />
      );
    }

    nodes.push(
      <span key={randomString()} className="text-neutral-default">
        &#125;
      </span>
    );

    return <span>{nodes}</span>;
  }

  ///
  /// 2. owl:Restriction expressions
  ///
  const onProperty = expr["http://www.w3.org/2002/07/owl#onProperty"];
  // let onProperties = expr['http://www.w3.org/2002/07/owl#onProperties'])

  if (!onProperty) {
    return (
      <span className="text-embl-red-default italic">
        unknown class expression
      </span>
    );
  }

  const someValuesFrom = asArray(
    expr["http://www.w3.org/2002/07/owl#someValuesFrom"]
  )[0];
  if (someValuesFrom) {
    return (
      <span>
        <ClassExpression ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">some</span>
        <ClassExpression ontologyId={ontologyId} entityType={'classes'} expr={someValuesFrom} linkedEntities={linkedEntities} />
      </span>
    );
  }

  const allValuesFrom = asArray(
    expr["http://www.w3.org/2002/07/owl#allValuesFrom"]
  )[0];
  if (allValuesFrom) {
    return (
      <span>
        <ClassExpression ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">only</span>
        <ClassExpression ontologyId={ontologyId} entityType={'classes'} expr={allValuesFrom} linkedEntities={linkedEntities} />
      </span>
    );
  }

  const hasValue = asArray(expr["http://www.w3.org/2002/07/owl#hasValue"])[0];
  if (hasValue) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">value</span>
        <ClassExpression  ontologyId={ontologyId} entityType={'individuals'} expr={hasValue} linkedEntities={linkedEntities} />
      </span>
    );
  }

  const minCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#minCardinality"]
  )[0];
  if (minCardinality) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">min</span>
        <ClassExpression  ontologyId={ontologyId} entityType={'classes'} expr={minCardinality} linkedEntities={linkedEntities} />
      </span>
    );
  }

  let maxCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#maxCardinality"]
  )[0];
  if (maxCardinality) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">max</span>
        <ClassExpression  ontologyId={ontologyId} entityType={'classes'} expr={maxCardinality} linkedEntities={linkedEntities} />
      </span>
    );
  }

  let minQualifiedCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#minQualifiedCardinality"]
  )[0];
  if (minQualifiedCardinality) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">min</span>
        <ClassExpression
	 ontologyId={ontologyId} entityType={'classes'}
          expr={minQualifiedCardinality}
          linkedEntities={linkedEntities}
        />
      </span>
    );
  }

  let maxQualifiedCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#maxQualifiedCardinality"]
  )[0];
  if (maxQualifiedCardinality) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">max</span>
        <ClassExpression
	 ontologyId={ontologyId} entityType={'classes'}
          expr={maxQualifiedCardinality}
          linkedEntities={linkedEntities}
        />
      </span>
    );
  }

  let exactCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#exactCardinality"]
  )[0];
  if (exactCardinality) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">exactly</span>
        <ClassExpression  ontologyId={ontologyId} entityType={'classes'} expr={exactCardinality} linkedEntities={linkedEntities} />
      </span>
    );
  }

  let exactQualifiedCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#exactQualifiedCardinality"]
  )[0];
  if (exactQualifiedCardinality) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">exactly</span>
        <ClassExpression
	 ontologyId={ontologyId} entityType={'classes'}
          expr={exactQualifiedCardinality}
          linkedEntities={linkedEntities}
        />
      </span>
    );
  }

  let hasSelf = asArray(expr["http://www.w3.org/2002/07/owl#hasSelf"])[0];
  if (hasSelf) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">Self</span>
      </span>
    );
  }

  return (
    <span className="text-embl-red-default italic">
      unknown class expression
    </span>
  );
}
