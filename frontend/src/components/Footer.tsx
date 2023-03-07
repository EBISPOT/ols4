export default function Footer() {
  return (
    <footer className="h-16 absolute inset-x-0 bottom-0 bg-embl-grey-dark flex flex-row items-center border-t-8 border-link-default">
      <span className="ml-8 text-embl-grey-lightest">
        <i className="icon icon-common icon-copyright icon-spacer" />
        EMBL-EBI&nbsp;2023
      </span>
      <a
        href={process.env.REACT_APP_EBI_LICENSING}
        className="ml-4 link-footer"
      >
        Licensing
      </a>
    </footer>
  );
}
