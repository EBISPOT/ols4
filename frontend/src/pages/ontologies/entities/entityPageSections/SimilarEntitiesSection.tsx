
import { Fragment, useEffect, useState } from "react";
import Entity from "../../../../model/Entity";
import { Typography, Select, MenuItem, FormControl, InputLabel, ThemeProvider } from "@mui/material";
import { getPaginated } from "../../../../app/api";
import LoadingOverlay from "../../../../components/LoadingOverlay";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Class from "../../../../model/Class";
import EntityLink from "../../../../components/EntityLink";
import LinkedEntities from "../../../../model/LinkedEntities";
import { Link, useSearchParams } from "react-router-dom";
import { Warning } from "@mui/icons-material";
import { theme } from "../../../../app/mui";

type SimilarResult = { entity:Entity, score:number }

export default function SimilarEntitiesSection({entity}:{entity:Entity}) {

    let [similar, setSimilar] = useState<any[]|null>(null);
    const [selectedModel, setSelectedModel] = useState<string>("");
    const [availableModels, setAvailableModels] = useState<string[]>([]);

    const [searchParams] = useSearchParams();
    let lang = searchParams.get("lang") || "en";

    useEffect(() => {
        setSimilar(null)
        const fetchSimilarEntities = async () => {
            // Fetch available models
            try {
                const modelsResponse = await fetch(`${process.env.REACT_APP_APIURL}api/v2/llm_models`);
                if (modelsResponse.ok) {
                    const modelsData = await modelsResponse.json();
                    if (modelsData && modelsData.length > 0) {
                        // Extract model names from the response
                        const modelNames = modelsData.map((m: any) => m.model);
                        setAvailableModels(modelNames);
                        if (!selectedModel || selectedModel === "") {
                            setSelectedModel(modelNames[0]);
                        }
                    }
                }
            } catch (error) {
                console.error("Error fetching models:", error);
            }

            if (!selectedModel || selectedModel === "") {
                // No model selected yet, wait for models to load
                return;
            }

            const modelParam = `?model=${selectedModel}`;
            let page = await getPaginated<any>(`api/v2/${entity.getTypePlural()}/${encodeURIComponent(encodeURIComponent(entity.getIri()))}/llm_similar${modelParam}`)
            setSimilar(page.elements.map((s) => new Class(s)))
        };

        if(entity && entity.getOntologyId() && (entity.getType() === 'class' || entity.getType() === 'property')) {
            fetchSimilarEntities();
        }

    }, [entity?.getIri(), selectedModel])

    if(!entity || (entity.getType() !== 'class' && entity.getType() !== 'property')) {
        return <Fragment/>
    }

    return <div>
        <div className="mb-3">
            <ThemeProvider theme={theme}>
                <FormControl sx={{ minWidth: 300 }} size="small">
                    <InputLabel id="similar-model-select-label">Embedding Model</InputLabel>
                    <Select
                        labelId="similar-model-select-label"
                        id="similar-model-select"
                        value={selectedModel}
                        label="Embedding Model"
                        onChange={(e) => setSelectedModel(e.target.value)}
                    >
                        {availableModels.map((model) => (
                            <MenuItem key={model} value={model}>
                                {model}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            </ThemeProvider>
        </div>
        { !similar && <i>Loading...</i> }
        { similar && similar.length === 0 && <p>No similar {entity.getTypePlural()} found</p> }
        { similar && similar.length > 0 && <Fragment>
            <PropertyValuesList
                values={similar.filter((otherEntity:Entity) => otherEntity.getIri() !== entity.getIri())}
                title="Predicted similar entities"
                renderValue={(otherEntity:Entity) => (
                    <Link
                        className="link-default"
                        to={`/ontologies/${otherEntity.getOntologyId()}/${
                            otherEntity.getTypePlural()
                        }/${encodeURIComponent(encodeURIComponent(otherEntity.getIri()))}?lang=${lang}`}
                    >
                        {otherEntity.getName()}
                        <span
                        className="link-ontology px-2 py-0.5 rounded-md text-sm text-white uppercase ml-1"
                        title={otherEntity.getOntologyId().toUpperCase()}
                        >
                        {otherEntity.getOntologyId()}
                        </span>
                    </Link>
                )}
                searchFilter={(otherEntity:Entity, searchQuery) => {
                    const name = otherEntity.getName()?.toLowerCase() || '';
                    const ontologyId = otherEntity.getOntologyId().toLowerCase();
                    const iri = otherEntity.getIri().toLowerCase();
                    return name.includes(searchQuery) || ontologyId.includes(searchQuery) || iri.includes(searchQuery);
                }}
            />
            <p className="text-xs text-gray-500 pt-2">
                <i className="icon icon-common icon-exclamation-triangle icon-spacer" />
                  Similarity results are derived from LLM embeddings and have not been manually curated. Model: <code>{selectedModel}</code>
            </p></Fragment>
        }
    </div>


}
