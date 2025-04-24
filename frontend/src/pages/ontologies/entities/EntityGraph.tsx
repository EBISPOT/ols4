import React, { useState, useMemo, useCallback, useRef, useEffect } from "react";
import ForceGraph2D, { ForceGraphMethods } from "react-force-graph-2d";
import * as d3 from 'd3-force';

// Import hooks and types
import { useOntologyGraph } from "../../../app/hooks";
import { EntityGraphProps, RelationshipType } from "./graphUtils/types";
import { generateColor } from "./graphUtils/colorUtils";
import { getShortFormFromIri } from "./graphUtils/nodeUtils";
import { renderNode } from "./graphRenderers/nodeRenderer";
import { renderLink } from "./graphRenderers/linkRenderer";

// Import UI components
import { LoadingOverlay } from "./graphUIComponents/LoadingOverlay";
import { ErrorDisplay } from "./graphUIComponents/ErrorDisplay";
import { RelationshipFilters } from "./graphUIComponents/RelationshipFilters";
import { ExpandedNodesList } from "./graphUIComponents/ExpandedNodesList";
import { EmptyGraphDisplay } from "./graphUIComponents/EmptyGraphDisplay";

const EntityGraph: React.FC<EntityGraphProps> = ({
                                                   ontologyId,
                                                   selectedEntity,
                                                   onNodeSelect,
                                                   expandedNodes = new Set(),
                                                   onStoreFetchFunc,
                                                   setExpandedNodes
                                                 }) => {
  // Component state
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<ForceGraphMethods>();
  const [dimensions, setDimensions] = useState({ width: 800, height: 600 });
  const [hoveredNode, setHoveredNode] = useState<any | null>(null);
  const [relationshipFilters, setRelationshipFilters] = useState<Record<string, RelationshipType>>({});

  // Create a local expanded nodes state if no setter is provided
  const [localExpandedNodes, setLocalExpandedNodes] = useState<Set<string>>(new Set());

  // Use either the prop state or local state depending on whether a setter was provided
  const expandedNodesSet = expandedNodes || localExpandedNodes;
  const updateExpandedNodes = useCallback((updaterFn: (prev: Set<string>) => Set<string>) => {
    if (setExpandedNodes) {
      // If external setter is provided, use it
      setExpandedNodes(updaterFn);
    } else {
      // Otherwise use local state
      setLocalExpandedNodes(updaterFn);
    }
  }, [setExpandedNodes]);

  // Fetch graph data using the hook
  const { graphData: rawData, loading, error, fetchNodeConnections } = useOntologyGraph(
      ontologyId,
      selectedEntity?.getIri()
  );

  // Pass the fetchNodeConnections function to the parent component once
  const fetchNodeConnectionsRef = useRef(fetchNodeConnections);

  useEffect(() => {
    fetchNodeConnectionsRef.current = fetchNodeConnections;
  }, [fetchNodeConnections]);

  useEffect(() => {
    if (onStoreFetchFunc && fetchNodeConnectionsRef.current) {
      onStoreFetchFunc(fetchNodeConnectionsRef.current);
    }
  }, [onStoreFetchFunc]);

  // Update dimensions when container size changes
  useEffect(() => {
    if (!containerRef.current) return;

    const updateDimensions = () => {
      if (containerRef.current) {
        const { width, height } = containerRef.current.getBoundingClientRect();
        setDimensions({ width, height });
      }
    };

    updateDimensions();

    const observer = new ResizeObserver(updateDimensions);
    observer.observe(containerRef.current);

    return () => {
      if (containerRef.current) {
        observer.unobserve(containerRef.current);
      }
    };
  }, []);

  // Toggle single relationship visibility
  const toggleRelationship = useCallback((relationshipType: string) => {
    setRelationshipFilters(prev => ({
      ...prev,
      [relationshipType]: {
        ...prev[relationshipType],
        visible: !prev[relationshipType].visible
      }
    }));
  }, []);

  // Handle the "show all / hide all" toggle
  const toggleAllRelationships = useCallback((showAll: boolean) => {
    setRelationshipFilters(prev => {
      const updated: Record<string, RelationshipType> = {};
      Object.entries(prev).forEach(([key, value]) => {
        updated[key] = { ...value, visible: showAll };
      });
      return updated;
    });
  }, []);

  // Process the graph data for visualization
  const { graphData, relationshipTypes } = useMemo(() => {
    if (!rawData || !rawData.nodes || !rawData.edges) {
      return { graphData: { nodes: [], links: [] }, relationshipTypes: {} };
    }

    // Create a map to deduplicate edges
    const uniqueEdges = new Map();

    // Process edges to keep only unique combinations of source, target, and relationship type
    rawData.edges.forEach(edge => {
      const label = edge.label || 'unlabeled';
      const edgeKey = `${edge.source}|${edge.target}|${label}`;

      // Only keep the first instance of each unique edge
      if (!uniqueEdges.has(edgeKey)) {
        uniqueEdges.set(edgeKey, edge);
      }
    });

    // Convert unique edges back to array
    const deduplicatedEdges = Array.from(uniqueEdges.values());

    // Extract and count unique relationship types
    const relationTypes: Record<string, RelationshipType> = {};
    deduplicatedEdges.forEach(edge => {
      const label = edge.label || 'unlabeled';
      if (!relationTypes[label]) {
        relationTypes[label] = {
          count: 1,
          // By default, no relationships are selected
          visible: relationshipFilters[label]?.visible ?? false,
          color: relationshipFilters[label]?.color || ''
        };
      } else {
        relationTypes[label].count += 1;
      }
    });

    // Assign colors to relationship types
    const relationTypeArray = Object.keys(relationTypes);
    relationTypeArray.forEach((type, index) => {
      if (!relationTypes[type].color) {
        relationTypes[type].color = generateColor(index, relationTypeArray.length);
      }
    });

    // Update relationship filters with new data
    if (JSON.stringify(relationTypes) !== JSON.stringify(relationshipFilters)) {
      setRelationshipFilters(relationTypes);
    }

    // Create nodes with consistent properties
    const nodes = rawData.nodes.map(node => ({
      id: node.iri,
      label: node.label || getShortFormFromIri(node.iri),
      isSelected: selectedEntity?.getIri() === node.iri,
      isObsolete: node.is_obsolete || false,
      originalNode: node
    }));

    // Track multiple edges between the same node pairs (but not duplicates)
    const edgeCounts: Record<string, number> = {};
    const bidirectionalPairs = new Map<string, boolean>();

    // First pass: count how many edges exist between each node pair (in each direction)
    deduplicatedEdges.forEach(edge => {
      // Create directional keys
      const forwardKey = `${edge.source}|${edge.target}`;

      // Create a normalized key that's the same regardless of source/target order
      const nodeIds = [edge.source, edge.target].sort();
      const normalizedKey = `${nodeIds[0]}|${nodeIds[1]}`;

      // Track directional counts
      if (!edgeCounts[forwardKey]) {
        edgeCounts[forwardKey] = 1;
      } else {
        edgeCounts[forwardKey]++;
      }

      // Check for bidirectional edges
      const hasBidirectional = deduplicatedEdges.some(e =>
          e.source === edge.target && e.target === edge.source
      );

      // Store bidirectional information
      if (hasBidirectional) {
        bidirectionalPairs.set(normalizedKey, true);
      }
    });

    // Create links with relationship colors and curvature
    const links = deduplicatedEdges.map((edge) => {
      const type = edge.label || 'unlabeled';

      // Get directional key
      const directionalKey = `${edge.source}|${edge.target}`;

      // Get normalized key (direction-agnostic)
      const nodeIds = [edge.source, edge.target].sort();
      const normalizedKey = `${nodeIds[0]}|${nodeIds[1]}`;

      // Get number of edges in this specific direction
      const directionalCount = edgeCounts[directionalKey] || 0;

      // Determine if we have a bidirectional relationship for these nodes
      const isBidirectional = bidirectionalPairs.has(normalizedKey);

      // Start with no curvature (straight line)
      let curvature = 0;

      // Only add curvature in two cases:
      // 1. Multiple edges in the same direction
      // 2. Bidirectional relationship
      if (directionalCount > 1) {
        // For multiple edges in the same direction, calculate distinct curvatures
        const edgeIndex = deduplicatedEdges.findIndex(e =>
            e.source === edge.source &&
            e.target === edge.target &&
            e.label === edge.label
        );

        // Spacing between multiple edges
        const curveStep = 0.2;
        const initialCurve = 0.15;

        // Calculate different curvature for each edge
        curvature = initialCurve + (edgeIndex % directionalCount) * curveStep;
      } else if (isBidirectional) {
        // For bidirectional edges, make them curve in opposite directions
        // Consistent direction based on node IDs to ensure opposite curves
        const direction = edge.source < edge.target ? 1 : -1;
        curvature = 0.2 * direction;
      }

      return {
        source: edge.source,
        target: edge.target,
        label: type,
        color: relationTypes[type]?.color || '#aaaaaa',
        visible: relationTypes[type]?.visible ?? false,
        curvature
      };
    });

    // Filter visible links - always keep links in the expansion chain
    const visibleLinks = links.filter(link => {
      const sourceId = typeof link.source === 'object' ? link.source.id : link.source;
      const targetId = typeof link.target === 'object' ? link.target.id : link.target;

      // Links are visible if:
      // 1. They're selected in the filter
      // 2. OR they connect a node that's explicitly expanded to its parent/source
      const isDirectExpansionLink =
          (expandedNodesSet.has(sourceId) && selectedEntity?.getIri() === targetId) ||
          (expandedNodesSet.has(targetId) && selectedEntity?.getIri() === sourceId) ||
          (expandedNodesSet.has(sourceId) && expandedNodesSet.has(targetId));

      return link.visible || isDirectExpansionLink;
    });

    // Get nodes connected by visible links
    const nodesInVisibleLinks = new Set<string>();
    visibleLinks.forEach(link => {
      nodesInVisibleLinks.add(typeof link.source === 'object' ? link.source.id : link.source);
      nodesInVisibleLinks.add(typeof link.target === 'object' ? link.target.id : link.target);
    });

    // Filter nodes to include visible links, selected node, and expanded nodes
    const selectedNodeId = selectedEntity?.getIri();
    const visibleNodes = nodes.filter(node =>
        nodesInVisibleLinks.has(node.id) ||
        (selectedNodeId && node.id === selectedNodeId) ||
        expandedNodesSet.has(node.id)
    );

    return {
      graphData: { nodes: visibleNodes, links: visibleLinks },
      relationshipTypes: relationTypes
    };
  }, [rawData, relationshipFilters, selectedEntity, expandedNodesSet]);

  // For double-click detection
  const [clickTimeout, setClickTimeout] = useState<NodeJS.Timeout | null>(null);
  const [prevClick, setPrevClick] = useState<{ node: any, time: Date } | null>(null);
  const DBL_CLICK_TIMEOUT = 500; // ms

  // Handle double-click
  const handleNodeDblClick = useCallback((node: any) => {
    // Cancel any pending single-click action
    if (clickTimeout) {
      clearTimeout(clickTimeout);
      setClickTimeout(null);
    }

    // Check if this is a leaf node (no outgoing links)
    const isLeafNode = !graphData.links.some(link =>
        (typeof link.source === 'object' ? link.source.id : link.source) === node.id
    );

    if (isLeafNode) {
      try {
        // Set loading state for this node
        updateExpandedNodes(prev => {
          const newSet = new Set(prev);
          newSet.add(node.id);
          return newSet;
        });

        // Fetch additional connections for this node
        if (fetchNodeConnections) {
          fetchNodeConnections(node.id)
              .then(success => {
                if (success) {
                  // Reheat simulation after adding new nodes
                  if (graphRef.current) {
                    graphRef.current.d3ReheatSimulation();

                    // After some stabilization, fit to view
                    setTimeout(() => {
                      graphRef.current?.zoomToFit(400);
                    }, 1000);
                  }
                } else {
                  // Remove from expanded nodes if failed
                  updateExpandedNodes(prev => {
                    const newSet = new Set(prev);
                    newSet.delete(node.id);
                    return newSet;
                  });
                }
              })
              .catch(err => {
                // Remove from expanded nodes if failed
                updateExpandedNodes(prev => {
                  const newSet = new Set(prev);
                  newSet.delete(node.id);
                  return newSet;
                });
              });
        } else {
          // Remove from expanded nodes if function not available
          updateExpandedNodes(prev => {
            const newSet = new Set(prev);
            newSet.delete(node.id);
            return newSet;
          });
        }
      } catch (error) {
        console.error("Error expanding node:", error);

        // Remove from expanded nodes if failed
        updateExpandedNodes(prev => {
          const newSet = new Set(prev);
          newSet.delete(node.id);
          return newSet;
        });
      }
    }
  }, [graphData.links, fetchNodeConnections, clickTimeout, updateExpandedNodes]);

  // Node click handler
  const handleNodeClick = useCallback((node: any) => {
    const now = new Date();

    if (prevClick &&
        prevClick.node.id === node.id &&
        (now.getTime() - prevClick.time.getTime()) < DBL_CLICK_TIMEOUT) {
      // This is a double-click
      setPrevClick(null); // Reset click tracking

      // Handle double-click
      handleNodeDblClick(node);
    } else {
      // This is a first click or a click on a different node
      setPrevClick({ node, time: now });

      // Set a timeout to handle as a single click if no double-click occurs
      const timeout = setTimeout(async () => {
        // Clear any existing timeout
        if (clickTimeout) {
          clearTimeout(clickTimeout);
          setClickTimeout(null);
        }

        try {
          // Fetch the entity details when a node is clicked
          const entityIri = node.id;

          // API call to get the complete entity data
          const doubleEncodedIri = encodeURIComponent(encodeURIComponent(entityIri));
          const apiUrl = `${process.env.REACT_APP_APIURL}api/v2/ontologies/${ontologyId}/entities/${doubleEncodedIri}`;

          const response = await fetch(apiUrl);

          if (!response.ok) {
            throw new Error(`Failed to fetch entity: ${response.status}`);
          }

          const entityData = await response.json();

          // Determine entity type from the fetched data
          let detectedType = "classes"; // Default to classes

          if (entityData.type && Array.isArray(entityData.type)) {
            if (entityData.type.includes("class")) {
              detectedType = "classes";
            } else if (entityData.type.includes("individual")) {
              detectedType = "individuals";
            } else if (entityData.type.includes("property")) {
              detectedType = "properties";
            }
          }

          // Create an entity-like object with the fetched data
          const entityObject = {
            iri: entityIri,
            nodeType: detectedType,
            data: entityData // Include the full entity data
          };

          // Notify parent component with the fetched entity data
          if (onNodeSelect) {
            onNodeSelect(entityObject);
          }
        } catch (error) {
          console.error("Error fetching entity data:", error);

          // If fetch fails, fall back to just passing the ID
          if (onNodeSelect) {
            onNodeSelect(node.id);
          }
        }
      }, DBL_CLICK_TIMEOUT + 50);

      // Clear any existing timeout
      if (clickTimeout) {
        clearTimeout(clickTimeout);
      }

      setClickTimeout(timeout);
    }
  }, [onNodeSelect, clickTimeout, prevClick, ontologyId, handleNodeDblClick]);

  // Custom node renderer
  const paintNode = useCallback((node: any, ctx: CanvasRenderingContext2D) => {
    renderNode(node, ctx, hoveredNode, expandedNodes, graphData.links);
  }, [hoveredNode, expandedNodes, graphData.links]);

  // Custom link renderer
  const paintLink = useCallback((link: any, ctx: CanvasRenderingContext2D) => {
    renderLink(link, ctx, hoveredNode);
  }, [hoveredNode]);

  // Handle zooming for "hide all" case
  const handleZoomForHideAll = useCallback(() => {
    if (graphRef.current) {
      // Set a fixed zoom level
      graphRef.current.zoom(0.8);

      // Center on the selected node if present
      if (graphData.nodes.length > 0) {
        const selectedNode = graphData.nodes.find(n => n.isSelected);
        const nodeToCenter = selectedNode || graphData.nodes[0];

        if (nodeToCenter) {
          setTimeout(() => {
            graphRef.current?.centerAt(
                (nodeToCenter as any).x || 0,
                (nodeToCenter as any).y || 0,
                1000 // transition duration
            );
          }, 50);
        }
      } else {
        // Center at viewport middle
        graphRef.current.centerAt(
            dimensions.width / 2,
            dimensions.height / 2,
            1000
        );
      }
    }
  }, [graphData.nodes, dimensions]);

  // Helper for getting node label
  const getNodeLabel = useCallback((nodeId: string): string | null => {
    const node = rawData?.nodes?.find(n => n.iri === nodeId);
    if (!node) return null;
    return node.label || getShortFormFromIri(nodeId);
  }, [rawData?.nodes]);

  // Set up the graph with better forces
  useEffect(() => {
    if (graphData.nodes.length > 0 && graphRef.current) {
      // Configure force simulation for better spacing
      if (graphRef.current) {
        const linkForce = graphRef.current.d3Force('link');
        if (linkForce) {
          linkForce.distance(() => 180);
        }

        const chargeForce = graphRef.current.d3Force('charge');
        if (chargeForce) {
          chargeForce.strength(-800).distanceMax(1500);
        }
        // Add collision force safely
        graphRef.current.d3Force('collision', d3.forceCollide().radius(80));
      }

      // Reheat the simulation
      graphRef.current.d3ReheatSimulation();

      // Fit graph to view with a slight delay to ensure the graph has stabilized
      setTimeout(() => {
        if (graphRef.current) {
          // Only auto-fit if we have multiple nodes
          if (graphData.nodes.length > 1) {
            graphRef.current.zoomToFit(400, 100);
          }
          // For single node (like when all relationships are hidden), set a fixed zoom
          else {
            handleZoomForHideAll();
          }
        }
      }, 500);
    }
  }, [graphData, handleZoomForHideAll]);

  // Check if all relationships are hidden
  const areAllRelationshipsHidden = useMemo(() => {
    return Object.keys(relationshipFilters).length > 0 &&
        Object.values(relationshipFilters).every(f => !f.visible);
  }, [relationshipFilters]);

  return (
      <div className="relative">
        {/* Loading and error states */}
        {loading && <LoadingOverlay />}
        {error && <ErrorDisplay error={error} />}

        {/* Relationship filters */}
        {graphData.nodes.length > 0 && (
            <RelationshipFilters
                relationshipTypes={relationshipTypes}
                toggleRelationship={toggleRelationship}
                toggleAllRelationships={toggleAllRelationships}
                onHideAll={handleZoomForHideAll}
            />
        )}

        {/* Expanded Nodes List */}
        <ExpandedNodesList
            expandedNodes={expandedNodesSet}
            getNodeLabel={getNodeLabel}
            getNodeShortForm={getShortFormFromIri}
        />

        {/* Graph container */}
        <div
            ref={containerRef}
            className="w-full h-[600px] border border-gray-300 rounded-md overflow-hidden"
        >
          {graphData.nodes.length > 0 ? (
              <ForceGraph2D
                  ref={graphRef as React.MutableRefObject<ForceGraphMethods>}
                  graphData={graphData}
                  nodeCanvasObject={paintNode}
                  linkCanvasObject={paintLink}
                  onNodeClick={handleNodeClick}
                  onNodeHover={setHoveredNode}
                  nodeCanvasObjectMode={() => 'after'}
                  enableZoomInteraction={true}
                  enablePanInteraction={true}
                  enableNodeDrag={true}
                  width={dimensions.width}
                  height={dimensions.height}
                  cooldownTicks={100}
                  // Enable curved links
                  linkCurvature="curvature"
                  // Don't use built-in link rendering at all - we'll do it manually
                  linkColor={() => 'rgba(0,0,0,0)'}
                  // Custom node hover area calculation - important for improved hover detection
                  nodePointerAreaPaint={(node, color, ctx) => {
                    // Use stored dimensions or calculate them
                    const nodeWidth = node.__bckgDimensions?.[0] || 60;
                    const nodeHeight = node.__bckgDimensions?.[1] || 30;

                    // Draw a larger invisible area for hit detection
                    ctx.fillStyle = color;
                    ctx.beginPath();
                    const padding = 4; // Extra padding for easier hovering
                    ctx.roundRect(
                        node.x - nodeWidth / 2 - padding,
                        node.y - nodeHeight / 2 - padding,
                        nodeWidth + padding * 2,
                        nodeHeight + padding * 2,
                        5
                    );
                    ctx.fill();
                  }}
              />
          ) : !loading && (
              <EmptyGraphDisplay
                  hasRelationshipFilters={Object.keys(relationshipFilters).length > 0}
                  areAllRelationshipsHidden={areAllRelationshipsHidden}
              />
          )}
        </div>
      </div>
  );
};

export default EntityGraph;