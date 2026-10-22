import { useEffect, useState } from "react";
import moment from "moment";
import Header from "../../components/Header";
import ReleaseNotes from "./ReleaseNotes";
import { loadReleases, ReleaseHistory } from "./release";

export default function Releases() {
  const [history, setHistory] = useState<ReleaseHistory>();
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setFailed(false);
    loadReleases().then(value => { if (active) setHistory(value); })
      .catch(() => { if (active) setFailed(true); });
    return () => { active = false; };
  }, [attempt]);
  return <>
    <Header section="releases" />
    <main className="container mx-auto px-4 my-8 max-w-4xl">
      <h1 className="text-3xl font-bold mb-4">OLS releases</h1>
      <p className="mb-6">Software releases and their archived source code. Ontology data is updated independently; its update date is shown on the homepage.</p>
      {failed ? <div role="alert">
        <p>Release information could not be loaded.</p>
        <button className="link-default mt-2" onClick={() => setAttempt(value => value + 1)}>Try again</button>
      </div> : !history ? <p role="status">Loading releases…</p> : <>
        {history.current && <p className="mb-4">This website runs OLS {history.current.version}.</p>}
        {history.conceptDoi && <p className="mb-6"><a className="link-default" href={`https://doi.org/${history.conceptDoi}`} target="_blank" rel="noopener noreferrer">OLS software archive across all versions</a></p>}
        {history.releases.length === 0 && <p>No numbered releases have been published for this build yet.</p>}
        {history.releases.map(release => <article key={release.version} id={`v${release.version}`} className="border border-neutral-light rounded-md p-6 mb-6">
          <h2 className="text-2xl font-bold">OLS {release.version}</h2>
          <p className="text-sm mt-1 mb-4">{moment.utc(release.releasedAt).format("D MMMM YYYY")}</p>
          <ReleaseNotes notes={release.notes} />
          <p><a className="link-default" href={release.releaseUrl} target="_blank" rel="noopener noreferrer">GitHub release</a>
            {" · "}<a className="link-default" href={`https://doi.org/${release.doi}`} target="_blank" rel="noopener noreferrer">Cite this release</a></p>
        </article>)}
      </>}
    </main>
  </>;
}
