import { asArray, randomString } from "../app/util";

export default function ClassExpression({
  expr,
  iriToLabels,
}: {
  expr: any;
  iriToLabels: any;
}) {
  if (typeof expr !== "object") {
    // expr is just an IRI
    const label = expr;
    return (
      <a href={expr} className="link-default">
        {label ? label : expr.substring(expr.lastIndexOf("/") + 1)}
      </a>
    );
  }

  iriToLabels = { ...iriToLabels, ...expr.iriToLabels };

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
          expr={subExpr}
          iriToLabels={iriToLabels}
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
          expr={subExpr}
          iriToLabels={iriToLabels}
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
        <ClassExpression expr={complementOf} iriToLabels={iriToLabels} />
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
          expr={subExpr}
          iriToLabels={iriToLabels}
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
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
        <span className="px-1 text-embl-purple-default italic">some</span>
        <ClassExpression expr={someValuesFrom} iriToLabels={iriToLabels} />
      </span>
    );
  }

  const allValuesFrom = asArray(
    expr["http://www.w3.org/2002/07/owl#allValuesFrom"]
  )[0];
  if (allValuesFrom) {
    return (
      <span>
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
        <span className="px-1 text-embl-purple-default italic">only</span>
        <ClassExpression expr={allValuesFrom} iriToLabels={iriToLabels} />
      </span>
    );
  }

  const hasValue = asArray(expr["http://www.w3.org/2002/07/owl#hasValue"])[0];
  if (hasValue) {
    return (
      <span>
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
        <span className="px-1 text-embl-purple-default italic">value</span>
        <ClassExpression expr={hasValue} iriToLabels={iriToLabels} />
      </span>
    );
  }

  const minCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#minCardinality"]
  )[0];
  if (minCardinality) {
    return (
      <span>
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
        <span className="px-1 text-embl-purple-default italic">min</span>
        <ClassExpression expr={minCardinality} iriToLabels={iriToLabels} />
      </span>
    );
  }

  let maxCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#maxCardinality"]
  )[0];
  if (maxCardinality) {
    return (
      <span>
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
        <span className="px-1 text-embl-purple-default italic">max</span>
        <ClassExpression expr={maxCardinality} iriToLabels={iriToLabels} />
      </span>
    );
  }

  let minQualifiedCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#minQualifiedCardinality"]
  )[0];
  if (minQualifiedCardinality) {
    return (
      <span>
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
        <span className="px-1 text-embl-purple-default italic">min</span>
        <ClassExpression
          expr={minQualifiedCardinality}
          iriToLabels={iriToLabels}
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
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
        <span className="px-1 text-embl-purple-default italic">max</span>
        <ClassExpression
          expr={maxQualifiedCardinality}
          iriToLabels={iriToLabels}
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
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
        <span className="px-1 text-embl-purple-default italic">exactly</span>
        <ClassExpression expr={exactCardinality} iriToLabels={iriToLabels} />
      </span>
    );
  }

  let exactQualifiedCardinality = asArray(
    expr["http://www.w3.org/2002/07/owl#exactQualifiedCardinality"]
  )[0];
  if (exactQualifiedCardinality) {
    return (
      <span>
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
        <span className="px-1 text-embl-purple-default italic">exactly</span>
        <ClassExpression
          expr={exactQualifiedCardinality}
          iriToLabels={iriToLabels}
        />
      </span>
    );
  }

  let hasSelf = asArray(expr["http://www.w3.org/2002/07/owl#hasSelf"])[0];
  if (hasSelf) {
    return (
      <span>
        <ClassExpression expr={onProperty} iriToLabels={iriToLabels} />
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
