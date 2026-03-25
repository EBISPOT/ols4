import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useDropzone } from "react-dropzone";
import { Link } from "react-router-dom";
import {
  TaggedEntity,
  TagTextResponse,
  tagText,
  computeSegments,
  getOntologyColor,
  deduplicateEntities,
  TextSegment,
  getCurationSources,
} from "../app/tagTextApi";

// ─── Debounce delay for auto-tagging (ms) ────────────────────────────────────
const AUTO_TAG_DELAY = 600;

// ─── Props ───────────────────────────────────────────────────────────────────

interface TagTextBoxProps {
  /** If true, render a simplified version for the homepage (no submit button) */
  compact?: boolean;
  /** Initial text to populate (e.g. from URL state) */
  initialText?: string;
  /** Initial ontology priority list */
  initialOntologyIds?: string[];
}

export default function TagTextBox({
  compact = false,
  initialText = "",
  initialOntologyIds,
}: TagTextBoxProps) {
  // ─── State ───────────────────────────────────────────────────────────
  const [inputText, setInputText] = useState(initialText);
  const [tagResult, setTagResult] = useState<TagTextResponse | null>(null);
  const [allEntities, setAllEntities] = useState<TaggedEntity[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hoveredEntityKey, setHoveredEntityKey] = useState<string | null>(null);
  const [hoveredOntologyId, setHoveredOntologyId] = useState<string | null>(
    null
  );
  const [ontologySearch, setOntologySearch] = useState("");
  const [ontologyPriority, setOntologyPriority] = useState<string[]>(
    initialOntologyIds || []
  );
  const [showInput, setShowInput] = useState(true);
  const [minLength, setMinLength] = useState(6);
  const [includeSubstrings, setIncludeSubstrings] = useState(false);

  // Track excluded entities (unticked in the terms table)
  const [excludedEntityKeys, setExcludedEntityKeys] = useState<Set<string>>(new Set());

  // Track starred table rows (by group key: matchedText_lower:term_iri:ontology_id)
  const [starredRows, setStarredRows] = useState<Set<string>>(new Set());

  // Track blacklisted ontologies (completely hidden from results)
  const [blacklistedOntologies, setBlacklistedOntologies] = useState<Set<string>>(new Set());

  // Track disabled sources (all enabled by default)
  const [disabledSources, setDisabledSources] = useState<Set<string>>(new Set());

  // Available sources fetched from the backend (independent of tag results)
  const [availableSources, setAvailableSources] = useState<string[]>([]);

  // Track the "full" result (no filtering) so we always have the complete ontology list
  const [fullEntities, setFullEntities] = useState<TaggedEntity[]>([]);

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // ─── Derived: ontology order & counts (from full unfiltered results) ──
  const ontologyOrder = useMemo(() => {
    const order: string[] = [];
    for (const e of fullEntities) {
      if (!order.includes(e.ontology_id)) order.push(e.ontology_id);
    }
    return order.sort();
  }, [fullEntities]);

  const ontologyCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const e of fullEntities) {
      counts[e.ontology_id] = (counts[e.ontology_id] || 0) + 1;
    }
    return counts;
  }, [fullEntities]);

  // When an ontology has exactly 1 hit, store its matched text for display
  const ontologySingleHitLabel = useMemo(() => {
    const labels: Record<string, string> = {};
    const seen: Record<string, TaggedEntity | null> = {};
    for (const e of fullEntities) {
      if (seen[e.ontology_id] === undefined) {
        seen[e.ontology_id] = e;
      } else {
        seen[e.ontology_id] = null; // more than one hit
      }
    }
    for (const [ontId, entity] of Object.entries(seen)) {
      if (entity !== null) {
        labels[ontId] = entity.term_label;
      }
    }
    return labels;
  }, [fullEntities]);

  // ─── Fetch available sources from backend on mount ─────────
  useEffect(() => {
    getCurationSources().then((ds) => setAvailableSources(ds));
  }, []);

  // Build the list of enabled sources to pass to the API
  const enabledSources = useMemo(() => {
    if (availableSources.length === 0) return undefined;
    if (disabledSources.size === 0) return undefined; // all enabled = no filter
    const enabled = availableSources.filter((ds) => !disabledSources.has(ds));
    return enabled;
  }, [availableSources, disabledSources]);

  // ─── Filter out blacklisted ontologies ─────────────────────────────
  const visibleEntities = useMemo(
    () => allEntities.filter((e) => {
      if (blacklistedOntologies.has(e.ontology_id)) return false;
      return true;
    }),
    [allEntities, blacklistedOntologies]
  );

  // ─── Segments for rendering ────────────────────────────────────────
  const displayText = tagResult?.text || inputText;
  const segments = useMemo(
    () => computeSegments(displayText, visibleEntities),
    [displayText, visibleEntities]
  );

  // ─── Auto-tag on every input change (debounced) ────────────────────
  const performTagging = useCallback(
    async (text: string, priorityIds: string[], ml: number = minLength, inclSub: boolean = includeSubstrings, forceRefresh: boolean = false) => {
      if (!text.trim()) {
        setTagResult(null);
        setAllEntities([]);
        setError(null);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        // If we have no full result yet (first tag) or params changed, do an unfiltered call
        if (forceRefresh || fullEntities.length === 0 || priorityIds.length === 0) {
          const fullRes = await tagText(text, undefined, ml, inclSub, enabledSources);
          const deduped = deduplicateEntities(fullRes.entities);
          setFullEntities(deduped);

          if (priorityIds.length === 0) {
            setTagResult(fullRes);
            setAllEntities(deduped);
          } else {
            // Also do a filtered call
            const filteredRes = await tagText(text, priorityIds, ml, inclSub, enabledSources);
            setTagResult(filteredRes);
            setAllEntities(deduplicateEntities(filteredRes.entities));
          }
        } else {
          // We already have the full ontology list, just do filtered
          const res = await tagText(
            text,
            priorityIds.length > 0 ? priorityIds : undefined,
            ml,
            inclSub,
            enabledSources
          );
          const deduped = deduplicateEntities(res.entities);
          setTagResult(res);
          setAllEntities(deduped);

          // If unfiltered, also update fullEntities
          if (priorityIds.length === 0) {
            setFullEntities(deduped);
          }
        }
      } catch (err: any) {
        setError(err.message || "Failed to tag text");
      } finally {
        setLoading(false);
      }
    },
    [fullEntities.length, minLength, includeSubstrings, enabledSources]
  );

  // Debounced trigger on text change
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (inputText.trim()) {
      setLoading(true);
    }
    debounceRef.current = setTimeout(() => {
      performTagging(inputText, ontologyPriority);
    }, AUTO_TAG_DELAY);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [inputText]); // eslint-disable-line react-hooks/exhaustive-deps

  // Re-tag when priority changes (but only if we have text)
  useEffect(() => {
    if (inputText.trim()) {
      performTagging(inputText, ontologyPriority);
    }
  }, [ontologyPriority]); // eslint-disable-line react-hooks/exhaustive-deps

  // Re-tag when minLength, includeSubstrings, or source selection change
  useEffect(() => {
    if (inputText.trim()) {
      performTagging(inputText, ontologyPriority, minLength, includeSubstrings, true);
    }
  }, [minLength, includeSubstrings, enabledSources]); // eslint-disable-line react-hooks/exhaustive-deps

  // ─── Drag & drop for text files ────────────────────────────────────
  const onDrop = useCallback(
    (acceptedFiles: File[]) => {
      if (acceptedFiles.length === 0) return;
      const file = acceptedFiles[0];
      const reader = new FileReader();
      reader.onload = () => {
        const content = reader.result as string;
        setInputText(content);
        setShowInput(true);
        // Reset full entities so next tag is unfiltered
        setFullEntities([]);
        setOntologyPriority([]);
      };
      reader.readAsText(file);
    },
    []
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { "text/plain": [".txt", ".csv", ".tsv", ".md", ".text"] },
    multiple: false,
    noClick: true, // Don't open file dialog on click (we have a textarea)
    noKeyboard: true, // Don't interfere with textarea keyboard events
  });

  // ─── Hover handlers ────────────────────────────────────────────────
  const makeEntityKey = (e: TaggedEntity) =>
    `${e.start}:${e.end}:${e.term_iri}`;

  const handleSegmentMouseEnter = (seg: TextSegment) => {
    if (seg.entities.length > 0) {
      // Highlight all entities at this position
      setHoveredEntityKey(
        seg.entities.map(makeEntityKey).join("|")
      );
      // Highlight all ontologies at this position
      setHoveredOntologyId(
        seg.entities.map((e) => e.ontology_id).join("|")
      );
    }
  };

  const handleSegmentMouseLeave = () => {
    setHoveredEntityKey(null);
    setHoveredOntologyId(null);
  };

  const handleOntologyHover = (ontId: string | null) => {
    setHoveredOntologyId(ontId);
  };

  // ─── Priority drag reordering ─────────────────────────────────────
  const dragItem = useRef<number | null>(null);
  const dragOverItem = useRef<number | null>(null);

  const handleDragStart = (idx: number) => {
    dragItem.current = idx;
  };

  const handleDragEnter = (idx: number) => {
    dragOverItem.current = idx;
  };

  const handleDragEnd = () => {
    if (dragItem.current === null || dragOverItem.current === null) return;
    const items = [...ontologyPriority];
    const draggedItem = items.splice(dragItem.current, 1)[0];
    items.splice(dragOverItem.current, 0, draggedItem);
    dragItem.current = null;
    dragOverItem.current = null;
    setOntologyPriority(items);
  };

  // ─── Toggle ontology in/out of priority list ─────────────────────
  const toggleOntology = (ontId: string) => {
    setOntologyPriority((prev) => {
      if (prev.includes(ontId)) {
        return prev.filter((id) => id !== ontId);
      } else {
        return [...prev, ontId];
      }
    });
  };

  // ─── Blacklist helpers ─────────────────────────────────────────────
  const blacklistOntology = (ontId: string) => {
    setBlacklistedOntologies((prev) => new Set(prev).add(ontId));
    setOntologyPriority((prev) => prev.filter((id) => id !== ontId));
  };

  const unblacklistOntology = (ontId: string) => {
    setBlacklistedOntologies((prev) => {
      const next = new Set(prev);
      next.delete(ontId);
      return next;
    });
  };

  // ─── CSV download (respects excluded rows & blacklist) ─────────────

  // ─── Deduplicated table rows ─────────────────────────────────────
  // Group all entities by (matched_text_lowercase, term_iri, ontology_id)
  // so the table shows each unique mapping only once.
  interface TableRow {
    matchedText: string;
    entity: TaggedEntity;
    entityKeys: string[];
  }

  const tableRows: TableRow[] = useMemo(() => {
    const groups = new Map<string, TableRow>();
    for (const e of visibleEntities) {
      const text = displayText.slice(e.start, e.end);
      const groupKey = `${text.toLowerCase()}:${e.term_iri}:${e.ontology_id}`;
      const eKey = makeEntityKey(e);
      const existing = groups.get(groupKey);
      if (existing) {
        existing.entityKeys.push(eKey);
        const types = new Set(existing.entity.string_types || [existing.entity.string_type || "LABEL"]);
        for (const t of (e.string_types || [e.string_type || "LABEL"])) types.add(t);
        existing.entity = { ...existing.entity, string_types: Array.from(types) };
        const srcs = new Set(existing.entity.sources || (existing.entity.source ? [existing.entity.source] : []));
        for (const s of (e.sources || (e.source ? [e.source] : []))) srcs.add(s);
        existing.entity = { ...existing.entity, sources: Array.from(srcs) };
      } else {
        groups.set(groupKey, { matchedText: text, entity: e, entityKeys: [eKey] });
      }
    }
    return Array.from(groups.values());
  }, [visibleEntities, displayText]);

  // Group table rows by matched text to detect duplicates (for star button)
  const matchedTextGroups = useMemo(() => {
    const groups: Record<string, number[]> = {};
    tableRows.forEach((row, idx) => {
      const textLower = row.matchedText.toLowerCase();
      if (!groups[textLower]) groups[textLower] = [];
      groups[textLower].push(idx);
    });
    return groups;
  }, [tableRows]);

  // Make a stable key for a table row
  const makeRowKey = (row: TableRow) =>
    `${row.matchedText.toLowerCase()}:${row.entity.term_iri}:${row.entity.ontology_id}`;

  // Star/unstar a table row: toggle starring; when starred, exclude all other rows
  // with the same matched text; when unstarred, restore them.
  const starRow = (rowIdx: number) => {
    const row = tableRows[rowIdx];
    const rowKey = makeRowKey(row);
    const textLower = row.matchedText.toLowerCase();
    const group = matchedTextGroups[textLower] || [];
    const isCurrentlyStarred = starredRows.has(rowKey);

    if (isCurrentlyStarred) {
      // Unstar: restore all rows in the group
      setStarredRows((prev) => { const next = new Set(prev); next.delete(rowKey); return next; });
      setExcludedEntityKeys((prev) => {
        const next = new Set(prev);
        for (const gIdx of group) {
          for (const k of tableRows[gIdx].entityKeys) next.delete(k);
        }
        return next;
      });
    } else {
      // Star: include this row, exclude all others in the group
      setStarredRows((prev) => {
        const next = new Set(prev);
        // Clear any other star in the same group
        for (const gIdx of group) next.delete(makeRowKey(tableRows[gIdx]));
        next.add(rowKey);
        return next;
      });
      setExcludedEntityKeys((prev) => {
        const next = new Set(prev);
        for (const gIdx of group) {
          const r = tableRows[gIdx];
          if (gIdx === rowIdx) {
            for (const k of r.entityKeys) next.delete(k);
          } else {
            for (const k of r.entityKeys) next.add(k);
          }
        }
        return next;
      });
    }
  };

  const downloadCSV = () => {
    if (tableRows.length === 0) return;
    const header = "Matched Text,Term IRI,Term Label,Ontology ID,Match Type,Source,Subject Categories\n";
    const rows = tableRows
      .filter((row) => !row.entityKeys.every((k) => excludedEntityKeys.has(k)))
      .map((row) => {
        const e = row.entity;
        const matchType = (e.string_types || [e.string_type || "LABEL"]).join("|");
        const source = (e.sources || (e.source ? [e.source] : [])).join("|");
        const categories = e.subject_categories?.join("|") || "";
        return `"${row.matchedText.replace(/"/g, '""')}","${e.term_iri}","${e.term_label.replace(/"/g, '""')}","${e.ontology_id}","${matchType}","${source}","${categories}"`;
      })
      .join("\n");
    const blob = new Blob([header + rows], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "tagged_terms.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  // ─── Filtered ontology list ────────────────────────────────────────
  const filteredOntologies = ontologyOrder.filter(
    (ontId) =>
      !blacklistedOntologies.has(ontId) &&
      ontId.toLowerCase().includes(ontologySearch.toLowerCase())
  );

  const filteredBlacklisted = ontologyOrder.filter(
    (ontId) =>
      blacklistedOntologies.has(ontId) &&
      ontId.toLowerCase().includes(ontologySearch.toLowerCase())
  );

  // ─── Render ────────────────────────────────────────────────────────
  return (
    <div className="w-full">
      {/* Text input area with drag-and-drop */}
      <div
        {...getRootProps()}
        className={`relative border-2 rounded-lg transition-colors ${
          isDragActive
            ? "border-link-default bg-blue-50 border-dashed"
            : "border-neutral-300"
        }`}
      >
        <input {...getInputProps()} />

        {isDragActive && (
          <div className="absolute inset-0 z-10 flex items-center justify-center bg-blue-50 bg-opacity-90 rounded-lg">
            <div className="text-link-default font-semibold text-lg">
              <i className="icon icon-common icon-upload mr-2" />
              Drop your text file here
            </div>
          </div>
        )}

        {showInput ? (
          <div className="relative">
            <textarea
              ref={textareaRef}
              className="w-full p-4 rounded-lg resize-y font-mono text-sm bg-transparent"
              style={{ minHeight: compact ? "120px" : "200px" }}
              placeholder="Paste or type text to tag with ontology terms, or drag & drop a text file..."
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
            />
            {inputText && (
              <button
                className="absolute top-2 right-2 text-neutral-400 hover:text-neutral-600 p-1"
                onClick={() => {
                  setInputText("");
                  setTagResult(null);
                  setAllEntities([]);
                  setFullEntities([]);
                  setOntologyPriority([]);
                  setError(null);
                }}
                title="Clear text"
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                </svg>
              </button>
            )}
          </div>
        ) : null}
      </div>

      {/* Min length slider + include substrings toggle */}
      <div className="flex flex-wrap items-center gap-4 mt-2 text-sm text-neutral-600">
        <label className="flex items-center gap-2">
          <span className="whitespace-nowrap">Min match length:</span>
          <input
            type="range"
            min={1}
            max={20}
            value={minLength}
            onChange={(e) => setMinLength(Number(e.target.value))}
            className="w-24 accent-link-default"
          />
          <span className="w-6 text-center font-medium">{minLength}</span>
        </label>
        <label className="flex items-center gap-2 cursor-pointer">
          <input
            type="checkbox"
            className="accent-link-default"
            checked={includeSubstrings}
            onChange={(e) => setIncludeSubstrings(e.target.checked)}
          />
          <span>Include substrings</span>
        </label>
      </div>

      {/* Source filters */}
      {availableSources.length > 0 && (
        <div className="mt-3 p-3 border border-neutral-200 rounded-lg bg-neutral-50">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-sm font-semibold text-neutral-600">Include previous curations from</span>
            <button
              className="text-xs text-link-default hover:underline"
              onClick={() => setDisabledSources(new Set())}
            >
              Select all
            </button>
            <button
              className="text-xs text-link-default hover:underline"
              onClick={() => setDisabledSources(new Set(availableSources))}
            >
              Select none
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            {availableSources.map((ds) => {
              const enabled = !disabledSources.has(ds);
              return (
                <button
                  key={ds}
                  className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-medium border transition-colors ${
                    enabled
                      ? "bg-link-default text-white border-link-default hover:bg-link-dark"
                      : "bg-white text-neutral-400 border-neutral-300 hover:border-neutral-400"
                  }`}
                  onClick={() => {
                    setDisabledSources((prev) => {
                      const next = new Set(prev);
                      if (next.has(ds)) {
                        next.delete(ds);
                      } else {
                        next.add(ds);
                      }
                      return next;
                    });
                  }}
                >
                  {ds}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="mt-2 p-3 bg-red-50 border border-red-200 rounded text-red-700 text-sm">
          {error}
        </div>
      )}

      {/* Tagged text display + ontology list */}
      {(tagResult && visibleEntities.length > 0) && (
        <div className={`mt-4 ${compact ? "" : "grid grid-cols-1 lg:grid-cols-4 lg:gap-6"}`}>
          {/* Tagged text panel */}
          <div className={compact ? "" : "lg:col-span-3"}>
            <div className="flex items-center justify-between mb-2">
              <div className="text-sm font-semibold text-neutral-600">
                Tagged Text
                {loading && (
                  <span className="inline-block ml-2 w-3 h-3 border-2 border-neutral-300 border-t-link-default rounded-full animate-spin align-middle" />
                )}
                <span className="ml-2 text-xs font-normal text-neutral-400">
                  ({visibleEntities.length} term{visibleEntities.length !== 1 ? "s" : ""} found
                  {ontologyPriority.length > 0 &&
                    ` · filtered to ${ontologyPriority.length} ontolog${ontologyPriority.length !== 1 ? "ies" : "y"}`}
                  )
                </span>
              </div>
              <div className="flex gap-2">
                <button
                  className="text-xs px-2 py-1 rounded border border-neutral-300 hover:bg-neutral-100 text-neutral-600"
                  onClick={() => setShowInput((v) => !v)}
                >
                  {showInput ? "Hide input" : "Show input"}
                </button>
              </div>
            </div>
            <div
              className="p-4 bg-white border border-neutral-200 rounded-lg text-sm leading-relaxed overflow-auto"
              style={{ maxHeight: compact ? "300px" : "500px" }}
            >
              {renderSegments(
                segments,
                ontologyOrder,
                hoveredEntityKey,
                hoveredOntologyId,
                handleSegmentMouseEnter,
                handleSegmentMouseLeave,
                makeEntityKey
              )}
            </div>

            {/* Submit link for compact mode */}
            {compact && inputText.trim() && (
              <div className="mt-3 text-right">
                <Link
                  to={`/tag-text?text=${encodeURIComponent(inputText)}${
                    ontologyPriority.length > 0
                      ? "&ontologyId=" + ontologyPriority.join(",")
                      : ""
                  }`}
                  className="inline-flex items-center gap-1 px-4 py-2 bg-link-default text-white rounded-md hover:bg-link-dark text-sm font-medium"
                >
                  Export terms →
                </Link>
              </div>
            )}
          </div>

          {/* Ontology list sidebar */}
          <div className={compact ? "mt-4" : "lg:col-span-1"}>
            <div className="text-sm font-semibold text-neutral-600 mb-2">
              Ontologies
              {ontologyPriority.length > 0 && (
                <button
                  className="ml-2 text-xs text-link-default hover:underline font-normal"
                  onClick={() => setOntologyPriority([])}
                >
                  Clear filter
                </button>
              )}
            </div>
            <div className="relative mb-2">
              <input
                type="text"
                className="input-default text-sm w-full pl-3"
                placeholder="Search ontologies..."
                value={ontologySearch}
                onChange={(e) => setOntologySearch(e.target.value)}
              />
              {ontologySearch && (
                <button
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-neutral-600"
                  onClick={() => setOntologySearch("")}
                >
                  <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                  </svg>
                </button>
              )}
            </div>

            {/* Priority ordering hint */}
            {ontologyPriority.length > 0 && (
              <div className="text-xs text-neutral-400 mb-1 italic">
                Drag selected ontologies to reorder priority
              </div>
            )}

            {/* Unified ontology list: selected (draggable) first, then unselected */}
            <div
              className="border border-neutral-200 rounded-md overflow-y-auto"
              style={{ maxHeight: compact ? "200px" : "400px" }}
            >
              {/* Selected ontologies (priority order, draggable) */}
              {ontologyPriority
                .filter((ontId) => filteredOntologies.includes(ontId))
                .map((ontId, idx) => (
                  <div
                    key={ontId}
                    draggable
                    onDragStart={() => handleDragStart(idx)}
                    onDragEnter={() => handleDragEnter(idx)}
                    onDragEnd={handleDragEnd}
                    onDragOver={(e) => e.preventDefault()}
                    className={`flex items-center gap-2 px-2 py-1.5 text-sm cursor-grab border-b border-neutral-100 last:border-b-0 transition-colors ${
                      hoveredOntologyId?.split("|").includes(ontId)
                        ? "bg-yellow-50"
                        : "bg-blue-50 hover:bg-blue-100"
                    }`}
                    onMouseEnter={() => handleOntologyHover(ontId)}
                    onMouseLeave={() => handleOntologyHover(null)}
                  >
                    <input
                      type="checkbox"
                      className="accent-link-default"
                      checked={true}
                      onChange={() => toggleOntology(ontId)}
                    />
                    <span className="text-neutral-300 cursor-grab text-xs">⠿</span>
                    <span className="text-xs text-neutral-400 w-4">{idx + 1}.</span>
                    <span
                      className="inline-block w-3 h-3 rounded-sm flex-shrink-0"
                      style={{
                        backgroundColor: getOntologyColor(ontId, ontologyOrder),
                      }}
                    />
                    <span className="uppercase font-medium flex-1">{ontId}</span>
                    <span className="text-xs text-neutral-400 truncate max-w-[120px]" title={ontologySingleHitLabel[ontId] || `${ontologyCounts[ontId] || 0} matches`}>
                      {ontologyCounts[ontId] === 1 && ontologySingleHitLabel[ontId]
                        ? ontologySingleHitLabel[ontId]
                        : <i>{ontologyCounts[ontId] || 0} matches</i>}
                    </span>
                    <button
                      className="ml-1 text-neutral-300 hover:text-red-500 flex-shrink-0"
                      title={`Blacklist ${ontId.toUpperCase()}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        blacklistOntology(ontId);
                      }}
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                      </svg>
                    </button>
                  </div>
                ))}

              {/* Unselected ontologies (sorted by count) */}
              {filteredOntologies
                .filter((ontId) => !ontologyPriority.includes(ontId))
                .sort((a, b) => (ontologyCounts[b] || 0) - (ontologyCounts[a] || 0))
                .map((ontId) => (
                  <label
                    key={ontId}
                    className={`flex items-center gap-2 px-2 py-1.5 text-sm cursor-pointer border-b border-neutral-100 last:border-b-0 transition-colors ${
                      hoveredOntologyId?.split("|").includes(ontId)
                        ? "bg-yellow-50"
                        : "hover:bg-neutral-50"
                    }`}
                    onMouseEnter={() => handleOntologyHover(ontId)}
                    onMouseLeave={() => handleOntologyHover(null)}
                  >
                    <input
                      type="checkbox"
                      className="accent-link-default"
                      checked={false}
                      onChange={() => toggleOntology(ontId)}
                    />
                    <span
                      className="inline-block w-3 h-3 rounded-sm flex-shrink-0"
                      style={{
                        backgroundColor: getOntologyColor(ontId, ontologyOrder),
                      }}
                    />
                    <span className="uppercase font-medium flex-1">{ontId}</span>
                    <span className="text-xs text-neutral-400 truncate max-w-[120px]" title={ontologySingleHitLabel[ontId] || `${ontologyCounts[ontId] || 0} matches`}>
                      {ontologyCounts[ontId] === 1 && ontologySingleHitLabel[ontId]
                        ? ontologySingleHitLabel[ontId]
                        : <i>{ontologyCounts[ontId] || 0} matches</i>}
                    </span>
                    <button
                      className="ml-1 text-neutral-300 hover:text-red-500 flex-shrink-0"
                      title={`Blacklist ${ontId.toUpperCase()}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        e.preventDefault();
                        blacklistOntology(ontId);
                      }}
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                      </svg>
                    </button>
                  </label>
                ))}
              {filteredOntologies.length === 0 && filteredBlacklisted.length === 0 && (
                <div className="text-sm text-neutral-400 p-3 text-center">
                  No ontologies match "{ontologySearch}"
                </div>
              )}

              {/* Blacklisted ontologies section */}
              {filteredBlacklisted.length > 0 && (
                <>
                  <div className="px-2 py-1.5 bg-neutral-100 border-t border-neutral-200 text-xs text-neutral-500 font-semibold uppercase tracking-wide flex items-center justify-between">
                    <span>Excluded ({filteredBlacklisted.length})</span>
                    <button
                      className="text-xs text-link-default hover:underline font-normal normal-case tracking-normal"
                      onClick={() => setBlacklistedOntologies(new Set())}
                    >
                      Restore all
                    </button>
                  </div>
                  {filteredBlacklisted.map((ontId) => (
                    <div
                      key={ontId}
                      className="flex items-center gap-2 px-2 py-1.5 text-sm border-b border-neutral-100 last:border-b-0 bg-neutral-50 opacity-60"
                    >
                      <span
                        className="inline-block w-3 h-3 rounded-sm flex-shrink-0"
                        style={{
                          backgroundColor: getOntologyColor(ontId, ontologyOrder),
                        }}
                      />
                      <span className="uppercase font-medium flex-1 line-through">{ontId}</span>
                      <span className="text-xs text-neutral-400">
                        <i>{ontologyCounts[ontId] || 0} matches</i>
                      </span>
                      <button
                        className="ml-1 text-neutral-400 hover:text-link-default flex-shrink-0"
                        title={`Restore ${ontId.toUpperCase()}`}
                        onClick={() => unblacklistOntology(ontId)}
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                          <path fillRule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z" clipRule="evenodd" />
                        </svg>
                      </button>
                    </div>
                  ))}
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Loading spinner */}
      {loading && (
        <div className="mt-6 flex items-center justify-center">
          <span className="inline-block w-6 h-6 border-2 border-neutral-300 border-t-link-default rounded-full animate-spin" />
        </div>
      )}

      {/* No results state */}
      {tagResult && visibleEntities.length === 0 && !loading && (
        <div className="mt-4 p-4 bg-neutral-50 border border-neutral-200 rounded-lg text-neutral-500 text-sm text-center">
          No ontology terms found in the provided text.
        </div>
      )}

      {/* Full-page extras: terms table + CSV download */}
      {!compact && tagResult && visibleEntities.length > 0 && (
        <div className="mt-6">
          <div className="flex items-center justify-between mb-3">
            <div className="text-lg font-semibold text-neutral-700">
              Matched Terms
              {excludedEntityKeys.size > 0 && (
                <span className="ml-2 text-xs font-normal text-neutral-400">
                  ({excludedEntityKeys.size} excluded)
                </span>
              )}
            </div>
            <button
              className="flex items-center gap-1 px-3 py-1.5 bg-link-default text-white rounded hover:bg-link-dark text-sm"
              onClick={downloadCSV}
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M3 17a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm3.293-7.707a1 1 0 011.414 0L9 10.586V3a1 1 0 112 0v7.586l1.293-1.293a1 1 0 111.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z" clipRule="evenodd" />
              </svg>
              Download CSV
            </button>
          </div>
          <div className="overflow-x-auto border border-neutral-200 rounded-lg">
            <table className="w-full text-sm">
              <thead className="bg-neutral-50 text-neutral-600">
                <tr>
                  <th className="text-left px-4 py-2 font-semibold">Matched Text</th>
                  <th className="text-left px-4 py-2 font-semibold">Term IRI</th>
                  <th className="text-left px-4 py-2 font-semibold">Term Label</th>
                  <th className="text-left px-4 py-2 font-semibold">Ontology</th>
                  <th className="text-left px-4 py-2 font-semibold">Match Type</th>
                  <th className="px-2 py-2 w-8" title="Include in CSV">
                    <input
                      type="checkbox"
                      className="accent-link-default"
                      checked={excludedEntityKeys.size === 0}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setExcludedEntityKeys(new Set());
                        } else {
                          setExcludedEntityKeys(new Set(tableRows.flatMap((r) => r.entityKeys)));
                        }
                      }}
                      title={excludedEntityKeys.size === 0 ? "Exclude all" : "Include all"}
                    />
                  </th>
                </tr>
              </thead>
              <tbody>
                {tableRows.map((row, i) => {
                  const { matchedText, entity, entityKeys } = row;
                  const isHovered = hoveredEntityKey !== null &&
                    entityKeys.some((k) => hoveredEntityKey.split("|").includes(k));
                  const isExcluded = entityKeys.every((k) => excludedEntityKeys.has(k));
                  const textLower = matchedText.toLowerCase();
                  const hasDuplicates = (matchedTextGroups[textLower]?.length || 0) > 1;
                  return (
                    <tr
                      key={i}
                      className={`border-t border-neutral-100 transition-colors ${
                        isExcluded ? "opacity-40" : ""
                      } ${
                        isHovered ? "bg-yellow-50" : "hover:bg-neutral-50"
                      }`}
                      onMouseEnter={() => {
                        setHoveredEntityKey(entityKeys.join("|"));
                        setHoveredOntologyId(entity.ontology_id);
                      }}
                      onMouseLeave={() => {
                        setHoveredEntityKey(null);
                        setHoveredOntologyId(null);
                      }}
                    >
                      <td className="px-4 py-2 font-medium">{matchedText}</td>
                      <td className="px-4 py-2">
                        <a
                          href={entity.term_iri}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="link-default text-xs break-all"
                        >
                          {entity.term_iri}
                        </a>
                      </td>
                      <td className="px-4 py-2">{entity.term_label}</td>
                      <td className="px-4 py-2">
                        <span className="inline-flex items-center gap-1">
                          <span
                            className="inline-block w-2.5 h-2.5 rounded-sm"
                            style={{
                              backgroundColor: getOntologyColor(
                                entity.ontology_id,
                                ontologyOrder
                              ),
                            }}
                          />
                          <span className="uppercase">{entity.ontology_id}</span>
                        </span>
                      </td>
                      <td className="px-4 py-2">
                        <span className="inline-flex items-center gap-1 flex-wrap">
                          {(entity.string_types || [entity.string_type || "LABEL"]).includes("LABEL") && (
                            <span className="inline-block px-1.5 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800">LABEL</span>
                          )}
                          {(entity.string_types || [entity.string_type || "LABEL"]).includes("CURATION") && (
                            <span
                              className="inline-block px-1.5 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-800 cursor-default"
                              title={(entity.sources || (entity.source ? [entity.source] : [])).join(", ")}
                            >CURATION</span>
                          )}
                        </span>
                      </td>
                      <td className="px-2 py-2 text-center">
                        <input
                          type="checkbox"
                          className="accent-link-default"
                          checked={!isExcluded}
                          onChange={() => {
                            setExcludedEntityKeys((prev) => {
                              const next = new Set(prev);
                              const allExcluded = entityKeys.every((k) => next.has(k));
                              for (const k of entityKeys) {
                                if (allExcluded) {
                                  next.delete(k);
                                } else {
                                  next.add(k);
                                }
                              }
                              return next;
                            });
                          }}
                        />
                      </td>
                      <td className="px-1 py-2 text-center">
                        {hasDuplicates && (
                          <button
                            className={`transition-colors leading-none ${
                              starredRows.has(makeRowKey(row))
                                ? "text-amber-500 hover:text-amber-600"
                                : "text-neutral-300 hover:text-amber-500"
                            }`}
                            title={starredRows.has(makeRowKey(row)) ? "Unstar this match" : "Pick this match and exclude others with the same text"}
                            onClick={() => starRow(i)}
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                              <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                            </svg>
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

// ─── Segment renderer with overlap visualization ──────────────────────────────

function renderSegments(
  segments: TextSegment[],
  ontologyOrder: string[],
  hoveredEntityKey: string | null,
  hoveredOntologyId: string | null,
  onMouseEnter: (seg: TextSegment) => void,
  onMouseLeave: () => void,
  makeEntityKey: (e: TaggedEntity) => string
) {
  return segments.map((seg, i) => {
    if (seg.entities.length === 0) {
      // Plain text
      return <span key={i}>{seg.text}</span>;
    }

    const isOverlap = seg.entities.length > 1;
    const primaryEntity = seg.entities[0];
    const primaryColor = getOntologyColor(
      primaryEntity.ontology_id,
      ontologyOrder
    );

    // Check if any entity in this segment is hovered
    const isHovered =
      hoveredEntityKey !== null &&
      seg.entities.some((e) => hoveredEntityKey.split("|").includes(makeEntityKey(e)));

    // Check if an ontology in this segment is hovered from the sidebar
    const isOntologyHovered =
      hoveredOntologyId !== null &&
      seg.entities.some((e) =>
        hoveredOntologyId.split("|").includes(e.ontology_id)
      );

    // Build tooltip showing all entities at this position
    const tooltipLines = seg.entities.map(
      (e) => `${e.term_label} (${e.ontology_id.toUpperCase()})`
    );
    const tooltipText = isOverlap
      ? `Overlapping terms:\n${tooltipLines.join("\n")}`
      : tooltipLines[0];

    if (isOverlap) {
      // Render overlapping segment with striped gradient pattern
      const colors = seg.entities.map((e) =>
        getOntologyColor(e.ontology_id, ontologyOrder)
      );
      const stripeWidth = 3; // px per stripe
      const gradientStops = colors.flatMap((c, idx) => [
        `${c}40 ${idx * stripeWidth}px`,
        `${c}40 ${(idx + 1) * stripeWidth}px`,
      ]);
      const gradient = `repeating-linear-gradient(135deg, ${gradientStops.join(", ")})`;

      return (
        <span
          key={i}
          className={`relative cursor-pointer rounded-sm transition-all ${
            isHovered || isOntologyHovered
              ? "ring-2 ring-yellow-400 ring-offset-1"
              : ""
          }`}
          style={{
            background: gradient,
            backgroundSize: `${colors.length * stripeWidth}px ${colors.length * stripeWidth}px`,
            padding: "1px 0",
            borderBottom: `2px solid ${primaryColor}`,
          }}
          title={tooltipText}
          onMouseEnter={() => onMouseEnter(seg)}
          onMouseLeave={onMouseLeave}
        >
          {seg.text}
          <span
            className="absolute -top-2 -right-1 inline-flex items-center justify-center w-3.5 h-3.5 text-white text-[9px] font-bold rounded-full"
            style={{ backgroundColor: "#6b7280" }}
            title={`${seg.entities.length} overlapping terms`}
          >
            {seg.entities.length}
          </span>
        </span>
      );
    }

    // Single entity segment
    return (
      <span
        key={i}
        className={`cursor-pointer rounded-sm transition-all ${
          isHovered || isOntologyHovered
            ? "ring-2 ring-yellow-400 ring-offset-1"
            : ""
        }`}
        style={{
          backgroundColor: `${primaryColor}30`,
          borderBottom: `2px solid ${primaryColor}`,
          padding: "1px 0",
        }}
        title={tooltipText}
        onMouseEnter={() => onMouseEnter(seg)}
        onMouseLeave={onMouseLeave}
      >
        {seg.text}
      </span>
    );
  });
}
