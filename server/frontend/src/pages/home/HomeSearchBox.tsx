import { Autocomplete, Grid, TextField, CircularProgress, Stack } from "@mui/material"
import React, { useEffect, useState } from "react"
import { get, getPaginated } from "../../api"
import Ontology from "../../model/Ontology"
import { thingFromProperties } from "../../model/fromProperties"
import Thing from "../../model/Thing"

export default function HomeSearchBox(props) {
	 
	let [ open, setOpen ] = useState<boolean>(false)
	let [ loading, setLoading ] = useState<boolean>(false)
	let [ query, setQuery ] = useState<string>('')
	let [ options, setOptions ] = useState<Thing[]>([])

	async function doSearch() {

		let search = '*' + query + '*'

		let [ entities, ontologies ] = await Promise.all([
			getPaginated<any>(`/api/v2/entities?${new URLSearchParams({
				search: search,
				size: '10'
			})}`),
			getPaginated<any>(`/api/v2/ontologies?${new URLSearchParams({
				search: search,
				size: '3'
			})}`)
		])

		setOptions([
			...ontologies.elements.map(obj => new Ontology(obj)),
			...entities.elements.map(obj => thingFromProperties(obj))
		])
	}

	useEffect(() => {

		doSearch()

	}, [ query ])

        return (
            <Autocomplete
            id="asynchronous-demo"
            style={{ width: 500 }}
            open={open}
            onOpen={() => { setOpen(true) }}
            onClose={() => { setOpen(false) }}
            onChange={(e, option) => {}}
            // getOptionSelected={(option:OlsSearchResult, value:OlsSearchResult) => option.iri === value.iri}
            getOptionLabel={(option:Thing) => option.getId()}
            renderOption={(props, option:Thing) =>
<Stack
  direction="row"
  justifyContent="space-between"
//   alignItems="center"
>
                
                <span>{ truncate(option.getName(), 40) }</span>
		
		
		{ !(option instanceof Ontology) && 
		<span style={{
                backgroundColor: '#1976d2',
                padding: '0 10px',
                lineHeight: '1.5',
                fontSize: '.875rem',
                color: '#fff',
                verticalAlign: 'middle',
                whiteSpace: 'nowrap',
                textAlign: 'center',
                borderRadius: '0.6rem',
                textTransform: 'uppercase',
            }}>{option.getOntologyId()}</span>
	    }
            
            </Stack>
        
        }
            filterOptions={x => x}
            options={options}
            loading={loading}
            renderInput={(params) => (
                <TextField
                {...params}
                label="Search..."
                variant="outlined"
                value={query}
                onChange={(e) => { setQuery(e.target.value) }}
                InputProps={{
                    ...params.InputProps,
                    endAdornment: (
                    <React.Fragment>
                        {loading ? <CircularProgress color="inherit" size={20} /> : null}
                        {params.InputProps.endAdornment}
                    </React.Fragment>
                    ),
                }}
                />
            )}
            />
        )

    }



    function truncate(str, len) {

	if(str.length > len) {
		return str.substr(0, len) + '...'
	} else {
		return str
	}
    }