/**
 * API client and types for the /v2/tag_text endpoint
 */

export interface TaggedEntity {
  start: number;
  end: number;
  term_label: string;
  term_iri: string;
  ontology_id: string;
}

export interface TagTextResponse {
  text: string;
  entities: TaggedEntity[];
}

/**
 * Call POST /api/v2/tag_text
 * @param text - The text to annotate
 * @param ontologyIds - Optional ordered list of ontology IDs to filter/prioritise
 */
export async function tagText(
  text: string,
  ontologyIds?: string[],
  minLength: number = 6,
  includeSubstrings: boolean = true
): Promise<TagTextResponse> {
  const baseUrl = process.env.REACT_APP_APIURL || "http://localhost:8080/";
  let url = baseUrl + "api/v2/tag_text";

  const params = new URLSearchParams();
  if (ontologyIds && ontologyIds.length > 0) {
    for (const id of ontologyIds) {
      params.append("ontologyId", id);
    }
  }
  // Sensible defaults: word-boundary delimiters so only whole tokens match
  params.set("delimiters", " ,.;:!?\t\n()[]{}\"'/\\-");
  params.set("minLength", String(minLength));
  params.set("includeSubstrings", String(includeSubstrings));

  url += "?" + params.toString();

  const res = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ text }),
  });

  if (!res.ok) {
    const message = `Text tagging failed: ${res.status} (${res.statusText})`;
    throw new Error(message);
  }

  return res.json();
}

/**
 * Check if the tag_text service is available
 */
export async function tagTextAvailable(): Promise<boolean> {
  try {
    const baseUrl = process.env.REACT_APP_APIURL || "http://localhost:8080/";
    const res = await fetch(baseUrl + "api/v2/tag_text");
    if (!res.ok) return false;
    const data = await res.json();
    return data.available === true;
  } catch {
    return false;
  }
}

// ─── Overlap-aware segment computation ───────────────────────────────────────

/**
 * A segment of text that may be covered by zero or more entities.
 * When multiple entities overlap at the same position, they are all listed.
 */
export interface TextSegment {
  start: number;
  end: number;
  text: string;
  /** All entities that span this segment (empty = plain text) */
  entities: TaggedEntity[];
}

/**
 * Given raw text and a list of (possibly overlapping) entities,
 * split the text into non-overlapping segments where each segment
 * carries the set of entities that cover it.
 *
 * This correctly handles partial overlaps, nesting, and disjoint spans.
 */
export function computeSegments(
  text: string,
  entities: TaggedEntity[]
): TextSegment[] {
  if (!entities || entities.length === 0) {
    return [{ start: 0, end: text.length, text, entities: [] }];
  }

  // Collect all unique boundary points
  const boundarySet = new Set<number>();
  boundarySet.add(0);
  boundarySet.add(text.length);
  for (const e of entities) {
    boundarySet.add(Math.max(0, e.start));
    boundarySet.add(Math.min(text.length, e.end));
  }
  const boundaries = Array.from(boundarySet).sort((a, b) => a - b);

  const segments: TextSegment[] = [];
  for (let i = 0; i < boundaries.length - 1; i++) {
    const segStart = boundaries[i];
    const segEnd = boundaries[i + 1];
    if (segStart === segEnd) continue;

    // Find all entities that fully cover this segment
    const covering = entities.filter(
      (e) => e.start <= segStart && e.end >= segEnd
    );

    segments.push({
      start: segStart,
      end: segEnd,
      text: text.slice(segStart, segEnd),
      entities: covering,
    });
  }

  return segments;
}

// ─── Ontology colour assignment ──────────────────────────────────────────────

/**
 * A curated palette of 20 distinct colours for ontology highlighting.
 * These are designed to be distinguishable at 30% opacity for backgrounds,
 * and at full saturation for list badges.
 */
const ONTOLOGY_COLORS = [
  "#2563eb", // blue-600
  "#dc2626", // red-600
  "#16a34a", // green-600
  "#9333ea", // purple-600
  "#ea580c", // orange-600
  "#0891b2", // cyan-600
  "#ca8a04", // yellow-600
  "#db2777", // pink-600
  "#4f46e5", // indigo-600
  "#059669", // emerald-600
  "#e11d48", // rose-600
  "#7c3aed", // violet-600
  "#0284c7", // sky-600
  "#65a30d", // lime-600
  "#d97706", // amber-600
  "#0d9488", // teal-600
  "#c026d3", // fuchsia-600
  "#475569", // slate-500
  "#b91c1c", // red-700
  "#1d4ed8", // blue-700
];

/**
 * Simple string hash to deterministically map an ontology ID to a colour index.
 */
function hashString(s: string): number {
  let hash = 0;
  for (let i = 0; i < s.length; i++) {
    hash = (hash * 31 + s.charCodeAt(i)) | 0;
  }
  return Math.abs(hash);
}

/**
 * Assign a stable colour to each ontology ID based on a hash of its name,
 * so the colour never changes regardless of which ontologies are present.
 */
export function getOntologyColor(
  ontologyId: string,
  _ontologyOrder?: string[]
): string {
  return ONTOLOGY_COLORS[hashString(ontologyId) % ONTOLOGY_COLORS.length];
}

/**
 * Deduplicate entities by (start, end, term_iri) — the backend may return
 * duplicates when filtering is involved.
 */
export function deduplicateEntities(entities: TaggedEntity[]): TaggedEntity[] {
  const seen = new Set<string>();
  return entities.filter((e) => {
    const key = `${e.start}:${e.end}:${e.term_iri}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
