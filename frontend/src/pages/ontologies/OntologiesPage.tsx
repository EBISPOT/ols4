import {useEffect, useMemo} from "react";
import {useNavigate} from "react-router-dom";
import urlJoin from "url-join";
import {useAppDispatch, useAppSelector} from "../../app/hooks";
import Header from "../../components/Header";
import LoadingOverlay from "../../components/LoadingOverlay";
import Ontology from "../../model/Ontology";
import {getAllOntologies} from "./ontologiesSlice";
import {MaterialReactTable, MRT_ColumnDef, useMaterialReactTable} from "material-react-table";

export default function OntologiesPage() {
    const dispatch = useAppDispatch();
    const ontologies = useAppSelector((state) => state.ontologies.ontologies);

    useEffect(() => {
        dispatch(getAllOntologies());
    }, [dispatch]);
    const loading = useAppSelector((state) => state.ontologies.loadingOntologies);

    const navigate = useNavigate();

    const columns = useMemo<MRT_ColumnDef<Ontology>[]>(
        () => [
            {
                accessorFn: (ontology) => ontology.getName(), //access nested data with dot notation
                id: 'name',
                header: 'Ontology',
                size: 50,
                filterFn: 'includesString',
                Cell: ({row, renderedCellValue}) => {
                    const name = row.original.getName();
                    const logo = row.original.getLogoURL();
                    const ontoId = row.original.getOntologyId();
                    if (name || logo) {
                        return (
                            <div>
                                {logo ? (
                                    <img
                                        alt={`${ontoId.toUpperCase()} logo`}
                                        title={`${ontoId.toUpperCase()} logo`}
                                        className="h-16 object-contain bg-white rounded-lg p-1 mb-3"
                                        src={
                                            logo.startsWith("/images")
                                                ? process.env.REACT_APP_OBO_FOUNDRY_REPO_RAW + logo
                                                : logo
                                        }
                                    />
                                ) : null}
                                <div>{renderedCellValue}</div>
                            </div>
                        );
                    } else return ontoId;
                },
            },
            {
                accessorFn: (ontology) => ontology.getOntologyId().toUpperCase(),
                id: 'id',
                header: 'ID',
                size: 20,
                filterFn: 'startsWith',
                Cell: ({row, renderedCellValue}) => {
                    return (
                        <div style={{width: '50px'}}>
                            <div className="bg-link-default text-white rounded-md px-2 py-1 w-fit font-bold break-keep">
                                {renderedCellValue}
                            </div>
                        </div>
                    );
                },
            },
            {
                accessorFn: (ontology) => ontology.getDescription(), //normal accessorKey
                id: 'description',
                header: 'Description',
                size: 300,
                filterFn: 'includesString',
            },
            {
                accessorKey: 'actions',
                header: 'Actions',
                size: 20,
                enableGlobalFilter: false,
                enableColumnFilter: false,
                enableSorting: false,
                enableColumnActions: false,
                Cell: ({row}) => {
                    return (
                        <div>
                            <div
                                onClick={() => {
                                    navigate(`/ontologies/${row.original.getOntologyId()}`);
                                }}
                                className="link-default"
                            >
                                Search
                            </div>
                            <a
                                href={urlJoin(
                                    process.env.PUBLIC_URL!,
                                    `/ontologies/${row.original.getOntologyId()}?tab=classes`
                                )}
                                className="link-default"
                            >
                                Classes
                            </a>
                            <br/>
                            <a
                                href={urlJoin(
                                    process.env.PUBLIC_URL!,
                                    `/ontologies/${row.original.getOntologyId()}?tab=properties`
                                )}
                                className="link-default"
                            >
                                Properties
                            </a>
                            <br/>
                            <a
                                href={urlJoin(
                                    process.env.PUBLIC_URL!,
                                    `/ontologies/${row.original.getOntologyId()}?tab=individuals`
                                )}
                                className="link-default"
                            >
                                Individuals
                            </a>
                        </div>
                    );
                },
            },
        ],
        [],
    );

    const table = useMaterialReactTable({
        columns,
        data: ontologies,
        initialState: {
            showColumnFilters: true,
            sorting: [
                {
                    id: 'id', //sort by id by default on page load
                    desc: false,
                },
            ],
        },
        enableFilterMatchHighlighting: true,
        enableGlobalFilter: false,
        enableFullScreenToggle: false,
        enableDensityToggle: false,
        enableHiding: false,
        enableTopToolbar: false,
        muiTableHeadCellProps: {
            sx: {
                fontWeight: 'bold',
                fontSize: '16px',
                fontFamily: '"IBM Plex Sans",Helvetica,Arial,sans-serif',
            },
        },
        muiTableBodyCellProps: {
            sx: {
                fontWeight: 'normal',
                fontFamily: '"IBM Plex Sans",Helvetica,Arial,sans-serif',
            },
        },
        muiTablePaperProps: {
            elevation: 0,
            sx: {
                borderRadius: '0',
            },
        },
        muiTableBodyProps: {
            sx: {
                //stripe the rows, make odd rows a darker color
                '& tr:nth-of-type(even) > td': {
                    backgroundColor: '#EDECE5',
                },
            },
        },
        muiTableBodyRowProps: ({row}) => ({
            onClick: (event) => {
                navigate(`/ontologies/${row.original.getOntologyId()}`);
            },
            sx: {
                cursor: 'pointer',
                textAlign: 'left',
                verticalAlign: 'top',
            },
        }),
        paginationDisplayMode: 'pages',
        muiPaginationProps: {
            color: 'primary',
            rowsPerPageOptions: [10, 20, 30, 50],
            shape: 'rounded',
            variant: 'outlined',
        },
    });

    document.title = "Ontology Lookup Service (OLS)";
    return (
        <div>
            <Header section="ontologies"/>
            <main className="container mx-auto my-8">
                {
                    <MaterialReactTable table={table}/>
                }
                {loading ? <LoadingOverlay message="Loading ontologies..."/> : null}
            </main>
        </div>
    );
}