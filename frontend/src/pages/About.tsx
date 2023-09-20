import { Fragment } from "react";
import Header from "../components/Header";

export default function About() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="about" />
      <main className="container mx-auto px-4 my-8">
        <div className="text-2xl font-bold my-6">About OLS</div>
        <div>
          <p className="my-2">
            The Ontology Lookup Service (OLS) is a repository for biomedical
            ontologies that aims to provide a single point of access to the
            latest ontology versions. You can browse the ontologies through the
            website as well as programmatically via the OLS API. OLS is
            developed and maintained by the&thinsp;
            <a
              className="link-default"
              href="http://www.ebi.ac.uk/about/spot-team"
              rel="noopener noreferrer"
              target="_blank"
            >
              Samples, Phenotypes and Ontologies Team
            </a>
            &thinsp;at EMBL-EBI.
          </p>
        </div>
        <div className="text-2xl font-bold my-6">Funding</div>
        <div>
          <p className="my-2">
            OLS is supported in part by CORBEL funded by the EU's Horizon 2020
            research and innovation programme (2014-2020) under grant agreement
            number 654248 and previously by&thinsp;
            <a
              className="link-default"
              href="http://www.diachron-fp7.eu/"
              rel="noopener noreferrer"
              target="_blank"
            >
              DIACHRON
            </a>
            , EU FP7 Capacities Specific
            Programme,/grant/agreement/number/284209.
          </p>
        </div>
        <div className="text-2xl font-bold my-6">Contact</div>
        <div>
          <ul className="list-disc list-inside">
            <li>
              For feedback, enquiries or suggestion about OLS or to request a
              new ontology please contact&thinsp;
              <a
                className="link-default"
                href="mailto:ols-support@ebi.ac.uk"
                rel="noopener noreferrer"
                target="_blank"
              >
                ols-support@ebi.ac.uk
              </a>
            </li>
            <li>
              For bugs or problems with the code or API please create a GitHub
              issue (
              <a
                className="link-default"
                href="https://github.com/EBISPOT/ols4"
                rel="noopener noreferrer"
                target="_blank"
              >
                https://github.com/EBISPOT/ols4
              </a>
              )
            </li>
            <li>
              For announcements relating to OLS (low traffic), such as new
              releases and new features sign up to the&thinsp;
              <a
                className="link-default"
                href="https://listserver.ebi.ac.uk/mailman/listinfo/ols-announce"
                rel="noopener noreferrer"
                target="_blank"
              >
                OLS announce mailing list
              </a>
            </li>
          </ul>
        </div>
        <div className="text-2xl font-bold my-6">Privacy Policy</div>
        <div>
          <p className="my-2">
            The General Data Protection Regulation (GDPR) will apply in the UK
            from 25 May 2018. It will replace the 1998 Data Protection Act and
            introduce new rules on privacy notices, as well as processing and
            safeguarding personal data.
          </p>
          <p className="my-2">
            This website requires cookies, and the limited processing of your
            personal data in order to function. By using the site you are
            agreeing to this as outlined in our&thinsp;
            <a
              className="link-default"
              href="https://www.ebi.ac.uk/data-protection/privacy-notice/embl-ebi-public-website"
              rel="noopener noreferrer"
              target="_blank"
            >
              Privacy Notice
            </a>
            &thinsp; and&thinsp;
            <a
              className="link-default"
              href="https://www.ebi.ac.uk/about/terms-of-use"
              rel="noopener noreferrer"
              target="_blank"
            >
              Terms of Use
            </a>
            .
          </p>
          <p className="my-2">
            <a
              className="link-default"
              href="https://www.ebi.ac.uk/data-protection/privacy-notice/ols"
              rel="noopener noreferrer"
              target="_blank"
            >
              OLS Submission Service
            </a>
            &thinsp; applies to the data submitted to OLS (eg. Ontology metadata
            or ontologies) or the data pulled out from other data providers
            (such as the OBO foundry).
          </p>
          <p className="my-2">
            <a
              className="link-default"
              href="https://www.ebi.ac.uk/data-protection/privacy-notice/ols-mailing-list"
              rel="noopener noreferrer"
              target="_blank"
            >
              OLS Mail Service
            </a>
            &thinsp; applies to our public e-mail lists; ols-support [at]
            ebi.ac.uk, ols-announce [at] ebi.ac.uk and ontology-tools-support
            [at] ebi.ac.uk.
          </p>
        </div>
      </main>
    </Fragment>
  );
}
