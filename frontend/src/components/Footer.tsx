import { GitHub } from "@mui/icons-material";

export default function Footer() {
  return (
    <footer className="h-fit absolute inset-x-0 bottom-0 bg-embl-grey-dark border-t-8 border-link-default flex flex-col px-12 py-6 text-embl-grey-lightest">
      <div className="flex flex-row gap-10 border-b border-embl-grey-lightest mb-2 pb-6">
        <div className="flex flex-col">
          <div className="mb-2 text-xs uppercase font-bold">Follow us</div>
          <div className="flex gap-3">
            <a
              href={process.env.REACT_APP_SPOT_OLS4_REPO}
              className="link-footer text-xs font-bold self-center"
              title="GitHub"
              rel="noopener noreferrer"
              target="_blank"
            >
              <GitHub />
            </a>
            <a
              href="https://twitter.com/EBIOLS"
              className="link-footer text-2xl font-bold hover:no-underline"
              title="X"
              rel="noopener noreferrer"
              target="_blank"
            >
              X
            </a>
          </div>
        </div>
      </div>
      <div className="flex flex-row gap-4 h-6 items-center">
        <span>
          <i className="icon icon-common icon-copyright icon-spacer" />
          EMBL-EBI&nbsp;2023
        </span>
        <a
          href={process.env.REACT_APP_EBI_LICENSING}
          className="link-footer"
          rel="noopener noreferrer"
          target="_blank"
        >
          Licensing
        </a>
      </div>
    </footer>
  );
}
