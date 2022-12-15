import { useEffect, useRef, useState } from "react";
import { Link, useHistory } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
import Header from "../../components/Header";
import { Pagination } from "../../components/Pagination";
import Entity from "../../model/Entity";
import Ontology from "../../model/Ontology";
import Thing from "../../model/Thing";
import { getSearchOptions, getSearchResults } from "./homeSlice";

export default function SearchResults({ search }: { search: string }) {
  const dispatch = useAppDispatch();
  const history = useHistory();
  const loadingSuggestions = useAppSelector(
    (state) => state.home.loadingSearchOptions
  );
  const suggestions = useAppSelector((state) => state.home.searchOptions);
  const results = useAppSelector((state) => state.home.searchResults);
  const totalResults = useAppSelector((state) => state.home.totalSearchResults);

  const [open, setOpen] = useState<boolean>(false);
  const [query, setQuery] = useState<string>(search);
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(10);

  const homeSearch = document.getElementById("home-search") as HTMLInputElement;

  useEffect(() => {
    dispatch(getSearchOptions(query));
  }, [dispatch, query]);
  useEffect(() => {
    dispatch(getSearchResults({page, rowsPerPage, search}));
  }, [dispatch, page, rowsPerPage, search]);
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
        <div className="flex flex-nowrap gap-4 mb-4">
          <div className="relative w-full self-center">
            <input
              id="home-search"
              type="text"
              placeholder="Search OLS..."
              className="input-default text-lg focus:rounded-b-sm focus-visible:rounded-b-sm pl-3"
              onFocus={() => {
                setOpen(true);
              }}
              onBlur={() => {
                setTimeout(function () {
                  if (mounted.current) setOpen(false);
                }, 500);
              }}
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
              }}
            />
            <div
              className={
                loadingSuggestions
                  ? "spinner-default w-7 h-7 absolute right-3 top-2.5 z-10"
                  : "hidden"
              }
            />
            <ul
              className={
                open
                  ? "list-none bg-white text-neutral-dark border-2 border-neutral-dark shadow-input rounded-b-md w-full absolute left-0 top-12 z-10"
                  : "hidden"
              }
            >
              {suggestions.length === 0 ? (
                <div className="py-1 px-3 text-lg leading-loose">
                  No options
                </div>
              ) : (
                suggestions.map((option: Thing) => {
                  const termUrl = encodeURIComponent(
                    encodeURIComponent(option.getIri())
                  );
                  return (
                    <li
                      key={randomString()}
                      className="py-2 px-3 leading-7 hover:bg-link-light hover:rounded-sm hover:cursor-pointer"
                    >
                      {option instanceof Entity ? (
                        <Link
                          onClick={() => {
                            setOpen(false);
                          }}
                          to={`/ontologies/${option.getOntologyId()}/${option.getTypePlural()}/${termUrl}`}
                        >
                          <div className="flex justify-between">
                            <div
                              className="truncate flex-auto"
                              title={option.getName()}
                            >
                              {option.getName()}
                            </div>
                            <div className="truncate flex-initial ml-2 text-right">
                              <span
                                className="mr-2 bg-link-default px-3 py-1 rounded-lg text-sm text-white uppercase"
                                title={option.getOntologyId()}
                              >
                                {option.getOntologyId()}
                              </span>
                              <span
                                className="bg-orange-default px-3 py-1 rounded-lg text-sm text-white uppercase"
                                title={option.getShortForm()}
                              >
                                {option.getShortForm()}
                              </span>
                            </div>
                          </div>
                        </Link>
                      ) : null}
                      {option instanceof Ontology ? (
                        <Link
                          onClick={() => {
                            setOpen(false);
                          }}
                          to={"/ontologies/" + option.getOntologyId()}
                        >
                          <div className="flex">
                            <span
                              className="truncate text-link-dark font-bold"
                              title={option.getName() || option.getOntologyId()}
                            >
                              {option.getName() || option.getOntologyId()}
                            </span>
                          </div>
                        </Link>
                      ) : null}
                    </li>
                  );
                })
              )}
            </ul>
          </div>
          <button
            className="button-primary text-lg font-bold self-center"
            onClick={() => {
              if (homeSearch?.value) {
                history.push("/home/search/" + homeSearch.value);
              }
            }}
          >
            Search
          </button>
        </div>
        {results.map((entity: Entity) => {
          return (
            <div key={randomString()}>
              <div>
                <a
                  href={
                    "/ontologies/" +
                    entity.getOntologyId() +
                    "/" +
                    entity.getTypePlural() +
                    "/" +
                    encodeURIComponent(encodeURIComponent(entity.getIri()))
                  }
                  className="link-default"
                >
                  {entity.getName()}
                </a>
                &nbsp;
                <span className="bg-orange-default text-white rounded-md px-2 py-1 w-fit font-bold break-all">
                  {entity.getShortForm()}
                </span>
              </div>
              <div>{entity.getIri()}</div>
              <div>{entity.getDescription()}</div>
              <div>
                <span className="font-bold">Ontology:</span>
                &nbsp;
                <span className="bg-petrol-default text-white rounded-md px-2 py-1 w-fit font-bold break-all">
                  {entity.getOntologyId().toUpperCase()}
                </span>
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
      </main>
    </div>
  );
}
