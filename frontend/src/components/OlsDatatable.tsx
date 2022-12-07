import { KeyboardArrowDown } from "@mui/icons-material";
import { useEffect, useState } from "react";
import { randomString } from "../app/util";
import Thing from "../model/Thing";

export interface Column {
  name: string;
  minWidth?: number;
  align?: "right";
  selector: (row: any) => any;
  sortable: boolean;
}

export default function OlsDatatable({
  columns,
  data,
  dataCount,
  onSelectRow,
  page,
  rowsPerPage,
  onPageChange,
  onRowsPerPageChange,
  onFilter,
}: {
  columns: readonly Column[];
  data: any[];
  dataCount: number;
  onSelectRow: (row: any) => void;
  page?: number;
  rowsPerPage: number;
  onPageChange?: (page: number) => void;
  onRowsPerPageChange?: (rowsPerPage: number) => void;
  onFilter?: (key: string) => void;
}) {
  // const [sortColumn, setSortColumn] = useState<string>("");
  // const [sortDirection, setSortDirection] = useState<string>("asc");
  const [pageCount, setPageCount] = useState<number>(
    Math.ceil(dataCount / rowsPerPage) || 1
  );

  useEffect(() => {
    setPageCount(Math.ceil(dataCount / rowsPerPage));
  }, [rowsPerPage, dataCount]);

  return (
    <div>
      <div className="grid grid-cols-2 mb-2">
        {rowsPerPage !== undefined &&
        rowsPerPage > 0 &&
        onRowsPerPageChange !== undefined ? (
          <div className="justify-self-start px-4">
            <div className="flex group relative">
              <label className="self-center px-3">Show</label>
              <select
                className="input-default text-md appearance-none pr-8"
                onChange={(e) => {
                  onRowsPerPageChange(parseInt(e.target.value));
                }}
              >
                <option value={10}>10</option>
                <option value={25}>25</option>
                <option value={100}>100</option>
              </select>
              <div className="absolute right-2 top-2 z-10 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
                <KeyboardArrowDown fontSize="medium" />
              </div>
            </div>
          </div>
        ) : null}
        {onFilter !== undefined ? (
          <div className="justify-self-end group relative w-3/4 px-4">
            <input
              type="text"
              placeholder="Search ontologies..."
              className="input-default text-md pl-10"
              onChange={(e) => {
                onFilter(e.target.value);
              }}
            />
            <div className="absolute left-7 top-2 z-10">
              <i className="icon icon-common icon-search text-xl text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark" />
            </div>
          </div>
        ) : null}
      </div>
      <div className="mx-4">
        <table className="border-collapse border-spacing-1 w-full mb-2">
          <thead>
            <tr key={randomString()} className="border-b-2 border-grey-default">
              {columns.map((column) => (
                <td
                  className="text-lg text-left font-bold py-2 px-4"
                  key={column.name}
                >
                  {column.name}
                </td>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.map((row: Thing) => {
              return (
                <tr
                  tabIndex={-1}
                  key={randomString()}
                  onClick={() => {
                    onSelectRow(row);
                  }}
                  className="even:bg-grey-50 cursor-pointer"
                >
                  {columns.map((column: any) => {
                    return (
                      <td
                        className="text-sm align-top py-2 px-4"
                        key={randomString()}
                      >
                        {column.selector(row)
                          ? column.selector(row)
                          : "(no data)"}
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
        {page !== undefined &&
        page >= 0 &&
        onPageChange !== undefined &&
        rowsPerPage !== undefined &&
        rowsPerPage > 0 ? (
          <div className="flex justify-center p-2 gap-2">
            <button
              onClick={() => {
                onPageChange(page - 1);
              }}
              disabled={page === 0}
              className={`px-4 py-1 text-neutral-default hov ${
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
                    ? "bg-neutral-default rounded-md text-white"
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
              <span className="px-4 py-1 bg-neutral-default rounded-md text-white">
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
                    ? "bg-neutral-default rounded-md text-white"
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
        ) : null}
      </div>
    </div>
  );
}
