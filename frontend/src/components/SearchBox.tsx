import { Checkbox, FormControlLabel, ThemeProvider, Select, MenuItem, FormControl, InputLabel } from "@mui/material";
import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { get, getPaginated } from "../app/api";
import { theme } from "../app/mui";
import { randomString, thingFromJsonProperties, highlightMatch } from "../app/util";
import Entity from "../model/Entity";
import Ontology from "../model/Ontology";
import { Suggest } from "../model/Suggest";
import Thing from "../model/Thing";
import Model from "../model/Model";

let curSearchToken: any = null;

interface SearchBoxEntry {
  linkUrl: string;
  li: JSX.Element;
}

export default function SearchBox({
  initialQuery,
  placeholder,
  ontologyId,
}: {
  initialQuery?: string;
  placeholder?: string;
  ontologyId?: string;
}) {
  const [searchParams, setSearchParams] = useSearchParams();
  //   let lang = searchParams.get("lang") || "en";
  const navigate = useNavigate();

  const [autocomplete, setAutocomplete] = useState<Suggest | null>(null);
  const [jumpTo, setJumpTo] = useState<Thing[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [query, setQuery] = useState<string>(initialQuery || "");
  const [isFocused, setIsFocused] = useState(false);
  const [arrowKeySelectedN, setArrowKeySelectedN] = useState<
    number | undefined
  >(undefined);
  const [availableModels, setAvailableModels] = useState<Model[]>([]);
  const [selectedModel, setSelectedModel] = useState<string>(searchParams.get("model") || "lexical");

  let exact = searchParams.get("exactMatch") === "true";
  let obsolete = searchParams.get("includeObsoleteEntities") === "true";
  let canonical = searchParams.get("isDefiningOntology") === "true";

  const setExact = useCallback(
    (exact: boolean) => {
      let newSearchParams = new URLSearchParams(searchParams);
      newSearchParams.set("q", query);
      if (exact.toString() === "true") {
        newSearchParams.set("exactMatch", exact.toString());
      } else {
        newSearchParams.delete("exactMatch");
      }
      setSearchParams(newSearchParams);
    },
    [searchParams, setSearchParams, query]
  );

  const setObsolete = useCallback(
    (obsolete: boolean) => {
      let newSearchParams = new URLSearchParams(searchParams);
      newSearchParams.set("q", query);
      if (obsolete.toString() === "true") {
        newSearchParams.set("includeObsoleteEntities", obsolete.toString());
      } else {
        newSearchParams.delete("includeObsoleteEntities");
      }
      setSearchParams(newSearchParams);
    },
    [searchParams, setSearchParams, query]
  );

  const setCanonical = useCallback(
    (canonical: boolean) => {
      let newSearchParams = new URLSearchParams(searchParams);
      newSearchParams.set("q", query);
      if (canonical.toString() === "true") {
        newSearchParams.set("isDefiningOntology", canonical.toString());
      } else {
        newSearchParams.delete("isDefiningOntology");
      }
      setSearchParams(newSearchParams);
    },
    [searchParams, setSearchParams, query]
  );

  // Check if we're on the search results page (has a query in URL)
  const isOnSearchPage = !!searchParams.get("q");

  const handleModelChange = useCallback(
    (model: string) => {
      console.log("SearchBox handleModelChange called with:", model, "isOnSearchPage:", isOnSearchPage, "current q:", searchParams.get("q"));
      setSelectedModel(model);
      // If there's already a search query, update URL to trigger new search
      const currentQuery = searchParams.get("q");
      if (currentQuery) {
        const newSearchParams = new URLSearchParams(searchParams);
        if (model && model !== "lexical") {
          newSearchParams.set("model", model);
        } else {
          newSearchParams.delete("model");
        }
        console.log("SearchBox navigating to:", `/search?${newSearchParams}`);
        navigate(`/search?${newSearchParams}`);
      }
    },
    [searchParams, navigate]
  );

  const searchForOntologies = ontologyId === undefined;
  const showSuggestions = ontologyId === undefined;

  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    
    return () => {
      mounted.current = false;
    };
  });

  useEffect(() => {

    async function fetchModels() {
      setAvailableModels(await get<Model[]>("api/v2/llm_models"));
    }

    fetchModels();

  }, []);
    

  const cancelPromisesRef = useRef(false);
  useEffect(() => {
    // Clear previous results immediately when search params change
    setJumpTo([]);
    setAutocomplete(null);
    
    async function loadSuggestions() {
      setLoading(true);
      setArrowKeySelectedN(undefined);

      const searchToken = randomString();
      curSearchToken = searchToken;

      // Use llm_search endpoint if embedding model is selected
      const isEmbeddingSearch = selectedModel && selectedModel !== 'lexical';
      
      const entitiesPromise = isEmbeddingSearch
        ? getPaginated<any>(
            `api/v2/entities/llm_search?${new URLSearchParams({
              q: query,
              size: "5",
              model: selectedModel,
              ...(ontologyId ? { ontologyId } : {}),
            })}`
          )
        : getPaginated<any>(
            `api/v2/entities?${new URLSearchParams({
              search: query,
              size: "5",
              lang: "en",
              exactMatch: exact.toString(),
              includeObsoleteEntities: obsolete.toString(),
              ...(ontologyId ? { ontologyId } : {}),
              ...((canonical ? { isDefiningOntology: true } : {}) as any),
            })}`
          );

      const [entities, ontologies, autocomplete] = await Promise.all([
        entitiesPromise,
        searchForOntologies && !isEmbeddingSearch
          ? getPaginated<any>(
              `api/v2/ontologies?${new URLSearchParams({
                search: query,
                size: "5",
                lang: "en",
                exactMatch: exact.toString(),
                includeObsoleteEntities: obsolete.toString()
              })}`
            )
          : null,
        showSuggestions && !isEmbeddingSearch
          ? get<Suggest>(
              `api/suggest?${new URLSearchParams({
                q: query,
                exactMatch: exact.toString(),
                includeObsoleteEntities: obsolete.toString(),
              })}`
            )
          : null,
      ]);
      if (cancelPromisesRef.current && !mounted.current) return;

      if (searchToken === curSearchToken) {
        setJumpTo([
          ...entities.elements.map((obj) => thingFromJsonProperties(obj)),
          ...(ontologies?.elements.map((obj) => new Ontology(obj)) || []),
        ]);
        setAutocomplete(autocomplete);
        setLoading(false);
      }
    }

    loadSuggestions();

    return () => {
      cancelPromisesRef.current = true;
    };
  }, [query, exact, obsolete, canonical, selectedModel]);

  let autocompleteToShow = autocomplete?.response.docs.slice(0, 5) || [];
  let autocompleteElements = autocompleteToShow.map(
    (autocomplete, i): SearchBoxEntry => {
      const linkParams = new URLSearchParams(searchParams);
      linkParams.set("q", autocomplete.autosuggest);
      if (ontologyId) linkParams.set("ontology", ontologyId);
      const linkUrl = `/search?${linkParams}`;
      return {
        linkUrl,
        li: (
          <li
            key={autocomplete.autosuggest}
            className={
              "py-1 px-3 leading-7 hover:bg-link-light hover:cursor-pointer" +
              (arrowKeySelectedN === i ? " bg-link-light" : "")
            }
            onClick={() => {
              setQuery(autocomplete.autosuggest);
            }}
          >
            <span
              dangerouslySetInnerHTML={{
                __html: highlightMatch(autocomplete.autosuggest, query)
              }}
            />
          </li>
        ),
      };
    }
  );

  let jumpToEntityElements = jumpTo
    .filter((thing) => thing.getType() !== "ontology")
    .map((jumpToEntry: Thing, i: number): SearchBoxEntry => {
      const termUrl = encodeURIComponent(
        encodeURIComponent(jumpToEntry.getIri())
      );
      if (!(jumpToEntry instanceof Entity)) {
        throw new Error("jumpToEntry should be Entity");
      }
      // TODO which names to show? (multilang = lots of names)
      return jumpToEntry
        .getNames()
        .splice(0, 1)
        .map((name) => {
          const linkUrl = `/ontologies/${jumpToEntry.getOntologyId()}/${jumpToEntry.getTypePlural()}/${termUrl}`;
          return {
            linkUrl,
            li: (
              <li
                key={randomString()}
                className={
                  "py-1 px-3 leading-7 hover:bg-link-light hover:cursor-pointer" +
                  (arrowKeySelectedN === i + autocompleteElements.length
                    ? " bg-link-light"
                    : "")
                }
              >
                <Link
                  onClick={() => {
                    setQuery("");
                  }}
                  to={linkUrl}
                >
                  <div className="flex justify-between">
                    <div
                      className="truncate flex-auto"
                      title={name}
                      dangerouslySetInnerHTML={{
                        __html: highlightMatch(name, query)
                      }}
                    />
                    <div className="truncate flex-initial ml-2 text-right">
                      <span
                        className="mr-2 bg-link-default px-3 py-1 rounded-lg text-sm text-white uppercase"
                        title={jumpToEntry.getOntologyId()}
                      >
                        {jumpToEntry.getOntologyId()}
                      </span>
                      <span
                        className="bg-orange-default px-3 py-1 rounded-lg text-sm text-white uppercase"
                        title={jumpToEntry.getShortForm()}
                      >
                        {jumpToEntry.getShortForm()}
                      </span>
                    </div>
                  </div>
                </Link>
              </li>
            ),
          };
        })[0];
    });

  let jumpToOntologyElements = jumpTo
    .filter((thing) => thing.getType() === "ontology")
    .map((jumpToEntry: Thing, i: number): SearchBoxEntry => {
      if (!(jumpToEntry instanceof Ontology)) {
        throw new Error("jumpToEntry should be Ontology");
      }
      return jumpToEntry
        .getNames()
        .splice(0, 1)
        .map(() => {
          const linkUrl = "/ontologies/" + jumpToEntry.getOntologyId();
          return {
            linkUrl,
            li: (
              <li
                key={jumpToEntry.getOntologyId()}
                className={
                  "py-1 px-3 leading-7 hover:bg-link-light hover:cursor-pointer" +
                  (arrowKeySelectedN ===
                  i + jumpToEntityElements.length + autocompleteElements.length
                    ? " bg-link-light"
                    : "")
                }
              >
                <Link
                  onClick={() => {
                    setQuery("");
                  }}
                  to={linkUrl}
                >
                  <div className="flex justify-between">
                    <div
                      className="truncate flex-auto font-bold"
                      title={
                        jumpToEntry.getName() || jumpToEntry.getOntologyId()
                      }
                      dangerouslySetInnerHTML={{
                        __html: highlightMatch(jumpToEntry.getName() || jumpToEntry.getOntologyId(), query)
                      }}
                    />
                    <div className="truncate flex-initial ml-2 text-right">
                      <span className="bg-link-default px-3 py-1 rounded-lg text-sm text-white uppercase">
                        {jumpToEntry.getOntologyId()}
                      </span>
                    </div>
                  </div>
                </Link>
              </li>
            ),
          };
        })[0];
    });

  let allDropdownElements = [
    ...autocompleteElements,
    ...jumpToEntityElements,
    ...jumpToOntologyElements,
  ];

  return (
    <Fragment>
      <div className="w-full self-center">
        <div className="flex space-x-4 items-center mb-2">
          <div className="relative grow">
            <input
              id="home-search"
              type="text"
              autoComplete="off"
              placeholder={placeholder || "Search OLS..."}
              className={`input-default text-lg pl-3 ${
                query !== "" && isFocused
                  ? "rounded-b-sm rounded-b-sm shadow-input"
                  : ""
              }`}
              onBlur={() => {
                setTimeout(function () {
                  if (mounted.current) setIsFocused(false);
                }, 500);
              }}
              onFocus={() => {
                setIsFocused(true);
              }}
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
              }}
              onKeyDown={(ev) => {
                if (ev.key === "Enter") {
                  if (
                    arrowKeySelectedN !== undefined &&
                    arrowKeySelectedN < allDropdownElements.length
                  ) {
                    navigate(allDropdownElements[arrowKeySelectedN].linkUrl);
                  } else if (query) {
                    const navParams = new URLSearchParams(searchParams);
                    navParams.set("q", query);
                    if (ontologyId) navParams.set("ontology", ontologyId);
                    if (selectedModel && selectedModel !== "lexical") {
                      navParams.set("model", selectedModel);
                    } else {
                      navParams.delete("model");
                    }
                    navigate(`/search?${navParams}`);
                  }
                } else if (ev.key === "ArrowDown") {
                  setArrowKeySelectedN(
                    arrowKeySelectedN !== undefined
                      ? Math.min(
                          arrowKeySelectedN + 1,
                          allDropdownElements.length
                        )
                      : 0
                  );
                } else if (ev.key === "ArrowUp") {
                  if (arrowKeySelectedN !== undefined)
                    setArrowKeySelectedN(Math.max(arrowKeySelectedN - 1, 0));
                }
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
                query !== "" && isFocused
                  ? "list-none bg-white text-neutral-dark border-2 border-neutral-dark shadow-input rounded-b-md w-full absolute left-0 top-12 z-10"
                  : "hidden"
              }
            >
              {autocompleteElements.map((entry) => entry.li)}
              <hr />
              {jumpToEntityElements.length + jumpToOntologyElements.length >
                0 && (
                <div className="pt-1 px-3 leading-7">
                  <b>Jump to</b>
                </div>
              )}
              {jumpToEntityElements.map((entry) => entry.li)}
              {jumpToOntologyElements.map((entry) => entry.li)}
              <hr />
              {query && (
                <div
                  className={
                    "py-1 px-3 leading-7 hover:bg-link-light hover:rounded-b-sm hover:cursor-pointer" +
                    (arrowKeySelectedN === allDropdownElements.length
                      ? " bg-link-light"
                      : "")
                  }
                  onClick={() => {
                    if (query) {
                      const navParams = new URLSearchParams(searchParams);
                      navParams.set("q", query);
                      if (ontologyId)
                        navParams.set("ontology", ontologyId);
                      if (selectedModel && selectedModel !== "lexical") {
                        navParams.set("model", selectedModel);
                      } else {
                        navParams.delete("model");
                      }
                      navigate(`/search?${navParams}`);
                    }
                  }}
                >
                  <b className="pr-1">Search OLS for </b>
                  {query}
                </div>
              )}
            </ul>
          </div>
          <div>
            <button
              className="button-primary text-lg font-bold self-center"
              onClick={() => {
                if (query) {
                  const navParams = new URLSearchParams(searchParams);
                  navParams.set("q", query);
                  if (ontologyId) navParams.set("ontology", ontologyId);
                  if (selectedModel && selectedModel !== "lexical") {
                    navParams.set("model", selectedModel);
                  } else {
                    navParams.delete("model");
                  }
                  console.log("Search button clicked, navigating with model:", selectedModel, "url:", `/search?${navParams}`);
                  navigate(`/search?${navParams}`);
                }
              }}
            >
              Search
            </button>
          </div>
        </div>
        <div className="col-span-2">
          <ThemeProvider theme={theme}>
            <FormControlLabel
              control={
                <Checkbox
                  checked={exact}
                  onChange={(ev) => setExact(!!ev.target.checked)}
                />
              }
              label="Exact match"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={obsolete}
                  onChange={(ev) => setObsolete(!!ev.target.checked)}
                />
              }
              label="Include obsolete terms"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={!canonical}
                  onChange={(ev) => setCanonical(!ev.target.checked)}
                />
              }
              label="Include imported terms"
            />
            <FormControl sx={{ minWidth: 200, ml: 2 }} size="small">
              <InputLabel id="model-select-label">Search Model</InputLabel>
              <Select
                labelId="model-select-label"
                id="model-select"
                value={selectedModel}
                label="Search Model"
                onChange={(e) => handleModelChange(e.target.value)}
              >
                <MenuItem key="lexical" value="lexical">Lexical</MenuItem>
                {availableModels.filter((model) => model.can_embed).map((model) => (
                  <MenuItem key={model.model} value={model.model}>
                    {model.model === "lexical" ? "Lexical" : model.model}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </ThemeProvider>
        </div>
      </div>
    </Fragment>
  );
}
