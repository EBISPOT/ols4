import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  ThemeProvider,
} from "@mui/material";
import { get, getPaginated } from "../app/api";
import { theme } from "../app/mui";
import Model from "../model/Model";

interface ClassifyEntity {
  iri: string;
  ontologyId?: string | string[];
  label?: any;
  shortForm?: string | string[];
  curie?: string | string[];
  type?: string | string[];
  score?: number;
  numberOfClasses?: string | number;
  numberOfEntities?: string | number;
  // Ontology-specific
  title?: any;
  preferredPrefix?: string;
  [key: string]: any;
}

function pickString(v: any): string {
  if (v === undefined || v === null) return "";
  if (typeof v === "string") return v;
  if (Array.isArray(v)) {
    for (const x of v) {
      const s = pickString(x);
      if (s) return s;
    }
    return "";
  }
  if (typeof v === "object") {
    if (typeof v.value === "string") return v.value;
  }
  return String(v);
}

export default function ClassifyBox({ compact = false }: { compact?: boolean }) {
  const [inputText, setInputText] = useState("");
  const [submittedText, setSubmittedText] = useState("");
  const [availableModels, setAvailableModels] = useState<Model[]>([]);
  const [selectedModel, setSelectedModel] = useState<string>("");
  const [ontologies, setOntologies] = useState<ClassifyEntity[] | null>(null);
  const [parents, setParents] = useState<ClassifyEntity[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load models (only those that can do live embedding)
  useEffect(() => {
    (async () => {
      try {
        const models = await get<Model[]>("api/v2/llm_models");
        const embeddable = (models || []).filter((m) => m.can_embed);
        setAvailableModels(embeddable);
        if (embeddable.length > 0) {
          setSelectedModel(embeddable[0].model);
        }
      } catch (e) {
        // ignore
      }
    })();
  }, []);

  const runClassify = async (q: string, model: string) => {
    if (!q || !model) return;
    setSubmittedText(q);
    setLoading(true);
    setError(null);
    setOntologies(null);
    setParents(null);
    try {
      const params = new URLSearchParams({ q, model, size: "10" });
      const centroidParams = new URLSearchParams({ q, model, size: "50", vector: "centroid" });
      const [ontPage, parentPage] = await Promise.all([
        getPaginated<ClassifyEntity>(`api/v2/ontologies/llm_search?${params}`),
        getPaginated<ClassifyEntity>(`api/v2/classes/llm_search?${centroidParams}`),
      ]);
      setOntologies(ontPage.elements);
      // Only show parents with >=3 descendants
      const filtered = parentPage.elements.filter((e) => {
        const n = Number(
          Array.isArray((e as any).numDescendants)
            ? (e as any).numDescendants[0]
            : (e as any).numDescendants
        );
        return Number.isFinite(n) && n >= 3;
      });
      setParents(filtered.slice(0, 10));
    } catch (e: any) {
      setError(e.message || "Classification failed");
    } finally {
      setLoading(false);
    }
  };

  const scheduleClassify = (text: string, model: string) => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!text.trim() || !model) {
      setOntologies(null);
      setParents(null);
      setError(null);
      return;
    }
    debounceRef.current = setTimeout(() => runClassify(text.trim(), model), 600);
  };

  return (
    <div>
      <div className="flex flex-col gap-2 mb-4">
        <input
          type="text"
          className="input-default w-full"
          placeholder="Enter string to predict classification"
          value={inputText}
          onChange={(e) => { setInputText(e.target.value); scheduleClassify(e.target.value, selectedModel); }}
        />
        <div className="flex flex-row gap-2 items-center mt-2">
          <ThemeProvider theme={theme}>
            <FormControl size="small" sx={{ minWidth: 220 }}>
              <InputLabel id="classify-model-label">Embedding Model</InputLabel>
              <Select
                labelId="classify-model-label"
                value={selectedModel}
                label="Embedding Model"
                onChange={(e) => { setSelectedModel(e.target.value as string); scheduleClassify(inputText, e.target.value as string); }}
              >
                {availableModels.map((m) => (
                  <MenuItem key={m.model} value={m.model}>
                    {m.model}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </ThemeProvider>
          {loading && <span className="text-sm text-neutral-default italic">Classifying...</span>}
        </div>
      </div>

      {!compact && (
        <div className="text-neutral-black text-sm mb-4">
          Examples:&nbsp;
          <button
            className="link-default"
            onClick={() => setInputText("type 2 diabetes mellitus")}
          >
            type 2 diabetes mellitus
          </button>
          ,&nbsp;
          <button
            className="link-default"
            onClick={() => setInputText("blood sample from a healthy donor")}
          >
            blood sample from a healthy donor
          </button>
        </div>
      )}

      {error && (
        <div className="text-red-default mb-4">{error}</div>
      )}

      {(ontologies || parents) && (
        <div className="mb-2 text-sm text-neutral-default">
          Results for: <span className="italic">{submittedText}</span> &middot;
          model: <code>{selectedModel}</code>
        </div>
      )}

      {(ontologies || parents || loading) && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <div className="text-xl font-bold mb-2">Predicted ontologies</div>
            <OntologyResultsTable rows={ontologies} loading={loading} />
          </div>
          <div>
            <div className="text-xl font-bold mb-2">Predicted parents</div>
            <TermResultsTable rows={parents} loading={loading} />
          </div>
        </div>
      )}
    </div>
  );
}

function ScoreCell({ score }: { score?: number }) {
  if (score === undefined || score === null) return <span>-</span>;
  return <span>{(score * 100).toFixed(1)}%</span>;
}

function OntologyResultsTable({
  rows,
  loading,
}: {
  rows: ClassifyEntity[] | null;
  loading: boolean;
}) {
  if (loading && !rows) return <div className="italic">Loading...</div>;
  if (!rows) return <div className="italic text-neutral-default">Enter a string above and press Classify.</div>;
  if (rows.length === 0)
    return <div className="italic">No predicted ontologies.</div>;
  return (
    <table className="w-full text-sm border border-neutral-light">
      <thead className="bg-neutral-light/50">
        <tr>
          <th className="text-left px-2 py-1">Ontology</th>
          <th className="text-right px-2 py-1 w-20">Score</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => {
          const ontologyId =
            (Array.isArray(r.ontologyId) ? r.ontologyId[0] : r.ontologyId) ||
            r.preferredPrefix ||
            "";
          const label =
            pickString(r.label) ||
            pickString(r.title) ||
            ontologyId;
          return (
            <tr
              key={ontologyId || r.iri}
              className="hover:bg-yellow-100 cursor-pointer border-t border-neutral-light"
            >
              <td className="px-2 py-1">
                <Link
                  className="link-default block"
                  to={`/ontologies/${ontologyId.toLowerCase()}`}
                >
                  {label}
                  {ontologyId && (
                    <span
                      className="link-ontology px-2 py-0.5 rounded-md text-xs text-white uppercase ml-2"
                      title={ontologyId.toUpperCase()}
                    >
                      {ontologyId}
                    </span>
                  )}
                </Link>
              </td>
              <td className="px-2 py-1 text-right">
                <ScoreCell score={r.score} />
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function TermResultsTable({
  rows,
  loading,
}: {
  rows: ClassifyEntity[] | null;
  loading: boolean;
}) {
  if (loading && !rows) return <div className="italic">Loading...</div>;
  if (!rows) return <div className="italic text-neutral-default">Enter a string above and press Classify.</div>;
  if (rows.length === 0)
    return <div className="italic">No predicted parents.</div>;
  return (
    <table className="w-full text-sm border border-neutral-light">
      <thead className="bg-neutral-light/50">
        <tr>
          <th className="text-left px-2 py-1">Term</th>
          <th className="text-right px-2 py-1 w-20">Score</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => {
          const ontologyId =
            (Array.isArray(r.ontologyId) ? r.ontologyId[0] : r.ontologyId) || "";
          const label = pickString(r.label) || r.iri;
          const typeArr = Array.isArray(r.type) ? r.type : r.type ? [r.type] : [];
          // Determine plural for the link path
          let typePlural = "classes";
          if (typeArr.includes("property")) typePlural = "properties";
          else if (typeArr.includes("individual")) typePlural = "individuals";
          const href = ontologyId
            ? `/ontologies/${ontologyId.toLowerCase()}/${typePlural}/${encodeURIComponent(
                encodeURIComponent(r.iri)
              )}`
            : undefined;
          return (
            <tr
              key={`${r.iri}-${i}`}
              className="hover:bg-yellow-100 cursor-pointer border-t border-neutral-light"
            >
              <td className="px-2 py-1">
                {href ? (
                  <Link className="link-default block" to={href}>
                    {label}
                    {ontologyId && (
                      <span
                        className="link-ontology px-2 py-0.5 rounded-md text-xs text-white uppercase ml-2"
                        title={ontologyId.toUpperCase()}
                      >
                        {ontologyId}
                      </span>
                    )}
                  </Link>
                ) : (
                  <span>{label}</span>
                )}
              </td>
              <td className="px-2 py-1 text-right">
                <ScoreCell score={r.score} />
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
