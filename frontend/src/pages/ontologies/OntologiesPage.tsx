import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import urlJoin from "url-join";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import DataTable, { Column } from "../../components/DataTable";
import Header from "../../components/Header";
import LoadingOverlay from "../../components/LoadingOverlay";
import Ontology from "../../model/Ontology";
import { getOntologies } from "./ontologiesSlice";

export default function OntologiesPage() {
  const dispatch = useAppDispatch();
  const ontologies = useAppSelector((state) => state.ontologies.ontologies);
  const totalOntologies = useAppSelector(
    (state) => state.ontologies.totalOntologies
  );
  const loading = useAppSelector((state) => state.ontologies.loadingOntologies);

  const [searchParams, setSearchParams] = useSearchParams();
  let page = parseInt(searchParams.get("page") || "0");
  let rowsPerPage = parseInt(searchParams.get("rowsPerPage") || "10");
  let search = searchParams.get("search") || "";

  useEffect(() => {
    dispatch(getOntologies({ page, rowsPerPage, search }));
  }, [dispatch, page, rowsPerPage, search]);

  const navigate = useNavigate();
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
                  className="h-16 object-contain bg-white rounded-lg p-1 mb-3"
                  src={
                    logo.startsWith("/images")
                      ? process.env.REACT_APP_OBO_FOUNDRY_REPO_RAW + logo
                      : logo
                  }
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
          <div className="bg-link-default text-white rounded-md px-2 py-1 w-fit font-bold break-keep">
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
            <div
              onClick={() => {
                navigate(`/ontologies/${ontology.getOntologyId()}`);
              }}
              className="link-default"
            >
              Search
            </div>
            <a
              href={urlJoin(
                process.env.PUBLIC_URL!,
                `/ontologies/${ontology.getOntologyId()}?tab=classes`
              )}
              className="link-default"
            >
              Classes
            </a>
            <br />
            <a
              href={urlJoin(
                process.env.PUBLIC_URL!,
                `/ontologies/${ontology.getOntologyId()}?tab=properties`
              )}
              className="link-default"
            >
              Properties
            </a>
            <br />
            <a
              href={urlJoin(
                process.env.PUBLIC_URL!,
                `/ontologies/${ontology.getOntologyId()}?tab=individuals`
              )}
              className="link-default"
            >
              Individuals
            </a>
          </div>
        );
      },
    },
  ];

  document.title = "Ontology Lookup Service (OLS)";
  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto my-8">
        <DataTable
          columns={columns}
          data={ontologies}
          dataCount={totalOntologies}
          placeholder="Search ontologies..."
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={(page: number) => {
            setSearchParams((params) => {
              params.set("page", page.toString());
              return params;
            });
          }}
          onRowsPerPageChange={(rows: number) => {
            setSearchParams((params) => {
              if (rows !== parseInt(params.get("rowsPerPage") || "0"))
                params.delete("page");
              params.set("rowsPerPage", rows.toString());
              return params;
            });
          }}
          onSelectRow={(row: Ontology) => {
            navigate("/ontologies/" + row.getOntologyId());
          }}
          onFilter={(search: string) => {
            setSearchParams((params) => {
              params.delete("page");
              params.set("search", search);
              return params;
            });
          }}
        />
        {loading ? <LoadingOverlay message="Loading ontologies..." /> : null}
      </main>
    </div>
  );
}
