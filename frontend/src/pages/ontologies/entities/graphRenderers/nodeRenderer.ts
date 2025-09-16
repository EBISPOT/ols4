import { getColorBrightness, applyOpacity } from '../graphUtils/colorUtils';

export const renderNode = (
    node: any,
    ctx: CanvasRenderingContext2D,
    hoveredNode: any | null,
    expandedNodes: Set<string>,
    graphLinks: any[]
) => {
    const { x, y, label, isSelected, isObsolete } = node;
    const isHovered = hoveredNode === node;
    const isExpanded = expandedNodes.has(node.id);

    // Check if this is a leaf node (no outgoing links)
    const isLeafNode = !graphLinks.some(link =>
        (typeof link.source === 'object' ? link.source.id : link.source) === node.id
    );

    // Draw node body and label
    drawNodeBody(node, ctx, isHovered, isSelected, isObsolete, isExpanded, graphLinks);

    // Draw expansion indicators if applicable
    if (isLeafNode && !isExpanded) {
        drawExpandIcon(node, ctx);
    } else if (isExpanded) {
        drawCollapseIcon(node, ctx);
    }

    // Draw tooltip for hovered node
    if (isHovered) {
        drawNodeTooltip(node, ctx);
    }
};

/**
 * Draws the main node body including background and label
 */
const drawNodeBody = (
    node: any,
    ctx: CanvasRenderingContext2D,
    isHovered: boolean,
    isSelected: boolean,
    isObsolete: boolean,
    isExpanded: boolean,
    graphLinks: any[]
) => {
    const { x, y, label } = node;

    // Store node dimensions for hit detection and link connections
    const fontSize = isHovered ? 12 : 11;
    ctx.font = `${isHovered ? 'bold ' : ''}${fontSize}px Arial`;

    // Limit label length
    let displayLabel = label;
    if (displayLabel.length > 25) {
        displayLabel = displayLabel.substring(0, 22) + '...';
    }

    // Calculate node dimensions
    const textWidth = Math.max(ctx.measureText(displayLabel).width, 40);
    const padding = 10;
    const rectWidth = textWidth + padding * 2;
    const rectHeight = fontSize + padding * 2;

    // Store dimensions in node object for link calculation and hit detection
    node.__bckgDimensions = [rectWidth, rectHeight];

    // Determine node color based on its links and status
    let nodeColor = '#7c93c7'; // Default blue

    // Center/selected node is gold
    if (isSelected) {
        nodeColor = '#FFD700';
    }
    // Expanded nodes get a highlight color
    else if (isExpanded) {
        nodeColor = '#E57373'; // Light red
    }
    // Obsolete nodes are red
    else if (isObsolete) {
        nodeColor = '#f44336';
    }
    // Otherwise, use the color of its first relationship
    else {
        const nodeLinks = graphLinks.filter(link => {
            const sourceId = typeof link.source === 'object' ? link.source.id : link.source;
            const targetId = typeof link.target === 'object' ? link.target.id : link.target;
            return sourceId === node.id || targetId === node.id;
        });

        if (nodeLinks.length > 0) {
            nodeColor = nodeLinks[0].color;
        }
    }

    // Apply opacity to color if it's a hex color
    let finalNodeColor = applyOpacity(nodeColor, isHovered ? 1 : 0.9);

    // Draw rounded rectangle
    ctx.fillStyle = finalNodeColor;
    ctx.strokeStyle = isHovered ? '#000000' : '#666666';
    ctx.lineWidth = isHovered ? 2 : 1;

    const cornerRadius = 5;

    ctx.beginPath();
    ctx.moveTo(x - rectWidth / 2 + cornerRadius, y - rectHeight / 2);
    ctx.lineTo(x + rectWidth / 2 - cornerRadius, y - rectHeight / 2);
    ctx.quadraticCurveTo(x + rectWidth / 2, y - rectHeight / 2, x + rectWidth / 2, y - rectHeight / 2 + cornerRadius);
    ctx.lineTo(x + rectWidth / 2, y + rectHeight / 2 - cornerRadius);
    ctx.quadraticCurveTo(x + rectWidth / 2, y + rectHeight / 2, x + rectWidth / 2 - cornerRadius, y + rectHeight / 2);
    ctx.lineTo(x - rectWidth / 2 + cornerRadius, y + rectHeight / 2);
    ctx.quadraticCurveTo(x - rectWidth / 2, y + rectHeight / 2, x - rectWidth / 2, y + rectHeight / 2 - cornerRadius);
    ctx.lineTo(x - rectWidth / 2, y - rectHeight / 2 + cornerRadius);
    ctx.quadraticCurveTo(x - rectWidth / 2, y - rectHeight / 2, x - rectWidth / 2 + cornerRadius, y - rectHeight / 2);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();

    // Choose text color based on background brightness
    const brightness = getColorBrightness(nodeColor);
    ctx.fillStyle = brightness > 128 ? '#000000' : '#ffffff';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(displayLabel, x, y);
};

