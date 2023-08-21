import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../../app/hooks";
import DataTable, { Column } from "../../../components/DataTable";
import Entity from "../../../model/Entity";
import { getEntities } from "../ontologiesSlice";

export default function EntityList({
  ontologyId,
  entityType,
}: {
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

  useEffect(() => {
    dispatch(
      getEntities({ ontologyId, entityType, page, rowsPerPage, search })
    );
  }, [dispatch, ontologyId, entityType, page, rowsPerPage, search]);

  useEffect(() => {
    setPage(0);
  }, [entityType]);

  const navigate = useNavigate();
  return (
    <div className="mt-2">
      <DataTable
        columns={columns}
        data={entities}
        dataCount={totalEntities}
        placeholder={`Search ${entityType}...`}
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
          const termUrl = encodeURIComponent(
            encodeURIComponent(row.properties.iri)
          );
          navigate(
            `/ontologies/${ontologyId}/${row.getTypePlural()}/${termUrl}`
          );
        }}
        onFilter={(key: string) => {
          setSearch((prev) => {
            if (key !== prev) setPage(0);
            return key;
          });
        }}
      />
    </div>
  );
}

const columns: readonly Column[] = [
  {
    name: "Name",
    sortable: true,
    selector: (entity: Entity) => entity.getName(),
  },
  {
    name: "ID",
    sortable: true,
    selector: (entity: Entity) => entity.getShortForm(),
  },
];
