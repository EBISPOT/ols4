import { Close } from "@mui/icons-material";
import { useEffect, useRef, useState } from "react";
import { getPaginated } from "../app/api";
import Ontology from "../model/Ontology";

// Cache all ontologies once loaded
let cachedOntologies: Ontology[] | null = null;
let cachePromise: Promise<Ontology[]> | null = null;

async function getAllOntologies(): Promise<Ontology[]> {
  if (cachedOntologies) return cachedOntologies;
  if (cachePromise) return cachePromise;
  
  cachePromise = getPaginated<any>(`api/v2/ontologies?size=1000`).then((results) => {
    cachedOntologies = results.elements.map((obj: any) => new Ontology(obj));
    return cachedOntologies;
  });
  
  return cachePromise;
}

interface OntologyAutocompleteProps {
  value: string;
  onChange: (ontologyId: string) => void;
  placeholder?: string;
  className?: string;
}

export default function OntologyAutocomplete({
  value,
  onChange,
  placeholder = "e.g. efo, mondo...",
  className = "",
}: OntologyAutocompleteProps) {
  const [query, setQuery] = useState(value);
  const [allOntologies, setAllOntologies] = useState<Ontology[]>([]);
  const [suggestions, setSuggestions] = useState<Ontology[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [loading, setLoading] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);
  const dropdownRef = useRef<HTMLUListElement>(null);

  // Load all ontologies on mount
  useEffect(() => {
    setLoading(true);
    getAllOntologies()
      .then((ontologies) => {
        setAllOntologies(ontologies);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  // Sync external value changes
  useEffect(() => {
    setQuery(value);
  }, [value]);

  // Filter suggestions when query changes
  useEffect(() => {
    if (!query || query.length < 1) {
      setSuggestions([]);
      return;
    }

    const lowerQuery = query.toLowerCase();
    const filtered = allOntologies
      .filter((ont) => {
        const id = ont.getOntologyId().toLowerCase();
        const name = (ont.getName() || "").toLowerCase();
        return id.includes(lowerQuery) || name.includes(lowerQuery);
      })
      .sort((a, b) => {
        // Prioritize matches that start with the query
        const aId = a.getOntologyId().toLowerCase();
        const bId = b.getOntologyId().toLowerCase();
        const aStartsWithId = aId.startsWith(lowerQuery);
        const bStartsWithId = bId.startsWith(lowerQuery);
        if (aStartsWithId && !bStartsWithId) return -1;
        if (!aStartsWithId && bStartsWithId) return 1;
        return aId.localeCompare(bId);
      })
      .slice(0, 10);
    
    setSuggestions(filtered);
  }, [query, allOntologies]);

  const handleSelect = (ontology: Ontology) => {
    const ontologyId = ontology.getOntologyId();
    setQuery(ontologyId);
    onChange(ontologyId);
    setShowDropdown(false);
    setSelectedIndex(-1);
  };

  const handleClear = () => {
    setQuery("");
    onChange("");
    setSuggestions([]);
    inputRef.current?.focus();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!showDropdown || suggestions.length === 0) return;

    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelectedIndex((prev) => Math.min(prev + 1, suggestions.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelectedIndex((prev) => Math.max(prev - 1, 0));
    } else if (e.key === "Enter" && selectedIndex >= 0) {
      e.preventDefault();
      handleSelect(suggestions[selectedIndex]);
    } else if (e.key === "Escape") {
      setShowDropdown(false);
      setSelectedIndex(-1);
    }
  };

  return (
    <div className={`relative ${className}`}>
      <input
        ref={inputRef}
        type="text"
        autoComplete="off"
        placeholder={placeholder}
        className="input-default text-sm py-1 pl-3 pr-8 w-48"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setShowDropdown(true);
          setSelectedIndex(-1);
        }}
        onFocus={() => setShowDropdown(true)}
        onBlur={() => {
          // Delay to allow click on dropdown
          setTimeout(() => setShowDropdown(false), 200);
        }}
        onKeyDown={handleKeyDown}
      />
      {query && (
        <button
          className="absolute right-2 top-1/2 -translate-y-1/2 text-neutral-dark hover:text-neutral-black"
          onClick={handleClear}
          type="button"
        >
          <Close fontSize="small" />
        </button>
      )}
      {showDropdown && suggestions.length > 0 && (
        <ul
          ref={dropdownRef}
          className="absolute z-50 top-full left-0 mt-1 w-72 max-h-64 overflow-y-auto bg-white border border-neutral-light rounded-lg shadow-lg"
        >
          {suggestions.map((ontology, i) => (
            <li
              key={ontology.getOntologyId()}
              className={`py-2 px-3 cursor-pointer hover:bg-link-light ${
                i === selectedIndex ? "bg-link-light" : ""
              }`}
              onMouseDown={() => handleSelect(ontology)}
            >
              <div className="flex justify-between items-center gap-2">
                <div className="truncate flex-auto font-medium" title={ontology.getName() || ontology.getOntologyId()}>
                  {ontology.getName() || ontology.getOntologyId()}
                </div>
                <div className="flex-shrink-0">
                  <span className="bg-link-default px-2 py-0.5 rounded text-xs text-white uppercase">
                    {ontology.getOntologyId()}
                  </span>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      {showDropdown && loading && (
        <div className="absolute z-50 top-full left-0 mt-1 w-72 py-2 px-3 bg-white border border-neutral-light rounded-lg shadow-lg text-neutral-dark text-sm">
          Loading...
        </div>
      )}
    </div>
  );
}
