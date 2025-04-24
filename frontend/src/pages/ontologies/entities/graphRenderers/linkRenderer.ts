export const renderLink = (
    link: any,
    ctx: CanvasRenderingContext2D,
    hoveredNode: any | null
) => {
    const source = link.source;
    const target = link.target;

    // Get positions and calculate angle
    const x1 = source.x;
    const y1 = source.y;
    const x2 = target.x;
    const y2 = target.y;

    // Get the curvature from link (or default to 0)
    const curvature = link.curvature || 0;

    // Calculate angle and distance
    const dx = x2 - x1;
    const dy = y2 - y1;
    const distance = Math.sqrt(dx * dx + dy * dy);
    const angle = Math.atan2(dy, dx);

    // Get node dimensions (stored during node painting)
    const sourceWidth = source.__bckgDimensions?.[0] || 60;
    const sourceHeight = source.__bckgDimensions?.[1] || 30;
    const targetWidth = target.__bckgDimensions?.[0] || 60;
    const targetHeight = target.__bckgDimensions?.[1] || 30;

    // Calculate intersection points with node boundaries precisely
    const sourceHalfWidth = sourceWidth / 2;
    const sourceHalfHeight = sourceHeight / 2;
    const targetHalfWidth = targetWidth / 2;
    const targetHalfHeight = targetHeight / 2;

    // Draw based on curvature
    if (curvature > 0) {
        renderCurvedLink(
            link, ctx, hoveredNode,
            x1, y1, x2, y2,
            sourceHalfWidth, sourceHalfHeight,
            targetHalfWidth, targetHalfHeight,
            angle, distance, curvature
        );
    } else {
        renderStraightLink(
            link, ctx, hoveredNode,
            x1, y1, x2, y2,
            sourceHalfWidth, sourceHalfHeight,
            targetHalfWidth, targetHalfHeight,
            angle, dx, dy
        );
    }
};

/**
 * Renders a curved link between nodes
 */
const renderCurvedLink = (
    link: any,
    ctx: CanvasRenderingContext2D,
    hoveredNode: any | null,
    x1: number, y1: number, x2: number, y2: number,
    sourceHalfWidth: number, sourceHalfHeight: number,
    targetHalfWidth: number, targetHalfHeight: number,
    angle: number, distance: number, curvature: number
) => {
    const source = link.source;
    const target = link.target;

    // Calculate control point for the curve
    const controlPointDistance = distance * 0.5;
    const controlPointOffset = distance * curvature;

    // Calculate perpendicular vector
    const perpX = -Math.sin(angle);
    const perpY = Math.cos(angle);

    // Calculate control point
    const cpX = x1 + (x2 - x1) * 0.5 + perpX * controlPointOffset;
    const cpY = y1 + (y2 - y1) * 0.5 + perpY * controlPointOffset;

    // Calculate angles from source to control point and from control point to target
    const angleStart = Math.atan2(cpY - y1, cpX - x1);
    const angleEnd = Math.atan2(y2 - cpY, x2 - cpX);

    // Calculate intersection with source node
    let startX: number, startY: number, endX: number, endY: number;

    if (Math.abs(Math.tan(angleStart)) < sourceHalfHeight / sourceHalfWidth) {
        // Intersects with right or left edge
        const xSign = Math.sign(Math.cos(angleStart));
        startX = x1 + xSign * sourceHalfWidth;
        startY = y1 + Math.tan(angleStart) * xSign * sourceHalfWidth;
    } else {
        // Intersects with top or bottom edge
        const ySign = Math.sign(Math.sin(angleStart));
        startY = y1 + ySign * sourceHalfHeight;
        startX = x1 + (1 / Math.tan(angleStart)) * ySign * sourceHalfHeight;
    }

    // Calculate intersection with target node
    if (Math.abs(Math.tan(angleEnd)) < targetHalfHeight / targetHalfWidth) {
        // Intersects with right or left edge
        const xSign = -Math.sign(Math.cos(angleEnd));
        endX = x2 + xSign * targetHalfWidth;
        endY = y2 + Math.tan(angleEnd) * xSign * targetHalfWidth;
    } else {
        // Intersects with top or bottom edge
        const ySign = -Math.sign(Math.sin(angleEnd));
        endY = y2 + ySign * targetHalfHeight;
        endX = x2 + (1 / Math.tan(angleEnd)) * ySign * targetHalfHeight;
    }

    // Draw curved link
    ctx.beginPath();
    ctx.moveTo(startX, startY);

    // Create quadratic curve
    ctx.quadraticCurveTo(cpX, cpY, endX, endY);

    // Style based on whether the link is highlighted
    const isHighlighted = hoveredNode &&
        (hoveredNode.id === source.id || hoveredNode.id === target.id);

    ctx.strokeStyle = isHighlighted ? link.color : `${link.color}CC`;
    ctx.lineWidth = isHighlighted ? 2.5 : 1.8;
    ctx.stroke();

    // Draw arrow at the end of the curve
    // We need to calculate the tangent direction at the end point
    const arrowLength = 8;
    const arrowAngle = Math.atan2(endY - cpY, endX - cpX);

    ctx.beginPath();
    ctx.moveTo(endX, endY);
    ctx.lineTo(
        endX - arrowLength * Math.cos(arrowAngle - Math.PI / 6),
        endY - arrowLength * Math.sin(arrowAngle - Math.PI / 6)
    );
    ctx.lineTo(
        endX - arrowLength * Math.cos(arrowAngle + Math.PI / 6),
        endY - arrowLength * Math.sin(arrowAngle + Math.PI / 6)
    );
    ctx.closePath();
    ctx.fillStyle = isHighlighted ? link.color : `${link.color}CC`;
    ctx.fill();

    // Draw label for curved links
    if (hoveredNode &&
        (hoveredNode.id === source.id || hoveredNode.id === target.id)) {
        // Position label at the highest point of the curve
        const labelX = cpX;
        const labelY = cpY - 10; // Offset slightly above the curve

        ctx.font = '10px Arial';
        const textWidth = ctx.measureText(link.label).width;

        // Draw text background
        ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
        ctx.fillRect(
            labelX - textWidth / 2 - 3,
            labelY - 7,
            textWidth + 6,
            16
        );

        // Draw text
        ctx.fillStyle = '#000000';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(link.label, labelX, labelY);
    }
};

