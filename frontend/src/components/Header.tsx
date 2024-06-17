import { Link } from "react-router-dom";
import urlJoin from "url-join";
import { Helmet } from 'react-helmet';

export default function Header({ section }: { section?: string }) {

  return (
    <header
      className="bg-black bg-right bg-cover"
      style={{
        backgroundImage:
          "url('" +
          urlJoin(process.env.PUBLIC_URL!, "/embl-ebi-background-4.jpg") +
          "')",
      }}
    >
        <Helmet>
          <meta charSet="utf-8" />
          <title>{caps(section || 'Not Found')} - Ontology Lookup Service</title>
        </Helmet>
      <div className="container mx-auto px-4 flex flex-col md:flex-row md:gap-10">
        <div className="py-6 self-center">
          <a href={urlJoin(process.env.PUBLIC_URL!, "/")}>
            <img
              alt="OLS logo"
              className="h-24 inline-block"
              src={urlJoin(process.env.PUBLIC_URL!, "/logo.svg")}
            />
          </a>
        </div>
        <nav className="self-center">
          <ul
            className="bg-transparent text-white flex flex-wrap divide-white divide-x"
            data-description="navigational"
            role="menubar"
            data-dropdown-menu="6mg2ht-dropdown-menu"
          >
            <Link to="/">
              <li
                role="menuitem"
                className={`rounded-l-md px-4 py-3  ${
                  section === "home"
                    ? "bg-opacity-75 bg-neutral-500"
                    : "hover:bg-opacity-50 hover:bg-neutral-500"
                }`}
              >
                Home
              </li>
            </Link>
            <Link to="/ontologies">
              <li
                role="menuitem"
                className={`px-4 py-3 ${
                  section === "ontologies"
                    ? " bg-opacity-75 bg-neutral-500"
                    : "hover:bg-opacity-50 hover:bg-neutral-500 "
                }`}
              >
                Ontologies
              </li>
            </Link>
            <Link to={`/help`}>
              <li
                role="menuitem"
                className={`px-4 py-3  ${
                  section === "help"
                    ? " bg-opacity-75 bg-neutral-500"
                    : "hover:bg-opacity-50 hover:bg-neutral-500"
                }`}
              >
                Help
              </li>
            </Link>
            <Link to={`/about`}>
              <li
                role="menuitem"
                className={`px-4 py-3 ${
                  section === "about"
                    ? " bg-opacity-75 bg-neutral-500"
                    : "hover:bg-opacity-50 hover:bg-neutral-500"
                }`}
              >
                About
              </li>
            </Link>
            <Link to={`/downloads`}>
              <li
                role="menuitem"
                className={`rounded-r-md px-4 py-3 ${
                  section === "downloads"
                    ? " bg-opacity-75 bg-neutral-500"
                    : "hover:bg-opacity-50 hover:bg-neutral-500"
                }`}
              >
                Downloads
              </li>
            </Link>
          </ul>
        </nav>
      </div>
    </header>
  );
}

function caps(str) {
    return str[0].toUpperCase() + str.slice(1);
}

