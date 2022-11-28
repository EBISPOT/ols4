import { useEffect, useState } from "react";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
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

  return (
    <div className="flex flex-nowrap gap-4 my-2">
      <div className="relative w-full self-center">
        <input
          type="text"
          placeholder="Search OLS..."
          className="input-default text-lg focus:rounded-b-sm focus-visible:rounded-b-sm"
          onFocus={() => {
            setOpen(true);
          }}
          onBlur={() => {
            setOpen(false);
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
          id="home-search"
          className={
            open
              ? "list-none bg-white text-neutral-dark border-2 border-neutral-dark shadow-input rounded-b-md w-full absolute left-0 top-12 z-10"
              : "hidden"
          }
        >
          {results.length === 0 ? (
            <span className="px-2 py-4 text-lg leading-loose">No options</span>
          ) : (
            results.map((option: Thing) => {
              if (option instanceof Ontology) return null;
              else
                return (
                  <li
                    key={randomString()}
                    className="flex justify-between p-2 hover:bg-link-light hover:cursor-pointer"
                  >
                    <span className="truncate self-center">
                      {option.getName()}
                    </span>
                    <span className="self-center bg-link-default px-3 py-1 rounded-lg text-white uppercase text-right">
                      {option.getOntologyId()}
                    </span>
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
