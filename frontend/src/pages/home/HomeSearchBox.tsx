import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
import Entity from "../../model/Entity";
import Ontology from "../../model/Ontology";
import Thing from "../../model/Thing";
import { getSearchOptions } from "./homeSlice";

export default function HomeSearchBox() {
  const dispatch = useAppDispatch();
  const results = useAppSelector((state) => state.home.searchResult);
  const loading = useAppSelector((state) => state.home.loadingSearchResult);

  const [open, setOpen] = useState<boolean>(false);
  const [query, setQuery] = useState<string>("");

  useEffect(() => {
    dispatch(getSearchOptions(query));
  }, [dispatch, query]);

  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  });

  return (
    <div className="flex flex-nowrap gap-4 my-2">
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
            loading
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
          onClick={() => {
            setOpen(false);
          }}
        >
          {results.length === 0 ? (
            <div className="py-1 px-3 text-lg leading-loose">No options</div>
          ) : (
            results.map((option: Thing) => {
              const termUrl = encodeURIComponent(
                encodeURIComponent(option.getIri())
              );
              return (
                <li
                  key={randomString()}
                  className="py-2 px-3 hover:bg-link-light hover:rounded-sm hover:cursor-pointer"
                >
                  {option instanceof Entity ? (
                    <Link
                      to={`/ontologies/${option.getOntologyId()}/${option.getTypePlural()}/${termUrl}`}
                    >
                      <div className="flex justify-between">
                        <span className="truncate">{option.getName()}</span>
                        <span className="ml-2 text-right">
                          <span className="mr-2 truncate bg-link-default px-3 py-1 rounded-lg text-sm text-white uppercase">
                            {option.getOntologyId()}
                          </span>
                          <span className="bg-orange-default px-3 py-1 rounded-lg text-sm text-white uppercase">
                            {option.getShortForm()}
                          </span>
                        </span>
                      </div>
                    </Link>
                  ) : null}
                  {option instanceof Ontology ? (
                    <Link to={"/ontologies/" + option.getOntologyId()}>
                      <div className="flex">
                        <span className="truncate text-link-dark font-bold">
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
      <button className="button-primary text-lg font-bold self-center">
        Search
      </button>
    </div>
  );
}
