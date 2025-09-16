import React from 'react';

interface RelationshipType {
    color: string;
    count: number;
    visible: boolean;
}

interface RelationshipFiltersProps {
    relationshipTypes: Record<string, RelationshipType>;
    toggleRelationship: (relationshipType: string) => void;
    toggleAllRelationships: (showAll: boolean) => void;
    onHideAll: () => void;
}

export const RelationshipFilters: React.FC<RelationshipFiltersProps> = ({
                                                                            relationshipTypes,
                                                                            toggleRelationship,
                                                                            toggleAllRelationships,
                                                                            onHideAll
                                                                        }) => {
    // Sort relationship types alphabetically by name
    const sortedRelationships = Object.entries(relationshipTypes)
        .sort(([typeA], [typeB]) => typeA.localeCompare(typeB));

    return (
        <div className="mb-2 p-3 border rounded-md bg-gray-50">
            <div className="flex justify-between items-center mb-3 pb-2 border-b border-gray-200">
                <h3 className="font-semibold text-sm">Relationship Types:</h3>
                <div className="flex gap-3">
                    <button
                        className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                        onClick={() => toggleAllRelationships(true)}
                    >
                        Show All
                    </button>
                    <span className="text-gray-400">|</span>
                    <button
                        className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                        onClick={() => {
                            toggleAllRelationships(false);
                            // Apply special zoom handling for "hide all" case
                            setTimeout(onHideAll, 100);
                        }}
                    >
                        Hide All
                    </button>
                </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3 text-sm">
                {sortedRelationships.map(([type, { color, count, visible }]) => (
                    <div key={type} className="flex items-center">
                        <label className="flex items-center cursor-pointer">
                            <input
                                type="checkbox"
                                checked={visible}
                                onChange={() => toggleRelationship(type)}
                                className="mr-1 h-4 w-4"
                            />
                            <div
                                className="w-6 h-3 mx-1"
                                style={{
                                    backgroundColor: color,
                                    border: "1px solid rgba(0,0,0,0.2)"
                                }}
                            ></div>
                            <span className="text-sm font-medium">{type}</span>
                            <span className="text-xs text-gray-500 ml-1">({count})</span>
                        </label>
                    </div>
                ))}
            </div>
            <div className="text-xs text-gray-500 mt-2 text-right">
                Hover to highlight • Click to navigate
            </div>
        </div>
    );
};