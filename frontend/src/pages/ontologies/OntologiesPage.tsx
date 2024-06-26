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
                header: 'Ontology',
                size: 50,
                Cell: ({row}) => {
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
                                {name ? <div>{name}</div> : null}
                            </div>
                        );
                    } else return ontoId;
                },
            },
            {
                accessorFn: (ontology) => ontology.getOntologyId().toUpperCase(),
                header: 'ID',
                size: 20,
                Cell: ({row}) => {
                    return (
                        <div style={{width: '50px'}}>
                            <div className="bg-link-default text-white rounded-md px-2 py-1 w-fit font-bold break-keep">
                                {row.original.getOntologyId().toUpperCase()}
                            </div>
                        </div>
                    );
                },
            },
            {
                accessorFn: (ontology) => ontology.getDescription(), //normal accessorKey
                header: 'Description',
                size: 300,
                enableGlobalFilter: false,
            },
            {
                accessorKey: 'actions',
                header: 'Actions',
                size: 20,
                enableGlobalFilter: false,
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
        initialState: { showGlobalFilter: true },
        muiSearchTextFieldProps: {
            placeholder: 'Search all ontologies...',
            sx: {minWidth: '18 rem'},
            variant: 'outlined',
        },
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
            },
        }),
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
