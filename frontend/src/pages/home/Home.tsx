import moment from "moment";
import { useEffect, useRef, useState } from "react";
import { Link, useHistory } from "react-router-dom";
import { Timeline } from "react-twitter-widgets";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
import Header from "../../components/Header";
import Entity from "../../model/Entity";
import Ontology from "../../model/Ontology";
import Thing from "../../model/Thing";
import { getSearchOptions, getStats } from "./homeSlice";

export default function Home() {
  const dispatch = useAppDispatch();
  const history = useHistory();
  const stats = useAppSelector((state) => state.home.stats);
  const suggestions = useAppSelector((state) => state.home.searchOptions);
  const loading = useAppSelector((state) => state.home.loadingSearchOptions);
  const homeSearch = document.getElementById("home-search") as HTMLInputElement;

  const [open, setOpen] = useState<boolean>(false);
  const [query, setQuery] = useState<string>("");

  useEffect(() => {
    dispatch(getStats());
  }, [dispatch]);
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

  document.title = "Ontology Lookup Service (OLS)";
  return (
    <div>
      <Header section="home" />
      <main className="container mx-auto h-fit">
        <div className="grid grid-cols-4 gap-8">
          <div className="col-span-3">
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg my-8 p-8">
              <div className="text-3xl mb-4 text-neutral-black font-bold">
                Welcome to the EMBL-EBI Ontology Lookup Service
              </div>
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
                                    title={
                                      option.getName() || option.getOntologyId()
                                    }
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
              <div className="grid grid-cols-2">
                <div className="text-neutral-black">
                  <span>
                    Examples:&nbsp;
                    <a href="home/search/diabetes" className="link-default">
                      diabetes
                    </a>
                    &#44;&nbsp;
                    <a href="home/search/GO:0098743" className="link-default">
                      GO:0098743
                    </a>
                  </span>
                </div>
                <div className="text-right">
                  <a href="ontologies" className="link-default">
                    Looking for a particular ontology?
                  </a>
                </div>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-8">
              <div className="px-2 mb-4">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-browse icon-spacer text-orange-default" />
                  <a href="about" className="link-default">
                    About OLS
                  </a>
                </div>
                <p>
                  The Ontology Lookup Service (OLS) is a repository for
                  biomedical ontologies that aims to provide a single point of
                  access to the latest ontology versions. You can browse the
                  ontologies through the website as well as programmatically via
                  the OLS API. OLS is developed and maintained by the Samples,
                  Phenotypes and Ontologies Team (SPOT) at EMBL-EBI.
                </p>
              </div>
              <div className="px-2 mb-4">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-tool icon-spacer text-orange-default" />
                  <a
                    href="https://www.ebi.ac.uk/spot/ontology/"
                    className="link-default"
                  >
                    Related Tools
                  </a>
                </div>
                <p>
                  In addition to OLS the SPOT team also provides the OxO, Zooma
                  and Webulous services. OxO provides cross-ontology mappings
                  between terms from different ontologies. Zooma is a service to
                  assist in mapping data to ontologies in OLS and Webulous is a
                  tool for building ontologies from spreadsheets.
                </p>
              </div>
              <div className="px-2 mb-4">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-exclamation-triangle icon-spacer text-orange-default" />
                  <a
                    href="https://github.com/EBISPOT/OLS/issues"
                    className="link-default"
                  >
                    Report an Issue
                  </a>
                </div>
                <p>
                  For feedback, enquiries or suggestion about OLS or to request
                  a new ontology please use our GitHub issue tracker. For
                  announcements relating to OLS, such as new releases and new
                  features sign up to the OLS announce mailing list
                </p>
              </div>
            </div>
          </div>
          <div className="col-span-1">
            <div className="shadow-card border-b-8 border-link-default rounded-md my-8 p-4">
              <div className="text-2xl text-neutral-black font-bold mb-3">
                <i className="icon icon-common icon-analyse-graph icon-spacer" />
                <span>Data Content</span>
              </div>
              {stats ? (
                <div className="text-neutral-black">
                  <div className="mb-2 text-sm italic">
                    Updated&nbsp;
                      {moment(stats.lastModified).format(
                        "D MMM YYYY ddd HH:mm(Z)"
                      )}
                  </div>
                  <ul className="list-disc list-inside pl-2">
                    <li>
                      {stats.numberOfOntologies.toLocaleString()} ontologies
                    </li>
                    <li>{stats.numberOfClasses.toLocaleString()} classes</li>
                    <li>
                      {stats.numberOfProperties.toLocaleString()} properties
                    </li>
                    <li>
                      {stats.numberOfIndividuals.toLocaleString()} individuals
                    </li>
                  </ul>
                </div>
              ) : null}
            </div>
            <div className="mb-4">
              <Timeline
                dataSource={{
                  sourceType: "profile",
                  screenName: "EBIOLS",
                }}
                options={{ height: 600 }}
              />
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
