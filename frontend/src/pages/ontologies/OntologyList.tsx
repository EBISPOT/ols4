import { useEffect, useState } from "react";
import { useHistory } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import LoadingOverlay from "../../components/LoadingOverlay";
import OlsDatatable, { Column } from "../../components/OlsDatatable";
import Ontology from "../../model/Ontology";
import { getOntologies } from "./ontologiesSlice";

const columns: readonly Column[] = [
  // {
  //     name: 'Debug',
  //     sortable: true,
  //     selector: (ontology:Ontology) => JSON.stringify(ontology),
  //     wrap: true
  // },
  // {
  //   name: "",
  //   sortable: false,
  //   selector: (ontology: Ontology) =>
  //     ontology.getLogoURL() && <img width={50} src={ontology.getLogoURL()} />,
  // },
  {
    name: "Name",
    sortable: true,
    selector: (ontology: Ontology) => ontology.getName(),
  },
  {
    name: "ID",
    sortable: true,
    selector: (ontology: Ontology) => ontology.getOntologyId().toUpperCase(),
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
  }, [page, rowsPerPage, filter]);

  let history = useHistory();

  return (
    <div>
      {loading ? <LoadingOverlay /> : null}
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
    </div>
  );
}
