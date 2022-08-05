


import React from "react";
import OlsDatatable, { Column } from "../../components/OlsDatatable";
import Entity from "../../model/Entity";
import { thingFromProperties } from "../../model/fromProperties";


export default function EntityList(props:{
	ontologyId:string
	entityType:'entities'|'classes'|'properties'|'individuals'
}) {
	let { ontologyId, entityType } = props

        return <OlsDatatable
            columns={columns}
            endpoint={`/api/v2/ontologies/${ontologyId}/${entityType}`}
            instantiateRow={(row) => thingFromProperties(row)}
	    onClickRow={(entity:Entity) => {
	    }}
        />
}

const columns: readonly Column[] = [
	{
		name: 'Name',
		sortable: true,
		selector: (entity:Entity) => entity.getName(),
	}
]
