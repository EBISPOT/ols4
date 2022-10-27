import { Theme } from "@mui/material";
import { createStyles, makeStyles } from "@mui/styles";
import { useEffect } from "react";
import { Timeline } from "react-twitter-widgets";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import Header from "../../components/Header";
import Spinner from "../../components/Spinner";
import HomeSearchBox from "./HomeSearchBox";
import { getStats } from "./homeSlice";

const useStyles: any = makeStyles((theme: Theme) => createStyles({}));

export default function Home() {
  const dispatch = useAppDispatch();
  const stats = useAppSelector((state) => state.home.stats);

  useEffect(() => {
    dispatch(getStats());
  }, []);

  document.title = "Ontology Lookup Service (OLS)";
  return (
    <div>
      <Header section="home" />
      <main className="container mx-auto h-fit">
        <div className="grid grid-cols-4 gap-8">
          <div className="col-span-3">
            <div className="bg-gradient-to-r from-grey-50 to-grey-1 rounded-lg my-8 p-8">
              <div className="text-3xl mb-4 text-grey-2 font-bold">
                Welcome to the EMBL-EBI Ontology Lookup Service
              </div>
              <div className="mb-4">
                <HomeSearchBox />
              </div>
              {/* <TextField fullWidth size="small" label="Search OLS..." inputProps={{
									startAdornment: <InputAdornment position="start"><Search /></InputAdornment>,
									classes: {
										adornedStart: classes.adornedStart
								}}/> */}
              <div className="grid grid-cols-2">
                <div>Examples: diabetes, GO:0098743</div>
                <div className="text-grey-2 text-right">
                  <a
                    href="ontologies"
                    className="underline underline-offset-4 decoration-dotted"
                  >
                    Looking for a particular ontology?
                  </a>
                </div>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-8">
              <div className="px-2 mb-4">
                <div className="text-2xl mb-3 text-grey-2">
                  <i className="icon icon-common icon-browse icon-spacer text-yellow-600" />
                  <a
                    href="about"
                    className="underline underline-offset-4 decoration-dotted"
                  >
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
                <div className="text-2xl mb-3 text-grey-2">
                  <i className="icon icon-common icon-tool icon-spacer text-yellow-600" />
                  <a
                    href="https://www.ebi.ac.uk/spot/ontology/"
                    className="underline underline-offset-4 decoration-dotted"
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
                <div className="text-2xl mb-3 text-grey-2">
                  <i className="icon icon-common icon-exclamation-triangle icon-spacer text-yellow-600" />
                  <a
                    href="https://github.com/EBISPOT/OLS/issues"
                    className="underline underline-offset-4 decoration-dotted"
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
            <div className="bg-gradient-to-r from-grey-1 to-white rounded-lg my-8 p-4">
              <div className="text-2xl text-grey-2 mb-2">
                <i className="icon icon-common icon-analyse-graph icon-spacer" />
                <span>Data Content</span>
              </div>
              {stats ? (
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
              ) : (
                <Spinner />
              )}
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
  // return <div>
  //     {/* <div>Logged in as {getToken().authEmail} with token {getToken().auth}</div> */}
  //         <Breadcrumbs>
  //             <Link color="inherit" href="/">
  //                 Projects
  //             </Link>
  //         </Breadcrumbs>
  //     <h2>Projects</h2>
  //     <ProjectList />
  // </div>
}
