import { KeyboardArrowDown } from "@mui/icons-material";
import { randomString } from "../app/util";
import Thing from "../model/Thing";
import { Pagination } from "./Pagination";

export interface Column {
  name: string;
  minWidth?: number;
  align?: "right";
  selector: (row: any) => any;
  sortable: boolean;
}

export default function DataTable({
  columns,
  data,
  dataCount,
  placeholder,
  onSelectRow,
  page,
  rowsPerPage,
  onPageChange,
  onRowsPerPageChange,
  onFilter,
}: {
  columns: readonly Column[];
  data: any[];
  dataCount?: number;
  placeholder?: string;
  onSelectRow?: (row: any) => void;
  page?: number;
  rowsPerPage?: number;
  onPageChange?: (page: number) => void;
  onRowsPerPageChange?: (rowsPerPage: number) => void;
  onFilter?: (key: string) => void;
}) {
  // const [sortColumn, setSortColumn] = useState<string>("");
  // const [sortDirection, setSortDirection] = useState<string>("asc");

  return (
    <div>
      <div className="grid grid-cols-2">
        {rowsPerPage !== undefined &&
        rowsPerPage > 0 &&
        onRowsPerPageChange !== undefined ? (
          <div className="justify-self-start px-2 mb-2">
            <div className="flex group relative text-md">
              <label className="self-center px-3">Show</label>
              <select
                className="input-default appearance-none pr-7 z-20 bg-transparent"
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
          <div className="justify-self-end group relative w-3/4 px-2 mb-2">
            <input
              type="text"
              placeholder={placeholder ? placeholder : "Search table..."}
              className="input-default text-md pl-10"
              onChange={(e) => {
                onFilter(e.target.value);
              }}
            />
            <div className="absolute left-5 top-2 z-10">
              <i className="icon icon-common icon-search text-xl text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark" />
            </div>
          </div>
        ) : null}
      </div>
      <div className="mx-2 overflow-x-auto">
        <table className="table-auto border-collapse border-spacing-1 w-full mb-2">
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
                    if (onSelectRow) onSelectRow(row);
                  }}
                  className={`even:bg-grey-50 ${
                    onSelectRow ? "cursor-pointer" : ""
                  }`}
                >
                  {columns.map((column: any) => {
                    return (
                      <td
                        className="text-md align-top py-2 px-4"
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
      </div>
      {page !== undefined &&
      page >= 0 &&
      onPageChange !== undefined &&
      rowsPerPage !== undefined &&
      rowsPerPage > 0 ? (
        <Pagination
          page={page}
          onPageChange={onPageChange}
          rowsPerPage={rowsPerPage}
          dataCount={dataCount || 0}
        ></Pagination>
      ) : null}
    </div>
  );
}
