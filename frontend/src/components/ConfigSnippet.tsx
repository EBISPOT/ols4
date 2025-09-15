import React, { useState } from 'react';

interface ConfigSnippetProps {
  title: string;
  config: string;
  description?: string;
}

export default function ConfigSnippet({ title, config, description }: ConfigSnippetProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(config);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy text: ', err);
    }
  };

  return (
    <div className="mb-6 border border-gray-200 rounded-lg overflow-hidden">
      <div className="bg-gray-50 px-4 py-3 border-b border-gray-200">
        <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
        {description && (
          <p className="text-sm text-gray-600 mt-1">{description}</p>
        )}
      </div>
      <div className="relative">
        <pre className="bg-gray-900 text-gray-100 p-4 overflow-x-auto text-sm">
          <code>{config}</code>
        </pre>
        <button
          type="button"
          onClick={handleCopy}
          className={`absolute top-2 right-2 px-3 py-1 text-xs font-medium rounded transition-colors ${
            copied
              ? 'bg-green-600 text-white'
              : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
          }`}
        >
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>
    </div>
  );
}