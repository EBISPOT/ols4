import { useState, useEffect, memo } from "react";
import { useNavigate } from "react-router-dom";

export interface EntityDetailsProps {
    ontologyId: string;
    entityIri: string | null;
    entityType: string;
    onExpandNode?: (nodeIri: string) => void;
    isNodeExpanded?: boolean;
}

function EntityDetailsComponent({
                                    ontologyId,
                                    entityIri,
                                    entityType,
                                    onExpandNode,
                                    isNodeExpanded = false
                                }: EntityDetailsProps) {
    const [entityDetails, setEntityDetails] = useState<any>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);
    const [lastFetchedIri, setLastFetchedIri] = useState<string | null>(null);
    const navigate = useNavigate();
    // Fetch entity details only when entityIri changes
    useEffect(() => {
        const fetchEntityDetails = async () => {
            if (!entityIri || !ontologyId || entityIri === lastFetchedIri) return;

            setLoading(true);
            setError(null);

            try {
                // Double encode the IRI as required by the API
                const doubleEncodedIri = encodeURIComponent(encodeURIComponent(entityIri));
                let fetchUrl = '';

                if(entityType === 'classes'){
                    fetchUrl = `${process.env.REACT_APP_APIURL}api/ontologies/${ontologyId}/terms/${doubleEncodedIri}`;
                } else if (entityType === 'individuals'){
                    fetchUrl = `${process.env.REACT_APP_APIURL}api/ontologies/${ontologyId}/individuals/${doubleEncodedIri}`;
                } else if (entityType === 'properties'){
                    fetchUrl = `${process.env.REACT_APP_APIURL}api/ontologies/${ontologyId}/properties/${doubleEncodedIri}`;
                } else {
                    throw new Error(`Invalid entity type: ${entityType}`);
                }

                const response = await fetch(fetchUrl);

                if (!response.ok) {
                    throw new Error(`Error fetching entity details: ${response.status}`);
                }

                const data = await response.json();
                setEntityDetails(data);
                setLastFetchedIri(entityIri);
            } catch (err) {
                console.error("Failed to fetch entity details:", err);
                setError(err instanceof Error ? err.message : "Failed to fetch entity details");
            } finally {
                setLoading(false);
            }
        };

        if (entityIri !== lastFetchedIri) {
            fetchEntityDetails();
        }
    }, [ontologyId, entityIri, entityType, lastFetchedIri]);

    const handleNavigateToEntity = () => {
        if (!entityDetails) return;

        let targetUrl = '';
        const doubleEncodedIri = encodeURIComponent(encodeURIComponent(entityIri ?? ""));

        if(entityType === 'classes'){
            targetUrl = `/ontologies/${ontologyId}/classes/${doubleEncodedIri}`;
        } else if (entityType === 'individuals'){
            targetUrl = `/ontologies/${ontologyId}/individuals/${doubleEncodedIri}`;
        } else if (entityType === 'properties'){
            targetUrl = `/ontologies/${ontologyId}/properties/${doubleEncodedIri}`;
        } else {
            throw new Error(`Invalid entity type: ${entityType}`);
        }
        // Navigate to the entity page
        navigate(targetUrl);
    };

    const handleExpandNode = () => {
        if (onExpandNode && entityIri && !isNodeExpanded) {
            onExpandNode(entityIri);
        }
    };

    if (loading) {
        return (
            <div className="p-4 border rounded-md bg-gray-50 animate-pulse">
                <div className="h-6 bg-gray-200 rounded w-1/3 mb-4"></div>
                <div className="h-4 bg-gray-200 rounded w-full mb-2"></div>
                <div className="h-4 bg-gray-200 rounded w-5/6"></div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="p-4 border border-red-300 rounded-md bg-red-50 text-red-700">
                <h3 className="font-semibold mb-1">Error loading entity details</h3>
                <p className="text-sm">{error}</p>
            </div>
        );
    }

    if (!entityDetails) {
        return (
            <div className="p-4 border rounded-md bg-gray-50">
                <p className="text-gray-500 italic">Select an entity to view details</p>
            </div>
        );
    }

    // Extract the required information
    const {
        label,
        description = [],
        synonyms = [],
        short_form,
        iri
    } = entityDetails;

    // Format description array into a string
    const formattedDescription = Array.isArray(description)
        ? description.join(' ')
        : description || "No description available";

    return (
        <div className="p-4 border rounded-md bg-white">
            <h2 className="text-xl font-semibold mb-4">{label}</h2>

            <div className="mb-4">
                <h3 className="font-semibold mb-1">Description:</h3>
                <p className="text-gray-700">{formattedDescription}</p>
            </div>

            {synonyms && synonyms.length > 0 && (
                <div className="mb-4">
                    <h3 className="font-semibold mb-1">Synonyms:</h3>
                    <div className="flex flex-wrap gap-1">
                        {synonyms.map((synonym: string, index: number) => (
                            <span
                                key={index}
                                className="inline-block bg-blue-100 text-blue-800 px-2 py-1 rounded-md text-sm"
                            >
                {synonym}
              </span>
                        ))}
                    </div>
                </div>
            )}

            <div className="mt-3 pt-3 border-t border-gray-200">
                <p className="text-gray-600 mb-3">
                    <span className="font-semibold">Short ID:</span> {short_form}
                    {' '}
                    <span className="text-gray-500">
            (IRI: <span className="font-mono text-xs">{iri}</span>)
          </span>
                </p>

                <div className="flex flex-wrap gap-2">
                    <button
                        onClick={handleNavigateToEntity}
                        className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-md transition-colors"
                    >
                        Find this term in OLS
                    </button>

                    {/* Graph Expansion/Collapse Buttons */}
                    <button
                        onClick={handleExpandNode}
                        className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-md transition-colors flex items-center"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                        </svg>
                        Expand Node
                    </button>
                </div>
            </div>
        </div>
    );
}

// Then wrap the component with memo
// Why memo? -> https://react.dev/reference/react/memo
const EntityDetails = memo(EntityDetailsComponent);

// Export both the memoized component and its props interface
export default EntityDetails;