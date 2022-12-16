import { useEffect } from "react";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import DataTable, { Column } from "../../components/DataTable";
import Entity from "../../model/Entity";
import { getEntities } from "./ontologiesSlice";

export default function EntityList(props: {
  ontologyId: string;
  entityType: "entities" | "classes" | "properties" | "individuals";
}) {
  const dispatch = useAppDispatch();
  const entities = useAppSelector((state) => state.ontologies.entities);
  // const loading = useAppSelector((state) => state.ontologies.loadingEntities);

  let { ontologyId, entityType } = props;

  useEffect(() => {
    dispatch(getEntities({ ontologyId, entityType }));
  }, [dispatch, ontologyId, entityType]);

  return (
    <DataTable
      columns={columns}
      data={entities}
      dataCount={entities.length}
      onSelectRow={(row) => {
        console.log(JSON.stringify(row));
      }}
    />
  );
}

const columns: readonly Column[] = [
  {
    name: "Name",
    sortable: true,
    selector: (entity: Entity) => entity.getName(),
  },
];
