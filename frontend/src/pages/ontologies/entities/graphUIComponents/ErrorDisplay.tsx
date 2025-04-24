import React from 'react';

interface ErrorDisplayProps {
    error: string | null;
}

export const ErrorDisplay: React.FC<ErrorDisplayProps> = ({ error }) => (
    <div className="border border-red-300 bg-red-50 p-4 rounded-md text-red-700">
        <p>{error}</p>
        <p className="text-sm mt-2">
            Try refreshing the page or checking the browser console for more details.
        </p>
    </div>
);