import React from 'react';

interface EmptyGraphDisplayProps {
    hasRelationshipFilters: boolean;
    areAllRelationshipsHidden: boolean;
}

export const EmptyGraphDisplay: React.FC<EmptyGraphDisplayProps> = ({
                                                                        hasRelationshipFilters,
                                                                        areAllRelationshipsHidden
                                                                    }) => (
    <div className="flex items-center justify-center h-full">
        <div className="text-center p-4">
            <p className="text-lg font-semibold text-gray-700">No graph data available</p>
            <p className="text-sm text-gray-500 mt-2">
                {hasRelationshipFilters && areAllRelationshipsHidden
                    ? "All relationship types are currently hidden. Enable them in the legend above."
                    : "This entity doesn't have any relationships to display in the graph view."}
            </p>
        </div>
    </div>
);