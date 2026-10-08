import { Fragment } from "react";
import Header from "../components/Header";
import urlJoin from "url-join";
import { List, ListItem } from "@mui/material";

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
        <h2 className="text-2xl font-bold my-6">How to cite OLS</h2>
        <div>
          <p className="my-2">
            McLaughlin J, Lagrimas J, Iqbal H, Parkinson H, Harmse H.{" "}
            <strong>
              OLS4: a new Ontology Lookup Service for a growing interdisciplinary
              knowledge ecosystem.
            </strong>{" "}
            <em>Bioinformatics.</em> 2025;41(5):btaf279.{" "}
            <a
              className="link-default"
              href="https://doi.org/10.1093/bioinformatics/btaf279"
              rel="noopener noreferrer"
              target="_blank"
            >
              doi:10.1093/bioinformatics/btaf279
            </a>
            .
          </p>
        </div>
        <div className="text-2xl font-bold my-6">Funding</div>
        <div>
          OLS has been supported by:
          <List>
            <ListItem>EMBL-EBI Core Funds</ListItem>
            <ListItem>European Union HORIZON program grant number 101131959</ListItem>
            <ListItem>Chan–Zuckerberg Initiative award for the Human Cell Atlas Data Coordination Platform</ListItem>
            <ListItem>Office of the Director, National Institutes of Health (R24-OD011883, OT2OD033756)</ListItem>
            <ListItem>NIH National Human Genome Research Institute Phenomics First Resource, NIH-NHGRI # 5RM1 HG010860, a Center of Excellence in Genomic Science</ListItem>
            <ListItem>European Union’s Horizon 2020 research, and innovation program grant numbers 824087 (European Open Science Cloud Life from June 2020 to August 2023) and 825575 (European Joint Programme on Rare Diseases from June 2020 to December 2023)</ListItem>
            <ListItem>The EVORA project has received funding from the European Union's HORIZON programme under grant agreement No 101131959.</ListItem>
            <ListItem> CORBEL funded by the EU's Horizon 2020 research and innovation programme (2014-2020) under grant agreement number 654248</ListItem>
            <ListItem>DIACHRON, EU FP7 Capacities Specific Programme,/grant/agreement/number/284209.</ListItem>
          </List>
        </div>
        <div className="text-2xl font-bold my-6">Contact</div>
        <div>
          <ul className="list-disc list-inside">
            <li>
              For feedback, enquiries, suggestions about OLS or to request a
              new ontology please create a GitHub
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
        <h2 className="text-2xl font-bold my-6">Software preservation</h2>
        <div>
          <p className="my-2">
            OLS software releases are archived for continued access, citation and
            reuse. Our preservation policy describes what is preserved, retention
            arrangements and responsibilities if the service closes.
          </p>
          <p className="my-2">
            <a
              className="link-default"
              href={urlJoin(process.env.PUBLIC_URL || "/", "documents/ols-preservation-policy.pdf")}
              rel="noopener noreferrer"
              target="_blank"
            >
              OLS preservation policy (PDF, 254 KB)
            </a>
          </p>
          <p className="my-2">Version 1.00 · 2 October 2026</p>
          <h3 className="text-xl font-bold my-4">OLS data releases</h3>
          <p className="my-2">
            The latest OLS data releases, including OLS embeddings data, are
            available from the EMBL-EBI FTP site.
          </p>
          <p className="my-2">
            <a
              className="link-default"
              href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/"
              rel="noopener noreferrer"
              target="_blank"
            >
              Download the latest OLS data release
            </a>
          </p>
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
              href={urlJoin(process.env.PUBLIC_URL!, "/Privacy_notice_for_EMBL-EBI_Public_Website.pdf")}
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
              href={urlJoin(process.env.PUBLIC_URL!, "/Privacy_notice_for_OLS_submission_service_email_based.pdf")}
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
              href={urlJoin(process.env.PUBLIC_URL!, "/Privacy_notice_for_OLS_mailing_list.pdf")}
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
