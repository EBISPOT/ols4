import React from 'react';

interface ExpandedNodesListProps {
    expandedNodes: Set<string>;
    getNodeLabel: (nodeId: string) => string | null;
    getNodeShortForm: (nodeId: string) => string;
}

export const ExpandedNodesList: React.FC<ExpandedNodesListProps> = ({
                                                                        expandedNodes,
                                                                        getNodeLabel,
                                                                        getNodeShortForm
                                                                    }) => {
    if (expandedNodes.size === 0) return null;

    return (
        <div className="mb-2 p-3 border rounded-md bg-gray-50">
            <h3 className="font-semibold text-sm mb-2">List of expanded nodes (*):</h3>
            <ul className="list-disc list-inside text-sm">
                {Array.from(expandedNodes).map(nodeId => {
                    const nodeLabel = getNodeLabel(nodeId);
                    if (!nodeLabel) return null;

                    const nodeShortForm = getNodeShortForm(nodeId);

                    return (
                        <li key={nodeId} className="ml-2">
                            {nodeLabel} ({nodeShortForm})
                        </li>
                    );
                })}
            </ul>
        </div>
    );
};