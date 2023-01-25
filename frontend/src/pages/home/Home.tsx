import moment from "moment";
import { Fragment, useEffect, useRef, useState } from "react";
import { Link, useHistory } from "react-router-dom";
import { Timeline } from "react-twitter-widgets";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString } from "../../app/util";
import Header from "../../components/Header";
import SearchBox from "../../components/SearchBox";
import Entity from "../../model/Entity";
import Ontology from "../../model/Ontology";
import Thing from "../../model/Thing";
import { getStats } from "./homeSlice";

export default function Home() {
  const dispatch = useAppDispatch();
  const history = useHistory();
  const stats = useAppSelector((state) => state.home.stats);

  useEffect(() => {
    dispatch(getStats());
  }, [dispatch]);

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
		<SearchBox />
              </div>
              <div className="grid grid-cols-2">
                <div className="text-neutral-black">
                  <span>
                    Examples:&nbsp;
                    <a href="search/diabetes" className="link-default">
                      diabetes
                    </a>
                    &#44;&nbsp;
                    <a href="search/GO:0098743" className="link-default">
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
