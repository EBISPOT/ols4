import Search from "@mui/icons-material/Search";
import { Grid, Input, InputAdornment, TableHead } from "@mui/material";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import { Fragment } from "react";

export interface Column {
  name: string;
  minWidth?: number;
  align?: "right";
  selector: (row: any) => any;
  sortable: boolean;
}

export interface Props {
  columns: readonly Column[];
  data: any[];
  onSelectRow: (row: any) => void;
  page?: number;
  rowsPerPage?: number;
  onPageChange?: (page: number) => void;
  onRowsPerPageChange?: (rowsPerPage: number) => void;
  onFilter?: (key: string) => void;
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
}: Props) {
  // const [sortColumn, setSortColumn] = useState<string>("");
  // const [sortDirection, setSortDirection] = useState<string>("asc");

  return (
    <Fragment>
      <Grid container>
        {onFilter != undefined ? (
          <Grid item xs={6}>
            <Input
              startAdornment={
                <InputAdornment position="start">
                  <Search />
                </InputAdornment>
              }
              onChange={(e) => {
                onFilter(e.target.value);
              }}
            />
          </Grid>
        ) : null}
        {page != undefined &&
        page >= 0 &&
        onPageChange != undefined &&
        rowsPerPage != undefined &&
        rowsPerPage > 0 &&
        onRowsPerPageChange != undefined ? (
          <Grid item xs={6} container>
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
                let newRowsPerPage = parseInt(e.target.value);
                console.log("Set rows per page to " + newRowsPerPage);
                onRowsPerPageChange(newRowsPerPage);
              }}
            />
          </Grid>
        ) : null}
      </Grid>

      <TableContainer sx={{ maxHeight: 440 }}>
        <Table size="small" stickyHeader aria-label="sticky table">
          <TableHead>
            <TableRow>
              {columns.map((column) => (
                <TableCell
                  key={column.name}
                  align={column.align}
                  style={{ minWidth: column.minWidth }}
                >
                  {column.name}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {data.map((row: any) => {
              return (
                <TableRow
                  hover
                  tabIndex={-1}
                  key={row.code}
                  onClick={() => {
                    onSelectRow(row);
                  }}
                  style={{ cursor: "pointer" }}
                >
                  {columns.map((column: any) => {
                    return (
                      <TableCell key={column.name} align={column.align}>
                        {column.selector(row)}
                      </TableCell>
                    );
                  })}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </Fragment>
  );
}
