import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import DataTable, { Column } from "../../components/DataTable";
import Header from "../../components/Header";
import LoadingOverlay from "../../components/LoadingOverlay";
import Ontology from "../../model/Ontology";
import { getOntologies } from "./ontologiesSlice";

const columns: readonly Column[] = [
  {
    name: "Ontology",
    sortable: true,
    selector: (ontology: Ontology) => {
      const name = ontology.getName();
      const logo = ontology.getLogoURL();
      const ontoId = ontology.getOntologyId();
      if (name || logo) {
        return (
          <div>
            {logo ? (
              <img
                alt={`${ontoId.toUpperCase()} logo`}
                title={`${ontoId.toUpperCase()} logo`}
                className="h-16 object-contain bg-white rounded-md p-1 mb-3"
                src={logo}
              />
            ) : null}
            {name ? <div>{name}</div> : null}
          </div>
        );
      } else return ontoId;
    },
  },
  {
    name: "ID",
    sortable: true,
    selector: (ontology: Ontology) => {
      return (
        <div className="bg-petrol-default text-white rounded-md px-2 py-1 w-fit font-bold break-keep">
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
  {
    name: "Actions",
    sortable: false,
    selector: (ontology: Ontology) => {
      return (
        <div>
          <a
            href={process.env.PUBLIC_URL + `/ontologies/${ontology.getOntologyId()}`}
            className="link-default"
          >
            Search
          </a>
          <br />
          <a
            href={process.env.PUBLIC_URL + `/ontologies/${ontology.getOntologyId()}/classes`}
            className="link-default"
          >
            Classes
          </a>
          <br />
          <a
            href={process.env.PUBLIC_URL + `/ontologies/${ontology.getOntologyId()}/properties`}
            className="link-default"
          >
            Properties
          </a>
          <br />
          <a
            href={process.env.PUBLIC_URL + `/ontologies/${ontology.getOntologyId()}/individuals`}
            className="link-default"
          >
            Individuals
          </a>
        </div>
      );
    },
  },
];

export default function OntologiesPage() {
  const dispatch = useAppDispatch();
  const ontologies = useAppSelector((state) => state.ontologies.ontologies);
  const totalOntologies = useAppSelector(
    (state) => state.ontologies.totalOntologies
  );
  const loading = useAppSelector((state) => state.ontologies.loadingOntologies);

  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(10);
  const [search, setSearch] = useState<string>("");

  useEffect(() => {
    dispatch(getOntologies({ page, rowsPerPage, search }));
  }, [dispatch, page, rowsPerPage, search]);

  const navigate = useNavigate();
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto my-8">
        <DataTable
          columns={columns}
          data={ontologies}
          dataCount={totalOntologies}
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
          onSelectRow={(row: Ontology) => {
            navigate("/ontologies/" + row.getOntologyId());
          }}
          onFilter={(key: string) => {
            setSearch((prev) => {
              if (key !== prev) setPage(0);
              return key;
            });
          }}
        />
        {loading ? <LoadingOverlay message="Loading ontologies..." /> : null}
      </main>
    </div>
  );
}
