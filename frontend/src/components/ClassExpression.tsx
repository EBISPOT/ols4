import { Fragment } from "react";
import { asArray, randomString } from "../app/util";
import Entity from "../model/Entity";
import LinkedEntities from "../model/LinkedEntities";
import EntityLink from "./EntityLink";

export default function ClassExpression({
  ontologyId,
  currentEntity,
  expr,
  entityType,
  linkedEntities,
}: {
  ontologyId:string,
  currentEntity:Entity|undefined,
  expr: any;
  entityType?:'classes'|'properties'|'individuals',
  linkedEntities:LinkedEntities;
}) {
	entityType = entityType || 'classes'

  if (typeof expr !== "object" && typeof expr !== "boolean") {
    // expr is just an IRI
    return <EntityLink ontologyId={ontologyId} currentEntity={currentEntity} entityType={entityType} iri={expr} linkedEntities={linkedEntities} />
  }

  linkedEntities = linkedEntities.mergeWith(expr.linkedEntities)

  const types = asArray(expr['type'])

  if(types && types.indexOf('datatype') !== -1) {
    // rdfs:Datatype
    let equivClass = expr['http://www.w3.org/2002/07/owl#equivalentClass'];
    if(equivClass) {
      return <Fragment>
        { expr['label'] && <span>{expr['label']} </span> }
        <ClassExpression currentEntity={currentEntity}   ontologyId={ontologyId} entityType={'properties'} expr={equivClass} linkedEntities={linkedEntities} />
        </Fragment>
    }
  }


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
	  currentEntity={currentEntity}
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
	  currentEntity={currentEntity}
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
	  currentEntity={currentEntity}
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
	  currentEntity={currentEntity}
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

  let inverseOf = expr["http://www.w3.org/2002/07/owl#inverseOf"];

  if(inverseOf) {
	return (
		<span>
		<span className="px-1 text-embl-purple-default italic">inverse</span>
		<span>
		{"("}
		<ClassExpression currentEntity={currentEntity}   ontologyId={ontologyId} entityType={'properties'} expr={inverseOf} linkedEntities={linkedEntities} />
		{")"}
		</span>
		</span>
	);
  }

  ///
  /// 2. owl:Restriction on datatype
  ///
  const onDatatype = expr["http://www.w3.org/2002/07/owl#onDatatype"];

  if(onDatatype) {

	const withRestrictions = asArray(expr["http://www.w3.org/2002/07/owl#withRestrictions"]);

	let res:JSX.Element[] = [
		<ClassExpression currentEntity={currentEntity}   ontologyId={ontologyId} entityType={'properties'} expr={onDatatype} linkedEntities={linkedEntities} />
	]

	if(withRestrictions.length > 0) {
		res.push(<Fragment>[</Fragment>);
		let isFirst = true;
		for(let restriction of withRestrictions) {
			if(isFirst)
				isFirst = false;
			else
				res.push(<Fragment>, </Fragment>);


			let minExclusive = restriction['http://www.w3.org/2001/XMLSchema#minExclusive'];

			if(minExclusive) {
				res.push(<Fragment>&gt; {minExclusive}</Fragment>);
			}

			let minInclusive = restriction['http://www.w3.org/2001/XMLSchema#minInclusive'];

			if(minInclusive) {
				res.push(<Fragment>≥ {minInclusive}</Fragment>);
			}

			let maxExclusive = restriction['http://www.w3.org/2001/XMLSchema#maxExclusive'];

			if(maxExclusive) {
				res.push(<Fragment>&lt; {maxExclusive}</Fragment>);
			}

			let maxInclusive = restriction['http://www.w3.org/2001/XMLSchema#maxInclusive'];

			if(maxInclusive) {
				res.push(<Fragment>≤ {maxInclusive}</Fragment>);
			}
			
		}
		res.push(<Fragment>]</Fragment>);
	}

	return <span children={res} />
  }



  ///
  /// 3. owl:Restriction on property
  ///
  const onProperty = expr["http://www.w3.org/2002/07/owl#onProperty"];
  // let onProperties = expr['http://www.w3.org/2002/07/owl#onProperties'])

  if (!onProperty && typeof expr !== "boolean") {
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
        <ClassExpression ontologyId={ontologyId} currentEntity={currentEntity} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">some</span>
        <ClassExpression ontologyId={ontologyId} currentEntity={currentEntity} entityType={'classes'} expr={someValuesFrom} linkedEntities={linkedEntities} />
      </span>
    );
  }

  const allValuesFrom = asArray(
    expr["http://www.w3.org/2002/07/owl#allValuesFrom"]
  )[0];
  if (allValuesFrom) {
    return (
      <span>
        <ClassExpression ontologyId={ontologyId} currentEntity={currentEntity} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">only</span>
        <ClassExpression ontologyId={ontologyId} currentEntity={currentEntity} entityType={'classes'} expr={allValuesFrom} linkedEntities={linkedEntities} />
      </span>
    );
  }

  const hasValue = asArray(expr["http://www.w3.org/2002/07/owl#hasValue"])[0];
  if (hasValue) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">value</span>
        <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'individuals'} expr={hasValue} linkedEntities={linkedEntities} />
      </span>
    );
  }

  const minCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#minCardinality"]
  )[0];
  if (minCardinality) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">min</span>
        <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'classes'} expr={minCardinality} linkedEntities={linkedEntities} />
      </span>
    );
  }

  let maxCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#maxCardinality"]
  )[0];
  if (maxCardinality) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">max</span>
        <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'classes'} expr={maxCardinality} linkedEntities={linkedEntities} />
      </span>
    );
  }
  let exactCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#cardinality"]
  )[0];
  if (exactCardinality) {
    return (
      <span>
        <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">exactly</span>
        <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'classes'} expr={exactCardinality} linkedEntities={linkedEntities} />
      </span>
    );
  }

  let hasSelf = asArray(expr["http://www.w3.org/2002/07/owl#hasSelf"])[0];
  if (hasSelf) {
    return (
      <span>
        <ClassExpression currentEntity={currentEntity}   ontologyId={ontologyId} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
        <span className="px-1 text-embl-purple-default italic">Self</span>
      </span>
    );
  }


  ///
  /// 4. owl:Restriction qualified cardinalities (property and class)
  ///
  const onClass = expr["http://www.w3.org/2002/07/owl#onClass"];

  if(onClass) {
    let minQualifiedCardinality = asArray(
      expr["http://www.w3.org/2002/07/owl#minQualifiedCardinality"]
    )[0];
    if (minQualifiedCardinality) {
      return (
        <span>
          <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
          <span className="px-1 text-embl-purple-default italic">min</span>
          <ClassExpression
    currentEntity={currentEntity} 
    ontologyId={ontologyId} entityType={'classes'}
            expr={minQualifiedCardinality}
            linkedEntities={linkedEntities}
          />
          &nbsp;
          <ClassExpression
    currentEntity={currentEntity} 
    ontologyId={ontologyId} entityType={'classes'}
            expr={onClass}
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
          <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
          <span className="px-1 text-embl-purple-default italic">max</span>
          <ClassExpression
    currentEntity={currentEntity} 
    ontologyId={ontologyId} entityType={'classes'}
            expr={maxQualifiedCardinality}
            linkedEntities={linkedEntities}
          />
          &nbsp;
          <ClassExpression
    currentEntity={currentEntity} 
    ontologyId={ontologyId} entityType={'classes'}
            expr={onClass}
            linkedEntities={linkedEntities}
          />
        </span>
      );
    }

    let exactQualifiedCardinality = asArray(
      expr["http://www.w3.org/2002/07/owl#qualifiedCardinality"]
    )[0];
    if (exactQualifiedCardinality) {
      return (
        <span>
          <ClassExpression  ontologyId={ontologyId} currentEntity={currentEntity} entityType={'properties'} expr={onProperty} linkedEntities={linkedEntities} />
          <span className="px-1 text-embl-purple-default italic">exactly</span>
          <ClassExpression
    ontologyId={ontologyId} entityType={'classes'}
    currentEntity={currentEntity} 
            expr={exactQualifiedCardinality}
            linkedEntities={linkedEntities}
          />
          &nbsp;
          <ClassExpression
    currentEntity={currentEntity} 
    ontologyId={ontologyId} entityType={'classes'}
            expr={onClass}
            linkedEntities={linkedEntities}
          />
        </span>
      );
    }
  }

    console.log("unknown class expression - fall through")
    return (
      <span className="text-embl-red-default italic">
        unknown class expression
      </span>
    );
}
