import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import Header from "../components/Header";
import TagTextBox from "../components/TagTextBox";

export default function TagTextPage() {
  const [searchParams] = useSearchParams();
  const textParam = searchParams.get("text") || "";
  const ontologyParam = searchParams.get("ontologyId") || "";

  const initialOntologyIds = ontologyParam
    ? ontologyParam.split(",").filter(Boolean)
    : undefined;

  document.title = "Tag Text - Ontology Lookup Service (OLS)";

  return (
    <div>
      <Header section="tag-text" />
      <main className="container mx-auto px-4 h-fit my-8">
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-neutral-800 mb-2">
            Text Tagger
          </h1>
          <p className="text-neutral-500">
            Annotate free text with ontology terms. Paste or type text below, or
            drag &amp; drop a text file. Select ontologies on the right to filter and
            prioritise results.
          </p>
        </div>
        <TagTextBox
          compact={false}
          initialText={textParam}
          initialOntologyIds={initialOntologyIds}
        />
      </main>
    </div>
  );
}
