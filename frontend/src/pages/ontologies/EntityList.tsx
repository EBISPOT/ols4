import { useEffect, useState } from "react";
import { useHistory } from "react-router-dom";
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
  const totalEntities = useAppSelector(
    (state) => state.ontologies.totalEntities
  );

  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(10);
  const [search, setSearch] = useState<string>("");

  let { ontologyId, entityType } = props;

  useEffect(() => {
    dispatch(getEntities({ ontologyId, entityType, page, rowsPerPage, search }));
  }, [dispatch, ontologyId, entityType, page, rowsPerPage, search]);

  const history = useHistory();

  return (
    <DataTable
      columns={columns}
      data={entities}
      dataCount={totalEntities}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={(pg: number) => {
            setPage(pg);
          }}
          onRowsPerPageChange={(rows: number) => {
            setRowsPerPage((prev) => {
              if (rows !== prev) setPage(0);
              return rows;
            });
          }}
      onSelectRow={(row) => {
	const termUrl = encodeURIComponent(encodeURIComponent(row.properties.iri));
	history.push(`/ontologies/${ontologyId}/${row.getTypePlural()}/${termUrl}`)
        console.log(JSON.stringify(row));
      }}
          onFilter={(key: string) => {
            setSearch((prev) => {
              if (key !== prev) setPage(0);
              return key;
            });
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
