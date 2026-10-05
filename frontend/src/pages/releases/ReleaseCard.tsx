import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import moment from "moment";
import { loadReleases, ReleaseHistory } from "./release";

export default function ReleaseCard() {
  const [history, setHistory] = useState<ReleaseHistory>();
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    loadReleases().then(value => { if (active) setHistory(value); })
      .catch(() => { if (active) setFailed(true); });
    return () => { active = false; };
  }, []);
  const release = history?.current;
  return (
    <section aria-label="OLS software release" className="shadow-card border-b-8 border-link-default rounded-md mt-4 p-4">
      <h2 className="text-xl text-neutral-black font-bold mb-2">OLS release</h2>
      {release ? <>
        <p className="text-2xl text-neutral-black font-bold">{release.version}</p>
        <p className="text-sm mt-1">Released {moment.utc(release.releasedAt).format("D MMMM YYYY")}</p>
        <p className="mt-3"><Link className="link-default" to="/releases">Release notes</Link>
          {" · "}<a className="link-default" href={`https://doi.org/${release.doi}`} target="_blank" rel="noopener noreferrer">Cite this release</a></p>
      </> : <>
        <p className="text-sm">{failed ? "Release information is unavailable." : history ? "Development build" : "Loading release information…"}</p>
        <Link className="link-default" to="/releases">Release history</Link>
      </>}
    </section>
  );
}
