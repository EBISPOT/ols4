import { Fragment } from "react";
import { asArray, randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import PropertyValuesList from "../../../../components/PropertyValuesList";
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
    <PropertyValuesList
      values={propertyChains}
      title={hasMultipleChains ? "Property chains" : "Property chain"}
      renderValue={(propertyChain) => (
        <PropertyChain
          propertyChain={propertyChain}
          entity={entity}
          linkedEntities={linkedEntities}
        />
      )}
      searchFilter={(propertyChain, searchQuery) => {
        // Convert property chain to searchable string
        const chainStr = JSON.stringify(propertyChain).toLowerCase();
        return chainStr.includes(searchQuery);
      }}
    />
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
