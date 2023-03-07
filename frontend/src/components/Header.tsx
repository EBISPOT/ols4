import { Link } from "react-router-dom";

export default function Header({
  section,
}: // projectId,
{
  section: string;
  // projectId?: string;
}) {
  return (
    <header
      className="bg-black bg-right bg-cover"
      style={{
        backgroundImage:
          "url('" + process.env.PUBLIC_URL + "/embl-ebi-background-4.jpg')",
      }}
    >
      <div className="container mx-auto flex flex-row gap-10">
        <div className="py-6">
          <a href={process.env.PUBLIC_URL}>
            <img
              alt="OxO logo"
              className="h-24 inline-block"
              src={process.env.PUBLIC_URL + "/logo.png"}
            />
          </a>
        </div>
        <nav className="self-center">
          <ul
            className="bg-transparent text-white flex divide-white divide-x"
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
                className={`rounded-r-md px-4 py-3 ${
                  section === "about"
                    ? " bg-opacity-75 bg-neutral-500"
                    : "hover:bg-opacity-50 hover:bg-neutral-500"
                }`}
              >
                About
              </li>
            </Link>
          </ul>
        </nav>
      </div>
    </header>
  );
}
