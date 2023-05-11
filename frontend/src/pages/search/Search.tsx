import { KeyboardArrowDown } from "@mui/icons-material";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString, usePrevious } from "../../app/util";
import Header from "../../components/Header";
import LoadingOverlay from "../../components/LoadingOverlay";
import { Pagination } from "../../components/Pagination";
import SearchBox from "../../components/SearchBox";
import Entity from "../../model/Entity";
import { getSearchResults } from "./searchSlice";

export default function Search() {
  const params = useParams();
  let search: string = params.search as string;

  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const loadingResults = useAppSelector(
    (state) => state.search.loadingSearchResults
  );
  const results = useAppSelector((state) => state.search.searchResults);
  const totalResults = useAppSelector(
    (state) => state.search.totalSearchResults
  );
  const facets = useAppSelector((state) => state.search.facets);
  const prevSearch = usePrevious(search);

  const [query, setQuery] = useState<string>(search);
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(10);
  const [searchParams, setSearchParams] = useSearchParams();

  const ontologyFacets =
    facets && Object.keys(facets).length > 0 ? facets["ontologyId"] : {};
  const [ontologyFacetSelected, setOntologyFacetSelected] = useState<string[]>(
    []
  );
  const handleOntologyFacet = useCallback(
    (checked, key) => {
      let selected: string[] = ontologyFacetSelected;
      if (checked) {
        selected = [...selected, key];
      } else {
        selected = selected.filter((facet) => facet !== key);
      }
      setOntologyFacetSelected((prev) => {
        if (selected !== prev) setPage(0);
        return selected;
      });
    },
    [ontologyFacetSelected, setOntologyFacetSelected]
  );
  const typeFacets =
    facets && Object.keys(facets).length > 0 ? facets["type"] : {};
  const [typeFacetSelected, setTypeFacetSelected] = useState<string[]>([]);
  const handleTypeFacet = useCallback(
    (checked, key) => {
      let selected: string[] = typeFacetSelected;
      if (checked) {
        selected = [...selected, key];
      } else {
        selected = selected.filter((facet) => facet !== key);
      }
      setTypeFacetSelected((prev) => {
        if (selected !== prev) setPage(0);
        return selected;
      });
    },
    [typeFacetSelected, setTypeFacetSelected]
  );

  useEffect(() => {
    dispatch(
      getSearchResults({
        page,
        rowsPerPage,
        search,
        ontologyId: ontologyFacetSelected,
        type: typeFacetSelected,
        searchParams,
      })
    );
  }, [
    dispatch,
    search,
    page,
    rowsPerPage,
    ontologyFacetSelected,
    typeFacetSelected,
    searchParams,
  ]);
  useEffect(() => {
    if (prevSearch !== search) setPage(0);
  }, [search, prevSearch]);
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  });
  return (
    <div>
      <Header section="home" />
      <main className="container mx-auto h-fit my-8">
        <div className="flex flex-nowrap gap-4 mb-6">
          <SearchBox initialQuery={query} />
        </div>
        <div className="grid grid-cols-4 gap-8">
          <div className="col-span-1">
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8">
              <div className="font-bold text-neutral-dark text-sm mb-4">
                {`Showing ${
                  totalResults > rowsPerPage ? rowsPerPage : totalResults
                } from a total of ${totalResults}`}
              </div>
              {totalResults > 0 ? (
                <div className="text-neutral-black">
                  <div className="font-semibold text-lg mb-2">Type</div>
                  <fieldset className="mb-4">
                    {typeFacets && Object.keys(typeFacets).length > 0
                      ? Object.keys(typeFacets)
                          .sort((a, b) => {
                            const ac = a ? a.toString() : "";
                            const bc = b ? b.toString() : "";
                            return ac.localeCompare(bc);
                          })
                          .map((key) => {
                            if (key !== "entity" && typeFacets[key] > 0) {
                              return (
                                <label
                                  key={key}
                                  htmlFor={key}
                                  className="block p-1 w-fit"
                                >
                                  <input
                                    type="checkbox"
                                    id={key}
                                    className="invisible hidden peer"
                                    onChange={(e) => {
                                      handleTypeFacet(e.target.checked, key);
                                    }}
                                  />
                                  <span className="input-checkbox mr-4" />
                                  <span className="capitalize mr-4">
                                    {key} &#40;{typeFacets[key]}&#41;
                                  </span>
                                </label>
                              );
                            } else return null;
                          })
                      : null}
                  </fieldset>
                  <div className="font-semibold text-lg mb-2">Ontology</div>
                  <fieldset>
                    {ontologyFacets && Object.keys(ontologyFacets).length > 0
                      ? Object.keys(ontologyFacets)
                          .sort((a, b) => {
                            const ac = a ? a.toString() : "";
                            const bc = b ? b.toString() : "";
                            return ac.localeCompare(bc);
                          })
                          .map((key) => {
                            if (ontologyFacets[key] > 0) {
                              return (
                                <label
                                  key={key}
                                  htmlFor={key}
                                  className="block p-1 w-fit"
                                >
                                  <input
                                    type="checkbox"
                                    id={key}
                                    className="invisible hidden peer"
                                    onChange={(e) => {
                                      handleOntologyFacet(
                                        e.target.checked,
                                        key
                                      );
                                    }}
                                  />
                                  <span className="input-checkbox mr-4" />
                                  <span className="uppercase mr-4">
                                    {key} &#40;{ontologyFacets[key]}&#41;
                                  </span>
                                </label>
                              );
                            } else return null;
                          })
                      : null}
                  </fieldset>
                </div>
              ) : null}
            </div>
          </div>
          <div className="col-span-3">
            <div className="grid grid-cols-4 mb-4">
              <div className="justify-self-start col-span-3 self-center text-2xl font-bold text-neutral-dark">
                Search results for: {search}
              </div>
              <div className="justify-self-end col-span-1">
                <div className="flex group relative text-md">
                  <label className="self-center px-3">Show</label>
                  <select
                    className="input-default appearance-none pr-7 z-20 bg-transparent"
                    onChange={(e) => {
                      const rows = parseInt(e.target.value);
                      setRowsPerPage((prev) => {
                        if (rows !== prev) setPage(0);
                        return rows;
                      });
                    }}
                  >
                    <option value={10}>10</option>
                    <option value={25}>25</option>
                    <option value={100}>100</option>
                  </select>
                  <div className="absolute right-2 top-2 z-10 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
                    <KeyboardArrowDown fontSize="medium" />
                  </div>
                </div>
              </div>
            </div>
            {results.length > 0 ? (
              <div>
                <Pagination
                  page={page}
                  onPageChange={setPage}
                  dataCount={totalResults}
                  rowsPerPage={rowsPerPage}
                />
                {results.map((entity: Entity) => {
                  const MAX_DISPLAY_APPEARS_IN = 10;
                  const appearsInList = entity.getAppearsIn().filter(
                    (ontId) =>
                      ontId !== entity.getOntologyId() &&
                      entity
                        .getDefinedBy()
                        .filter((ontId) => ontId !== entity.getOntologyId())
                        .indexOf(ontId) === -1
                  );
                  let appearsInCopy: string[] = [];
                  if (appearsInList.length > MAX_DISPLAY_APPEARS_IN) {
                    appearsInCopy = appearsInList.slice(
                      0,
                      MAX_DISPLAY_APPEARS_IN
                    );
                  } else {
                    appearsInCopy = appearsInList.slice();
                  }
                  return (
                    <div key={randomString()} className="my-4">
                      <div className="mb-1 leading-loose truncate">
                        <Link
                          to={
                            "/ontologies/" +
                            entity.getOntologyId() +
                            "/" +
                            entity.getTypePlural() +
                            "/" +
                            encodeURIComponent(
                              encodeURIComponent(entity.getIri())
                            )
                          }
                          className={`link-default text-xl mr-2 ${
                            entity.isCanonical() ? "font-bold" : ""
                          }`}
                        >
                          {entity.getName()}
                        </Link>
                        <span className="bg-orange-default text-white rounded-md px-2 py-1 w-fit font-bold break-all">
                          {entity.getShortForm()}
                        </span>
                      </div>
                      <div className="mb-1 leading-relaxed text-sm text-neutral-default">
                        {entity.getIri()}
                      </div>
                      <div className="mb-1 leading-relaxed">
                        {entity.getDescription()}
                      </div>
                      <div className="leading-loose">
                        <span className="font-bold mr-1">Ontology:</span>
                        &nbsp;
                        <Link to={"/ontologies/" + entity.getOntologyId()}>
                          <span
                            className="link-ontology px-2 py-1 rounded-md text-sm text-white uppercase w-fit font-bold break-all"
                            title={entity.getOntologyId().toUpperCase()}
                          >
                            {entity.getOntologyId()}
                          </span>
                        </Link>
                      </div>
                      <div className="leading-loose">
                        {appearsInCopy && appearsInCopy.length > 0 ? (
                          <div
                            className="mb-2"
                            style={{ maxWidth: "100%", inlineSize: "100%" }}
                          >
                            <span className="font-bold mr-2">
                              Also appears in:
                            </span>
                            <>
                              {appearsInCopy.map((appearsIn) => {
                                return (
                                  <Link
                                    key={appearsIn}
                                    className="my-2"
                                    style={{ display: "inline-block" }}
                                    to={
                                      "/ontologies/" +
                                      appearsIn +
                                      `/${entity.getTypePlural()}/` +
                                      encodeURIComponent(
                                        encodeURIComponent(entity.getIri())
                                      )
                                    }
                                  >
                                    <span
                                      className="link-ontology px-2 py-1 rounded-md text-sm font-bold text-white uppercase mr-1"
                                      title={appearsIn.toUpperCase()}
                                    >
                                      {appearsIn}
                                    </span>
                                  </Link>
                                );
                              })}
                              &nbsp;
                              {appearsInList.length > MAX_DISPLAY_APPEARS_IN ? (
                                <Link
                                  to={
                                    "/ontologies/" +
                                    entity.getOntologyId() +
                                    "/" +
                                    entity.getTypePlural() +
                                    "/" +
                                    encodeURIComponent(
                                      encodeURIComponent(entity.getIri())
                                    )
                                  }
                                  className="link-default"
                                >
                                  +
                                </Link>
                              ) : null}
                            </>
                          </div>
                        ) : null}
                      </div>
                    </div>
                  );
                })}
                <Pagination
                  page={page}
                  onPageChange={setPage}
                  dataCount={totalResults}
                  rowsPerPage={rowsPerPage}
                />
              </div>
            ) : (
              <div className="text-xl text-neutral-black font-bold">
                No results!
              </div>
            )}
          </div>
        </div>
        {loadingResults ? (
          <LoadingOverlay message="Search results loading..." />
        ) : null}
      </main>
    </div>
  );
}