/**
 * Draws an expansion icon (plus sign) for leaf nodes
 */
const drawExpandIcon = (node: any, ctx: CanvasRenderingContext2D) => {
    const { x, y } = node;
    const [rectWidth, rectHeight] = node.__bckgDimensions || [60, 30];

    const plusSize = 8;
    const plusX = x + rectWidth / 2 - plusSize;
    const plusY = y + rectHeight / 2 - plusSize;

    // Background circle for the plus
    ctx.beginPath();
    ctx.arc(plusX, plusY, plusSize / 2, 0, 2 * Math.PI);
    ctx.fillStyle = '#ffffff';
    ctx.fill();
    ctx.strokeStyle = '#666666';
    ctx.lineWidth = 1;
    ctx.stroke();

    // Plus sign
    ctx.beginPath();
    ctx.moveTo(plusX - 3, plusY);
    ctx.lineTo(plusX + 3, plusY);
    ctx.moveTo(plusX, plusY - 3);
    ctx.lineTo(plusX, plusY + 3);
    ctx.strokeStyle = '#666666';
    ctx.lineWidth = 1;
    ctx.stroke();
};

/**
 * Draws a collapse icon (minus sign) for expanded nodes
 */
const drawCollapseIcon = (node: any, ctx: CanvasRenderingContext2D) => {
    const { x, y } = node;
    const [rectWidth, rectHeight] = node.__bckgDimensions || [60, 30];

    const minusSize = 8;
    const minusX = x + rectWidth / 2 - minusSize;
    const minusY = y + rectHeight / 2 - minusSize;

    // Background circle for the minus
    ctx.beginPath();
    ctx.arc(minusX, minusY, minusSize / 2, 0, 2 * Math.PI);
    ctx.fillStyle = '#ffffff';
    ctx.fill();
    ctx.strokeStyle = '#666666';
    ctx.lineWidth = 1;
    ctx.stroke();

    // Minus sign
    ctx.beginPath();
    ctx.moveTo(minusX - 3, minusY);
    ctx.lineTo(minusX + 3, minusY);
    ctx.strokeStyle = '#666666';
    ctx.lineWidth = 1;
    ctx.stroke();
};

/**
 * Draws a tooltip with node information
 */
const drawNodeTooltip = (node: any, ctx: CanvasRenderingContext2D) => {
    const { x, y, label, id } = node;

    // Prepare tooltip text
    const tooltipLabelText = "Label: " + label;
    const tooltipIriText = "IRI: " + id;

    // Calculate dimensions
    ctx.font = 'bold 12px Arial';
    const labelWidth = ctx.measureText(tooltipLabelText).width;

    ctx.font = '11px Arial';
    const iriWidth = ctx.measureText(tooltipIriText).width;

    // Add extra padding to ensure text doesn't overflow
    const textWidth = Math.max(labelWidth, iriWidth);
    const boxWidth = textWidth + 30;
    const boxHeight = 50;

    // Calculate position - centered above the node
    const boxX = x - boxWidth/2;
    const boxY = y - 67;

    // Draw shadow
    ctx.fillStyle = 'rgba(0, 0, 0, 0.2)';
    ctx.beginPath();
    ctx.roundRect(
        boxX + 2,
        boxY + 2,
        boxWidth,
        boxHeight,
        5
    );
    ctx.fill();

    // Draw background
    ctx.fillStyle = '#f8f8f8'; // Off-white background
    ctx.strokeStyle = '#dddddd'; // Light gray border
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.roundRect(
        boxX,
        boxY,
        boxWidth,
        boxHeight,
        5
    );
    ctx.fill();
    ctx.stroke();

    // Draw tooltip text with proper labels
    ctx.fillStyle = '#333333'; // Dark gray text
    ctx.textAlign = 'left';
    ctx.font = 'bold 12px Arial';
    ctx.fillText(tooltipLabelText, boxX + 10, boxY + 17);

    ctx.fillStyle = '#555555'; // Lighter gray for IRI
    ctx.font = '11px Arial';
    ctx.fillText(tooltipIriText, boxX + 10, boxY + 37);
};