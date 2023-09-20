import moment from "moment";
import { useEffect } from "react";
import { Link } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { Banner } from "../../components/Banner";
import Header from "../../components/Header";
import SearchBox from "../../components/SearchBox";
import { getBannerText, getStats } from "./homeSlice";

export default function Home() {
  const dispatch = useAppDispatch();
  const stats = useAppSelector((state) => state.home.stats);
  const banner = useAppSelector((state) => state.home.bannerText);

  useEffect(() => {
    dispatch(getStats());
  }, [dispatch]);

  useEffect(() => {
    dispatch(getBannerText());
  }, [dispatch]);

  if (banner !== "") console.log(banner);

  document.title = "Ontology Lookup Service (OLS)";
  return (
    <div>
      <Header section="home" />
      <main className="container mx-auto px-4 h-fit">
        {banner !== "" && (
          <div className="mt-4">
            <Banner type="warning">{banner}</Banner>
          </div>
        )}
        <div className="grid grid-cols-1 lg:grid-cols-4 lg:gap-8">
          <div className="lg:col-span-3">
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg my-8 p-8">
              <div className="text-3xl mb-4 text-neutral-black font-bold">
                Welcome to the EMBL-EBI Ontology Lookup Service
              </div>
              <div className="flex flex-nowrap gap-4 mb-4">
                <SearchBox />
              </div>
              <div className="grid md:grid-cols-2 grid-cols-1 gap-2">
                <div className="text-neutral-black">
                  <span>
                    Examples:&nbsp;
                    <Link to={"/search?q=diabetes"} className="link-default">
                      diabetes
                    </Link>
                    &#44;&nbsp;
                    <Link to={"/search?q=GO:0098743"} className="link-default">
                      GO:0098743
                    </Link>
                  </span>
                </div>
                <div className="md:text-right">
                  <Link to={"/ontologies"} className="link-default">
                    Looking for a particular ontology?
                  </Link>
                </div>
              </div>
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-8">
              <div className="px-2">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-browse icon-spacer text-yellow-default" />
                  <Link to={"/about"} className="link-default">
                    About OLS
                  </Link>
                </div>
                <p>
                  The Ontology Lookup Service (OLS) is a repository for
                  biomedical ontologies that aims to provide a single point of
                  access to the latest ontology versions. You can browse the
                  ontologies through the website as well as programmatically via
                  the OLS API. OLS is developed and maintained by the&thinsp;
                  <a
                    className="link-default"
                    href={process.env.REACT_APP_SPOT_HOME}
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    Samples, Phenotypes and Ontologies Team (SPOT)
                  </a>&thinsp;
                  at&thinsp;
                  <a
                    className="link-default"
                    href={process.env.REACT_APP_EBI_HOME}
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    EMBL-EBI
                  </a>
                  .
                </p>
              </div>
              <div className="px-2">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-tool icon-spacer text-yellow-default" />
                  <a
                    href={process.env.REACT_APP_SPOT_ONTOTOOLS}
                    className="link-default"
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    Related Tools
                  </a>
                </div>
                <p>
                  In addition to OLS the SPOT team also provides the&thinsp;
                  <a
                    className="link-default"
                    href={process.env.REACT_APP_SPOT_OXO}
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    OxO
                  </a>&thinsp;
                  and&thinsp;
                  <a
                    className="link-default"
                    href={process.env.REACT_APP_SPOT_ZOOMA}
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    ZOOMA
                  </a>&thinsp;
                  services. OxO provides cross-ontology mappings between terms
                  from different ontologies. ZOOMA is a service to assist in
                  mapping data to ontologies in OLS.
                </p>
              </div>
              <div className="px-2">
                <div className="text-2xl mb-3 text-neutral-default">
                  <i className="icon icon-common icon-exclamation-triangle icon-spacer text-yellow-default" />
                  <a
                    href={`${process.env.REACT_APP_SPOT_OLS4_REPO}/issues`}
                    className="link-default"
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    Report an Issue
                  </a>
                </div>
                <p>
                  For feedback, enquiries or suggestion about OLS or to request
                  a new ontology please use our&thinsp;
                  <a
                    href={`${process.env.REACT_APP_SPOT_OLS4_REPO}/issues`}
                    className="link-default"
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    GitHub issue tracker
                  </a>
                  . For announcements relating to OLS, such as new releases and
                  new features sign up to the&thinsp;
                  <a
                    className="link-default"
                    href={process.env.REACT_APP_SPOT_OLS_ANNOUNCE}
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    OLS announce mailing list
                  </a>
                  .
                </p>
              </div>
            </div>
          </div>
          <div className="lg:col-span-1 lg:order-none order-first">
            <div className="shadow-card border-b-8 border-link-default rounded-md mt-8 p-4">
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
              ) : (
                <div className="text-center">
                  <div className="spinner-default w-7 h-7" />
                </div>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
