
import React from "react";
import { useState, useEffect } from "react";
import { get, getPaginated, Page } from "../../api";
import OlsDatatable, { Column } from "../../components/OlsDatatable";
import Spinner from "../../components/Spinner";
import Ontology from "../../model/Ontology";
import { useHistory } from "react-router-dom";

const columns: readonly Column[] = [
// {
//     name: 'Debug',
//     sortable: true,
//     selector: (ontology:Ontology) => JSON.stringify(ontology),
//     wrap: true
// },
{
	name: '',
	sortable: false,
	selector: (ontology:Ontology) =>
		ontology.getLogoURL() && <img width={50} src={ontology.getLogoURL()} />
},
{
	name: 'Name',
	sortable: true,
	selector: (ontology:Ontology) => ontology.getName(),
},
{
	name: 'Description',
	sortable: true,
	selector: (ontology:Ontology) => ontology.getDescription(),
}
]

export default function OntologyList() {

	let history = useHistory()

        return <OlsDatatable
            columns={columns}
            endpoint={`/api/v2/ontologies`}
            instantiateRow={(row) => new Ontology(row)}
	    onClickRow={(ontology:Ontology) => {
		history.push('/ontologies/' + ontology.getOntologyId())
	    }}
        />
}
