import { WarningAmber } from "@mui/icons-material";
import Ontology from "../model/Ontology";

interface FallbackWarningProps {
  ontology: Ontology | undefined;
}

export default function FallbackWarning({ ontology }: FallbackWarningProps) {
  const isFallbackVersion = ontology ? ontology.isFallback() : false;

  if (!ontology || !isFallbackVersion) {
    return null;
  }

  return (
    <div
      className="bg-yellow-100 border-l-4 border-yellow-500 text-yellow-700 p-4 my-4"
      role="alert"
    >
      <div className="flex">
        <div className="py-1">
          <WarningAmber className="mr-3" />
        </div>
        <div>
          <p className="font-bold">Outdated Ontology Warning</p>
          <p className="text-sm">
            OLS was unable to index this ontology, so the version you are viewing may not be up to date.
          </p>
        </div>
      </div>
    </div>
  );
}
