import { Fragment, useEffect, useState } from "react";
import { getPaginated, Page } from "../api";
import Spinner from "./Spinner";
import * as React from 'react';
import { useTheme } from '@mui/material/styles';
import Box from '@mui/material/Box';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableFooter from '@mui/material/TableFooter';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import IconButton from '@mui/material/IconButton';
import FirstPageIcon from '@mui/icons-material/FirstPage';
import KeyboardArrowLeft from '@mui/icons-material/KeyboardArrowLeft';
import KeyboardArrowRight from '@mui/icons-material/KeyboardArrowRight';
import Search from '@mui/icons-material/Search';
import LastPageIcon from '@mui/icons-material/LastPage';
import { Grid, Input, InputAdornment, TableHead } from "@mui/material";


export interface Column {
	name: string
	minWidth?: number;
	align?: 'right';
	selector: (row: any) => any;
	sortable:boolean
}

export interface Props {
    columns:readonly Column[]
    endpoint:string
    instantiateRow:((any)=>any)
    onClickRow?:(any)=>void
}

export default function OlsDatatable(props:Props) {

    let { columns, endpoint, instantiateRow, onClickRow } = props

    let [ page, setPage ] = useState<number>(0)
    let [ rowsPerPage, setRowsPerPage ] = useState<number>(10)
    let [ sortColumn, setSortColumn ] = useState<string>('')
    let [ sortDirection, setSortDirection ] = useState<string>('asc')
    let [ filter, setFilter ] = useState<string>('')
    let [ loading, setLoading ] = useState<boolean>(false)
    let [ data, setData ] = useState<Page<any>|null>(null)

    useEffect(() => {

        fetchData()

    }, [ page, rowsPerPage, filter, columns, endpoint ])

    async function fetchData() {

        if(loading)
            return

        setLoading(true)

        let data = (await getPaginated<any>(endpoint + `?page=${page}&size=${rowsPerPage}`))
            .map(o => instantiateRow(o))

        setData(data)
        setLoading(false)
    }

    if(!data) {
        return <Spinner/>
    }

  return (
	<Fragment>

<Grid container>
			<Grid item xs={6}>
				<Input startAdornment={<InputAdornment position="start"><Search /></InputAdornment>}
					onChange={(e) => { setFilter(e.target.value) }} />
			</Grid>
			<Grid item xs={6} container>
      <TablePagination
      rowsPerPageOptions={[]}
        // rowsPerPageOptions={[10, 25, 100]}
        component="div"
        count={data.totalElements}
        rowsPerPage={rowsPerPage}
        page={page}
        onPageChange={async (ev, page) => {

		console.log("Set page to " + page)

		setPage(page)
	}}
        onRowsPerPageChange={async (ev) => {

		let newRowsPerPage = parseInt(ev.target.value)
		console.log("Set rows per page to " + newRowsPerPage)

		setRowsPerPage(newRowsPerPage)
	}}
      />
			</Grid>
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
            {data.elements
              .map((row) => {
                return (
                  <TableRow hover
		  	tabIndex={-1}
			key={row.code}
			onClick={onClickRow && (() => onClickRow!(row))}
			style={{cursor:'pointer'}}

			>
                    {columns.map((column) => {
                      return (
                        <TableCell key={column.name} align={column.align}>
			{ column.selector(row) }
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

