import { useEffect, useState } from "react";
import { useHistory } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import LoadingOverlay from "../../components/LoadingOverlay";
import OlsDatatable, { Column } from "../../components/OlsDatatable";
import Ontology from "../../model/Ontology";
import { getOntologies } from "./ontologiesSlice";

const columns: readonly Column[] = [
  {
    name: "Ontology",
    sortable: true,
    selector: (ontology: Ontology) => {
      return (
        <div>
          {ontology?.getLogoURL() ? (
            <img
              className="h-16 object-contain mb-3"
              src={ontology.getLogoURL()}
            />
          ) : null}
          <div>{ontology.getName()}</div>
        </div>
      );
    },
  },
  {
    name: "ID",
    sortable: true,
    selector: (ontology: Ontology) => {
      return (
        <div className="bg-petrol-default text-white rounded-md px-2 py-1 w-fit font-bold">
          {ontology.getOntologyId().toUpperCase()}
        </div>
      );
    },
  },
  {
    name: "Description",
    sortable: true,
    selector: (ontology: Ontology) => ontology.getDescription(),
  },
];

export default function OntologyList() {
  const dispatch = useAppDispatch();
  const ontologies = useAppSelector((state) => state.ontologies.ontologies);
  const loading = useAppSelector((state) => state.ontologies.loadingOntologies);

  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(10);
  const [filter, setFilter] = useState<string>("");

  useEffect(() => {
    dispatch(getOntologies({ page, rowsPerPage, filter }));
  }, [dispatch, page, rowsPerPage, filter]);

  const history = useHistory();
  return (
    <div>
      <OlsDatatable
        columns={columns}
        data={ontologies}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={(page: number) => {
          setPage(page);
        }}
        onRowsPerPageChange={(rows: number) => {
          setRowsPerPage(rows);
        }}
        onSelectRow={(row: Ontology) => {
          history.push("/ontologies/" + row.getOntologyId());
        }}
        onFilter={(key: string) => {
          setFilter(key);
        }}
      />
      {loading ? <LoadingOverlay message="Loading ontologies..." /> : null}
    </div>
  );
}
