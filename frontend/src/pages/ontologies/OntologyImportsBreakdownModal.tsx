import { Close } from "@mui/icons-material";
import { Fragment, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
} from "recharts";
import { getPaginated } from "../../app/api";
import LoadingOverlay from "../../components/LoadingOverlay";
import Ontology from "../../model/Ontology";

// Distinct palette (cycled for additional slices).
const PALETTE = [
  "#003a6b",
  "#0073e6",
  "#5ba300",
  "#b51963",
  "#e6308a",
  "#89ce00",
  "#00b4cb",
  "#ff9800",
  "#9c27b0",
  "#f44336",
  "#795548",
  "#607d8b",
];

interface Slice {
  ontologyId: string;
  count: number;
  isThisOntology: boolean;
}

export default function OntologyImportsBreakdownModal({
  ontology,
  onClose,
}: {
  ontology: Ontology;
  onClose: () => void;
}) {
  const ontologyId = ontology.getOntologyId();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [counts, setCounts] = useState<Record<string, number>>({});

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getPaginated<any>(
      `api/v2/ontologies/${ontologyId}/classes?size=1&facetFields=definedBy`
    )
      .then((page) => {
        if (cancelled) return;
        const facets = (page.facetFieldsToCounts as any) || {};
        const definedBy = facets.definedBy || {};
        setCounts(definedBy);
        setLoading(false);
      })
      .catch((e) => {
        if (cancelled) return;
        setError(e.message || String(e));
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [ontologyId]);

  const slices: Slice[] = useMemo(() => {
    const entries = Object.entries(counts).map(([ontId, count]) => ({
      ontologyId: ontId,
      count: count as number,
      isThisOntology: ontId === ontologyId,
    }));
    entries.sort((a, b) => {
      if (a.isThisOntology) return -1;
      if (b.isThisOntology) return 1;
      return b.count - a.count;
    });
    return entries;
  }, [counts, ontologyId]);

  const grandTotal = useMemo(
    () => slices.reduce((acc, s) => acc + s.count, 0),
    [slices]
  );

  // Close on Escape
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const colorFor = (s: Slice, index: number) =>
    s.isThisOntology ? "#1f3a6e" : PALETTE[index % PALETTE.length];

  const chartData = slices.map((s, i) => ({
    name: s.isThisOntology
      ? `${s.ontologyId.toUpperCase()} (defined here)`
      : s.ontologyId.toUpperCase(),
    value: s.count,
    ontologyId: s.ontologyId,
    isThisOntology: s.isThisOntology,
    color: colorFor(s, i),
  }));

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-lg shadow-xl w-full max-w-4xl max-h-[90vh] overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Imports breakdown"
      >
        <div className="flex items-center justify-between px-6 py-4 border-b">
          <h2 className="text-xl font-bold">
            Term sources in {ontologyId.toUpperCase()}
          </h2>
          <button
            type="button"
            className="link-default p-1"
            onClick={onClose}
            aria-label="Close"
          >
            <Close />
          </button>
        </div>
        <div className="flex-1 overflow-auto p-6">
          {loading && (
            <div className="relative h-64">
              <LoadingOverlay message="Loading…" />
            </div>
          )}
          {error && (
            <div className="text-red-700">Failed to load: {error}</div>
          )}
          {!loading && !error && slices.length === 0 && (
            <div className="text-neutral-600">
              No source ontology information available for the terms in this
              ontology.
            </div>
          )}
          {!loading && !error && slices.length > 0 && (
            <Fragment>
              <div style={{ width: "100%", height: 360 }}>
                <ResponsiveContainer>
                  <PieChart>
                    <Pie
                      data={chartData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      outerRadius={130}
                      isAnimationActive={false}
                      labelLine={false}
                      label={(d: any) =>
                        grandTotal > 0 && d.value / grandTotal >= 0.03
                          ? `${d.name}`
                          : ""
                      }
                    >
                      {chartData.map((entry) => (
                        <Cell
                          key={entry.ontologyId}
                          fill={entry.color}
                          stroke="#fff"
                        />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(value: any, name: any) => [
                        `${value.toLocaleString()} (${
                          grandTotal > 0
                            ? ((value / grandTotal) * 100).toFixed(1)
                            : "0"
                        }%)`,
                        name,
                      ]}
                    />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <table className="w-full mt-4 text-sm">
                <thead>
                  <tr className="border-b text-left">
                    <th className="py-2">Ontology</th>
                    <th className="py-2 text-right">Terms</th>
                    <th className="py-2 text-right">%</th>
                  </tr>
                </thead>
                <tbody>
                  {slices.map((s, i) => (
                    <tr key={s.ontologyId} className="border-b">
                      <td className="py-1">
                        <Link
                          to={"/ontologies/" + s.ontologyId}
                          onClick={onClose}
                        >
                          <span
                            className="px-2 py-1 rounded-md text-xs text-white uppercase mr-1"
                            style={{ backgroundColor: colorFor(s, i) }}
                            title={s.ontologyId.toUpperCase()}
                          >
                            {s.ontologyId}
                          </span>
                        </Link>
                        {s.isThisOntology && (
                          <span className="italic text-neutral-600">
                            defined here
                          </span>
                        )}
                      </td>
                      <td className="py-1 text-right">
                        {s.count.toLocaleString()}
                      </td>
                      <td className="py-1 text-right">
                        {grandTotal > 0
                          ? ((s.count / grandTotal) * 100).toFixed(1)
                          : "0.0"}
                        %
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Fragment>
          )}
        </div>
      </div>
    </div>
  );
}
