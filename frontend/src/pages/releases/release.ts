import urlJoin from "url-join";

export interface Release {
  version: string;
  commit: string;
  releasedAt: string;
  notes: string;
  doi: string;
  releaseUrl: string;
}

export interface ReleaseHistory {
  current: Release | null;
  conceptDoi: string | null;
  releases: Release[];
}

// Read the manifest shipped in this frontend image, so an undeployed GitHub
// release cannot change the version shown on the website.
export async function loadReleases(): Promise<ReleaseHistory> {
  const response = await fetch(urlJoin(process.env.PUBLIC_URL || "/", "ols-release.json"));
  if (!response.ok) throw new Error("Release information could not be loaded.");
  return response.json();
}
