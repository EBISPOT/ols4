export interface RelationshipType {
    color: string;
    count: number;
    visible: boolean;
}

export interface EntityGraphProps {
    ontologyId: string;
    selectedEntity: any;  // Assuming Entity class has getIri() method
    onNodeSelect?: (nodeInfo: { iri: any; nodeType: string; data?: any }) => void;
    expandedNodes?: Set<string>;
    onStoreFetchFunc?: (func: (nodeId: string) => Promise<boolean>) => void;
    setExpandedNodes?: (updater: (prev: Set<string>) => Set<string>) => void;
}