import { useEffect, useState } from "react";

export function Pagination({
  page,
  onPageChange,
  dataCount,
  rowsPerPage,
}: {
  page: number;
  onPageChange: (value: number) => void;
  dataCount: number;
  rowsPerPage: number;
}) {
  const [pageCount, setPageCount] = useState<number>(
    Math.ceil(dataCount / rowsPerPage) || 1
  );

  useEffect(() => {
    setPageCount(Math.ceil(dataCount / rowsPerPage));
  }, [rowsPerPage, dataCount]);

  return (
    <div className="flex justify-center p-2 gap-2">
      <button
        onClick={() => {
          onPageChange(page - 1);
        }}
        disabled={page === 0}
        className={`px-4 py-1 text-neutral-default ${
          page === 0
            ? "cursor-not-allowed"
            : "hover:bg-neutral-default hover:rounded-md hover:text-white"
        }`}
      >
        Previous
      </button>
      {pageCount > 0 ? (
        <button
          className={`px-4 py-1 ${
            page === 0
              ? "bg-neutral-default rounded-md text-white cursor-default"
              : "text-neutral-default hover:bg-neutral-default hover:rounded-md hover:text-white"
          }`}
          onClick={() => {
            onPageChange(0);
          }}
        >
          1
        </button>
      ) : null}
      {pageCount > 2 && page === 0 ? (
        <button
          className="px-4 py-1 text-neutral-default hover:bg-neutral-default hover:rounded-md hover:text-white"
          onClick={() => {
            onPageChange(1);
          }}
        >
          2
        </button>
      ) : null}
      {page <= pageCount - 1 && page - 1 > 1 ? (
        <span className="py-1 text-neutral-default">...</span>
      ) : null}
      {page + 1 <= pageCount - 1 && page - 1 > 0 ? (
        <button
          className="px-4 py-1 text-neutral-default hover:bg-neutral-default hover:rounded-md hover:text-white"
          onClick={() => {
            onPageChange(page - 1);
          }}
        >
          {page}
        </button>
      ) : null}
      {page > 0 && page < pageCount - 1 ? (
        <span className="px-4 py-1 bg-neutral-default rounded-md text-white cursor-default">
          {page + 1}
        </span>
      ) : null}
      {page >= 1 && page + 1 < pageCount - 1 ? (
        <button
          className="px-4 py-1 text-neutral-default hover:bg-neutral-default hover:rounded-md hover:text-white"
          onClick={() => {
            onPageChange(page + 1);
          }}
        >
          {page + 2}
        </button>
      ) : null}
      {page >= 0 && page + 1 < pageCount - 2 ? (
        <span className="py-1 text-neutral-default">...</span>
      ) : null}
      {pageCount > 2 && page === pageCount - 1 ? (
        <button
          className="px-4 py-1 text-neutral-default hover:bg-neutral-default hover:rounded-md hover:text-white"
          onClick={() => {
            onPageChange(pageCount - 2);
          }}
        >
          {pageCount - 1}
        </button>
      ) : null}
      {pageCount > 1 ? (
        <button
          className={`px-4 py-1 ${
            page === pageCount - 1
              ? "bg-neutral-default rounded-md text-white cursor-default"
              : "text-neutral-default hover:bg-neutral-default hover:rounded-md hover:text-white"
          }`}
          onClick={() => {
            onPageChange(pageCount - 1);
          }}
        >
          {pageCount}
        </button>
      ) : null}
      <button
        onClick={() => {
          onPageChange(page + 1);
        }}
        disabled={page === pageCount - 1}
        className={`px-4 py-1 text-neutral-default ${
          page === pageCount - 1
            ? "cursor-not-allowed"
            : "hover:bg-neutral-default hover:rounded-md hover:text-white"
        }`}
      >
        Next
      </button>
    </div>
  );
}
