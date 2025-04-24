import { TypedUseSelectorHook, useSelector, useDispatch } from "react-redux";
import type { RootState, AppDispatch } from "./store";
import { useEffect, useState, useCallback } from 'react';

export const useAppDispatch = () => useDispatch<AppDispatch>();
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;

// Graph data interfaces
export interface GraphNode {
    iri: string;
    label: string;
    description?: string;
    type: string;
    ontology_name: string;
    is_obsolete: boolean;
    has_children: boolean;
}

export interface GraphEdge {
    source: string;
    target: string;
    label: string;
    uri?: string;
}

export interface GraphData {
    nodes: GraphNode[];
    edges: GraphEdge[];
}

// Custom hook for fetching ontology graph data
export function useOntologyGraph(ontologyId: string, iri: string | undefined) {
    const [graphData, setGraphData] = useState<GraphData | null>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);

    // Load initial graph data
    useEffect(() => {
        const fetchGraphData = async () => {
            if (!iri) return;

            setLoading(true);
            setError(null);

            try {
                const apiUrl = `${process.env.REACT_APP_APIURL}api/ontologies/${ontologyId}/terms/${encodeURIComponent(encodeURIComponent(iri))}/graph`;
                const response = await fetch(apiUrl);

                if (!response.ok) {
                    throw new Error(`HTTP error ${response.status}`);
                }

                const data = await response.json();
                setGraphData(data);
            } catch (err) {
                console.error("Error fetching graph data:", err);
                setError(err instanceof Error ? err.message : "Failed to load graph data");
            } finally {
                setLoading(false);
            }
        };

        if (iri) {
            fetchGraphData();
        }
    }, [ontologyId, iri]);

    // Function to fetch connections for a specific node
    const fetchNodeConnections = useCallback(async (nodeIri: string) => {
        if (!nodeIri) return false;

        setLoading(true);
        setError(null);

        try {
            // API endpoint to get expanded node data
            const apiUrl = `${process.env.REACT_APP_APIURL}api/ontologies/${ontologyId}/terms/${encodeURIComponent(encodeURIComponent(nodeIri))}/graph`;
            const response = await fetch(apiUrl);

            if (!response.ok) {
                throw new Error(`HTTP error ${response.status}`);
            }

            const newData = await response.json();

            // Update graph data by merging the new connections
            setGraphData(prevData => {
                if (!prevData) return newData;

                // Create sets of existing node IRIs and edge combinations to avoid duplicates
                const existingNodeIris = new Set(prevData.nodes.map(node => node.iri));
                const existingEdgeKeys = new Set();

                prevData.edges.forEach(edge => {
                    existingEdgeKeys.add(`${edge.source}|${edge.target}|${edge.label}`);
                });

                // Filter out nodes that already exist
                const newNodes = newData.nodes.filter(node =>
                    !existingNodeIris.has(node.iri)
                );

                // Filter out edges that already exist
                const newEdges = newData.edges.filter(edge => {
                    const edgeKey = `${edge.source}|${edge.target}|${edge.label}`;
                    return !existingEdgeKeys.has(edgeKey);
                });

                // Return merged data
                return {
                    nodes: [...prevData.nodes, ...newNodes],
                    edges: [...prevData.edges, ...newEdges]
                };
            });

            return true; // Indicate success
        } catch (err) {
            console.error("Error expanding node:", err);
            setError(err instanceof Error ? err.message : "Failed to expand node");
            return false;
        } finally {
            setLoading(false);
        }
    }, [ontologyId]);

    return { graphData, loading, error, fetchNodeConnections };
}