import React from 'react';

export const LoadingOverlay: React.FC = () => (
    <div className="absolute inset-0 flex items-center justify-center bg-white bg-opacity-70 z-10">
        <div className="flex flex-col items-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500"></div>
            <div className="mt-2 text-orange-500 font-semibold">Loading graph...</div>
        </div>
    </div>
);