/**
 * Renders a straight link between nodes
 */
const renderStraightLink = (
    link: any,
    ctx: CanvasRenderingContext2D,
    hoveredNode: any | null,
    x1: number, y1: number, x2: number, y2: number,
    sourceHalfWidth: number, sourceHalfHeight: number,
    targetHalfWidth: number, targetHalfHeight: number,
    angle: number, dx: number, dy: number
) => {
    const source = link.source;
    const target = link.target;

    // Calculate intersection points with node boundaries precisely
    let startX: number, startY: number, endX: number, endY: number;

    // For straight links, use the original intersection calculation
    // Check if angle intersects horizontal or vertical edges
    if (Math.abs(Math.tan(angle)) < sourceHalfHeight / sourceHalfWidth) {
        // Intersects with right or left edge
        const xSign = Math.sign(Math.cos(angle));
        startX = x1 + xSign * sourceHalfWidth;
        startY = y1 + Math.tan(angle) * xSign * sourceHalfWidth;
    } else {
        // Intersects with top or bottom edge
        const ySign = Math.sign(Math.sin(angle));
        startY = y1 + ySign * sourceHalfHeight;
        startX = x1 + (1 / Math.tan(angle)) * ySign * sourceHalfHeight;
    }

    // Calculate target intersection (which edge of rectangle)
    if (Math.abs(Math.tan(angle)) < targetHalfHeight / targetHalfWidth) {
        // Intersects with right or left edge
        const xSign = -Math.sign(Math.cos(angle));
        endX = x2 + xSign * targetHalfWidth;
        endY = y2 + Math.tan(angle) * xSign * targetHalfWidth;
    } else {
        // Intersects with top or bottom edge
        const ySign = -Math.sign(Math.sin(angle));
        endY = y2 + ySign * targetHalfHeight;
        endX = x2 + (1 / Math.tan(angle)) * ySign * targetHalfHeight;
    }

    // Handle special case when angle is close to 0 or PI
    if (Math.abs(dy) < 0.001) {
        startX = x1 + Math.sign(dx) * sourceHalfWidth;
        startY = y1;
        endX = x2 - Math.sign(dx) * targetHalfWidth;
        endY = y2;
    }

    // Handle special case when angle is close to PI/2 or 3PI/2
    if (Math.abs(dx) < 0.001) {
        startX = x1;
        startY = y1 + Math.sign(dy) * sourceHalfHeight;
        endX = x2;
        endY = y2 - Math.sign(dy) * targetHalfHeight;
    }

    // Draw straight link
    ctx.beginPath();
    ctx.moveTo(startX, startY);
    ctx.lineTo(endX, endY);
    ctx.strokeStyle = hoveredNode &&
    (hoveredNode.id === source.id || hoveredNode.id === target.id) ? link.color : `${link.color}CC`;
    ctx.lineWidth = hoveredNode &&
    (hoveredNode.id === source.id || hoveredNode.id === target.id) ? 2 : 1.5;
    ctx.stroke();

    // Draw arrow
    const arrowLength = 8;
    ctx.beginPath();
    ctx.moveTo(endX, endY);
    ctx.lineTo(
        endX - arrowLength * Math.cos(angle - Math.PI / 6),
        endY - arrowLength * Math.sin(angle - Math.PI / 6)
    );
    ctx.lineTo(
        endX - arrowLength * Math.cos(angle + Math.PI / 6),
        endY - arrowLength * Math.sin(angle + Math.PI / 6)
    );
    ctx.closePath();
    ctx.fillStyle = hoveredNode &&
    (hoveredNode.id === source.id || hoveredNode.id === target.id) ? link.color : `${link.color}CC`;
    ctx.fill();

    // Draw label on hover or for all connected links
    if (hoveredNode &&
        (hoveredNode.id === source.id || hoveredNode.id === target.id)) {
        const midX = (startX + endX) / 2;
        const midY = (startY + endY) / 2;

        // Position label perpendicular to link
        const perpX = -dy / Math.sqrt(dx * dx + dy * dy) * 8;
        const perpY = dx / Math.sqrt(dx * dx + dy * dy) * 8;

        ctx.font = '10px Arial';
        const textWidth = ctx.measureText(link.label).width;

        // Draw text background
        ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
        ctx.fillRect(
            midX + perpX - textWidth / 2 - 3,
            midY + perpY - 7,
            textWidth + 6,
            16
        );

        // Draw text
        ctx.fillStyle = '#000000';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(link.label, midX + perpX, midY + perpY);
    }
};