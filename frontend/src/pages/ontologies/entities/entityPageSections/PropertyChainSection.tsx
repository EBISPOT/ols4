import { Fragment } from "react";
import { asArray, randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function PropertyChainSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property)) {
    return <Fragment />;
  }

  // TODO: reification discarded here
  let propertyChains: any[] = entity.getPropertyChains().map((rf) => rf.value);

  if (!propertyChains || propertyChains.length === 0) {
    return <Fragment />;
  }

  let hasMultipleChains =
    propertyChains.filter((chain) => Array.isArray(chain)).length > 0;

  return (
    <div>
      <div className="font-bold">
        {hasMultipleChains ? "Property chains" : "Property chain"}
      </div>
      {!hasMultipleChains ? (
        <p>
          <PropertyChain
            propertyChain={propertyChains}
            entity={entity}
            linkedEntities={linkedEntities}
          />
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {propertyChains.map((propertyChain) => {
            return (
              <li key={randomString()}>
                <PropertyChain
                  propertyChain={propertyChain}
                  entity={entity}
                  linkedEntities={linkedEntities}
                />
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function PropertyChain({
  propertyChain,
  entity,
  linkedEntities,
}: {
  propertyChain: any;
  entity: Entity;
  linkedEntities: any;
}) {
  let chain = asArray(propertyChain);

  return (
    <Fragment>
      {chain.reverse().map((propertyExpr, i) => {
        return (
          <span key={propertyExpr}>
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              entityType={"properties"}
              expr={propertyExpr}
              linkedEntities={linkedEntities}
            />
            <Fragment>
              {i < chain.length - 1 && (
                <span className="px-2 text-sm" style={{ color: "gray" }}>
                  ◂
                </span>
              )}
            </Fragment>
          </span>
        );
      })}
    </Fragment>
  );
}
