import { useState, useMemo } from "react";
import { randomString } from "../app/util";
import { Pagination } from "./Pagination";

interface PropertyValuesListProps<T> {
  values: T[];
  renderValue: (value: T, index: number) => JSX.Element;
  threshold?: number;
  itemsPerPage?: number;
  searchFilter?: (value: T, searchQuery: string) => boolean;
  title?: string;
}

export default function PropertyValuesList<T>({
  values,
  renderValue,
  threshold = 25,
  itemsPerPage = 25,
  searchFilter,
  title,
}: PropertyValuesListProps<T>) {
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [currentPage, setCurrentPage] = useState<number>(0);

  // Filter values based on search query
  const filteredValues = useMemo(() => {
    if (!searchQuery.trim() || !searchFilter) {
      return values;
    }
    return values.filter((value) => searchFilter(value, searchQuery.toLowerCase()));
  }, [values, searchQuery, searchFilter]);

  // Calculate pagination
  const totalPages = Math.ceil(filteredValues.length / itemsPerPage);
  const startIndex = currentPage * itemsPerPage;
  const endIndex = Math.min(startIndex + itemsPerPage, filteredValues.length);
  const currentPageValues = filteredValues.slice(startIndex, endIndex);

  // Reset to first page when search changes
  const handleSearchChange = (query: string) => {
    setSearchQuery(query);
    setCurrentPage(0);
  };

  const handlePageChange = (page: number) => {
    setCurrentPage(Math.max(0, Math.min(page, totalPages - 1)));
  };

  // Only show the enhanced view if values exceed threshold
  if (values.length <= threshold) {
    return (
      <div>
        {title && <div className="font-bold">{title}</div>}
        {values.length === 1 ? (
          <p>{renderValue(values[0], 0)}</p>
        ) : (
          <ul className="list-disc list-inside">
            {values.map((value, index) => (
              <li key={randomString()}>{renderValue(value, index)}</li>
            ))}
          </ul>
        )}
      </div>
    );
  }

  return (
    <div className="border border-neutral-200 rounded-md p-3 space-y-3 bg-neutral-50">
      {title && <div className="font-bold">{title}</div>}

      {searchFilter && (
        <div className="mb-3">
          <input
            type="text"
            placeholder="Search values..."
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            className="w-full px-3 py-2 border border-neutral-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-link-default focus:border-transparent bg-white"
          />
        </div>
      )}

      <div className="text-sm text-neutral-600 mb-2">
        Showing {startIndex + 1}-{endIndex} of {filteredValues.length} values
        {searchQuery && ` (filtered from ${values.length} total)`}
      </div>

      {currentPageValues.length === 0 ? (
        <p className="text-neutral-500 italic">No values found matching your search.</p>
      ) : (
        <ul className="list-disc list-inside space-y-1">
          {currentPageValues.map((value, index) => (
            <li key={randomString()}>
              {renderValue(value, startIndex + index)}
            </li>
          ))}
        </ul>
      )}

      {totalPages > 1 && (
        <div className="mt-4">
          <Pagination
            page={currentPage}
            onPageChange={handlePageChange}
            dataCount={filteredValues.length}
            rowsPerPage={itemsPerPage}
          />
        </div>
      )}
    </div>
  );
}