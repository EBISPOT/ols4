export const generateColor = (index: number, total?: number): string => {
    // Use a high-contrast color palette with clearly distinguishable colors
    const distinctColors = [
        '#e41a1c', // red
        '#377eb8', // blue
        '#4daf4a', // green
        '#984ea3', // purple
        '#ff7f00', // orange
        '#ffff33', // yellow
        '#a65628', // brown
        '#f781bf', // pink
        '#1b9e77', // teal
        '#d95f02', // rust
        '#7570b3', // slate blue
        '#e7298a', // magenta
        '#66a61e', // lime green
        '#e6ab02', // amber
        '#a6761d', // dark tan
        '#666666'  // dark gray
    ];

    // For more than 16 relationships, generate additional colors with HSL spread
    if (index < distinctColors.length) {
        return distinctColors[index];
    } else {
        // For additional colors, use HSL with maximum separation
        const hue = (index * 137.5) % 360; // golden ratio to spread hues evenly
        const saturation = 75 + (index % 3) * 5; // high saturation for distinctiveness
        const lightness = 45 + (index % 4) * 5; // mid-range lightness for visibility
        return `hsl(${hue}, ${saturation}%, ${lightness}%)`;
    }
};

/**
 * Applies opacity to a hex color
 * @param hexColor Hex color string
 * @param opacity Opacity value (0-1)
 * @returns RGBA color string
 */
export const applyOpacity = (hexColor: string, opacity: number): string => {
    if (!hexColor.startsWith('#')) return hexColor;

    const rgbMatch = hexColor.match(/^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i);
    if (rgbMatch) {
        const r = parseInt(rgbMatch[1], 16);
        const g = parseInt(rgbMatch[2], 16);
        const b = parseInt(rgbMatch[3], 16);
        return `rgba(${r}, ${g}, ${b}, ${opacity})`;
    }

    return hexColor;
};

/**
 * Calculates perceived brightness of a color (0-255)
 * @param color Color string (hex, rgb, rgba)
 * @returns Brightness value (0-255)
 */
export const getColorBrightness = (color: string): number => {
    // Default brightness for non-parsable colors
    if (!color || typeof color !== 'string') return 200;

    let r = 0, g = 0, b = 0;

    if (color.startsWith('#')) {
        // Parse hex color
        const hex = color.substring(1);
        r = parseInt(hex.substring(0, 2), 16);
        g = parseInt(hex.substring(2, 4), 16);
        b = parseInt(hex.substring(4, 6), 16);
    } else if (color.startsWith('rgba')) {
        // Parse rgba color
        const rgba = color.match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*[\d.]+\)/);
        if (rgba) {
            r = parseInt(rgba[1]);
            g = parseInt(rgba[2]);
            b = parseInt(rgba[3]);
        } else {
            return 200;
        }
    } else if (color.startsWith('rgb')) {
        // Parse rgb color
        const rgb = color.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
        if (rgb) {
            r = parseInt(rgb[1]);
            g = parseInt(rgb[2]);
            b = parseInt(rgb[3]);
        } else {
            return 200;
        }
    } else if (color.startsWith('hsl')) {
        // For HSL colors, approximate brightness
        return 180; // Default to medium brightness
    }

    // Calculate perceived brightness using weighted average
    return (r * 0.299 + g * 0.587 + b * 0.114);
};