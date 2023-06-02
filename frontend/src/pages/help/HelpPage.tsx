import { Fragment } from "react";
import Header from "../../components/Header";

export default function Help() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="help" />
      <main className="container mx-auto my-8">
        {
          // process.env.REACT_APP_APIURL && (
          //   <Link
          //     to={process.env.REACT_APP_APIURL + "swagger-ui/index.html"}
          //     target="_blank"
          //     rel="noopener noreferrer"
          //   >
          //     <button className="button-secondary font-bold self-center">
          //       <div className="flex gap-2">
          //         <Source />
          //         <div>Swagger Documentation</div>
          //       </div>
          //     </button>
          //   </Link>
          // )
        }
        <div className="text-2xl font-bold my-6">Documentation</div>
        <div className="text-lg">
          <p className="mb-4">
            The OLS4 API should function identically to the OLS3 API. We are
            currently in the process of making shiny new documentation pages,
            but in the meantime you can refer to the old documentation&thinsp;
            <a
              href={`${process.env.REACT_APP_EBI_HOME}/ols/docs/api`}
              rel="noopener noreferrer"
              target="_blank"
              className="link-default"
            >
              here
            </a>
            .
          </p>
          <p className="bg-blue-50 px-6 pt-3 pb-4 rounded-md mb-4 text-justify">
            <span>
              <i className="icon icon-common icon-info text-2xl text-blue-500 mr-2 mb-1"></i>
            </span>
            Migrating to the OLS4 API should be as simple as replacing&thinsp;
            <strong>ols</strong> with <strong>ols4</strong> in the path. For
            example,
            <ul className="list-disc list-inside">
              <li>
                <code>http://www.ebi.ac.uk/ols/api/ontologies</code>&nbsp;(
                <i>before</i>)
              </li>
              <li>
                <code>http://www.ebi.ac.uk/ols4/api/ontologies</code>&nbsp;(
                <i>after</i>)
              </li>
            </ul>
          </p>
          <p className="mb-2">
            If you find any cases where the OLS4 API does not function the same
            as the OLS3 API, please open an issue on our&thinsp;
            <a
              href={`${process.env.REACT_APP_SPOT_OLS4_REPO}/issues`}
              rel="noopener noreferrer"
              target="_blank"
              className="link-default"
            >
              issue tracker
            </a>
          </p>
        </div>
      </main>
    </Fragment>
  );
}
