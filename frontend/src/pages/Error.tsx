import { Fragment } from "react";
import { useLocation } from "react-router-dom";
import Header from "../components/Header";

export default function Error() {
  const location = useLocation();
  const message = location.state?.message;

  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header />
      <main className="container mx-auto">
        <img
          src={process.env.PUBLIC_URL + "/not-found.jpg"}
          className="max-w-lg rounded-lg mx-auto mb-4"
          alt="person using microscope"
        />
        <div className="text-center font-bold text-5xl mx-3 mb-4">
          Ooops! The requested page cannot be found.
        </div>
        <div className="text-center text-2xl mx-3 mb-8">
          <a
            className="link-default text-center text-3xl"
            href={process.env.PUBLIC_URL}
          >
            Return to home
          </a>
        </div>
        {message ? (
          <div className="bg-neutral-light rounded-lg px-8 py-4 mb-4">
            {message}
          </div>
        ) : null}
      </main>
    </Fragment>
  );
}
