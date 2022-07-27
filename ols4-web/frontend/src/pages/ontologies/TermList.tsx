


import React from "react";
import Term from "../../model/Term";
import OlsDatatable, { Column } from "../../components/OlsDatatable";
import { termFromProperties } from "../../model/fromProperties";


export default function TermList(props:{
	ontologyId:string
	termType:'terms'|'classes'|'properties'|'individuals'
}) {
	let { ontologyId, termType } = props

        return <OlsDatatable
            columns={columns}
            endpoint={`/api/v2/ontologies/${ontologyId}/${termType}`}
            instantiateRow={(row) => termFromProperties(row)}
	    onClickRow={(term:Term) => {
	    }}
        />
}

const columns: readonly Column[] = [
	{
		name: 'Name',
		sortable: true,
		selector: (term: Term) => term.getName(),
	}
]
