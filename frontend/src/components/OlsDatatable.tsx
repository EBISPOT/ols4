import Search from "@mui/icons-material/Search";
import { InputAdornment, TextField } from "@mui/material";
import TablePagination from "@mui/material/TablePagination";
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
  onSelectRow,
  page,
  rowsPerPage,
  onPageChange,
  onRowsPerPageChange,
  onFilter,
}: {
  columns: readonly Column[];
  data: any[];
  onSelectRow: (row: any) => void;
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
      <div className="grid grid-cols-2 mb-4">
        {onFilter != undefined ? (
          <div className="w-3/4 px-4">
            <TextField
              fullWidth
              size="small"
              margin="dense"
              onChange={(e) => {
                onFilter(e.target.value);
              }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Search />
                  </InputAdornment>
                ),
              }}
            ></TextField>
          </div>
        ) : null}
        {page != undefined &&
        page >= 0 &&
        onPageChange != undefined &&
        rowsPerPage != undefined &&
        rowsPerPage > 0 &&
        onRowsPerPageChange != undefined ? (
          <div>
            <TablePagination
              rowsPerPageOptions={[]}
              // rowsPerPageOptions={[10, 25, 100]}
              component="div"
              count={data.length}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={(e, page) => {
                console.log("Set page to " + page);
                onPageChange(page);
              }}
              onRowsPerPageChange={(e) => {
                const newRowsPerPage = parseInt(e.target.value);
                console.log("Set rows per page to " + newRowsPerPage);
                onRowsPerPageChange(newRowsPerPage);
              }}
            />
          </div>
        ) : null}
      </div>

      <div className="m-6">
        <table className="border-collapse border-spacing-1 w-full">
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
                        key={column.selector(row)}
                      >
                        {column.selector(row) ? column.selector(row) : "(no data)"}
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
