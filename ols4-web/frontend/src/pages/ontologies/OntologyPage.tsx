

import { Box, Breadcrumbs, Button, ButtonGroup, Link, Tab, Tabs, Tooltip, Typography } from '@mui/material'
import React, { Fragment, useEffect, useState } from 'react'
import { Redirect } from 'react-router-dom'
import { get } from '../../api'
import Header from '../../components/Header'
import Spinner from '../../components/Spinner'
import Ontology from '../../model/Ontology'
import { Link as RouterLink } from 'react-router-dom'
import { IconButton } from '@mui/material';
import { AccountTree } from '@mui/icons-material'
import FormatListBulletedIcon from '@mui/icons-material/FormatListBulleted';
import TermList from './TermList'
import TermTree from './TermTree'

export default function OntologyPage(props:{ontologyId:string}) {

    let { ontologyId } = props

    let [ ontology, setOntology ] = useState<Ontology|undefined>(undefined)
    let [ tab, setTab ] = useState<'terms'|'classes'|'properties'|'individuals'>('classes')
    let [ viewMode, setViewMode ] = useState<'tree'|'list'>('tree')

    useEffect(() => {

	async function fetchOntology() {

		let ontologyProperties = await get<any>(`/api/v2/ontologies/${ontologyId}`)

		setOntology(
			new Ontology(ontologyProperties)
		)
	}

	fetchOntology()

    }, [])


    return   <Fragment>
    <Header section='ontologies' />
    <main>

	{ renderOntologyPage() }

    </main>
</Fragment>


function renderOntologyPage() {

    if(!ontology) {
	return <Spinner/>
    }

    return  <Fragment>

	<Breadcrumbs>
		<Link color="inherit" component={RouterLink} to="/ontologies">
			Ontologies
		</Link>
		<Typography color="textPrimary">{ontology.getName()}</Typography>
	</Breadcrumbs>

	<h1>{ontology!.getName()}</h1>

	<Box>
		<p>
		{ontology!.getDescription()}
		</p>
	</Box>


	<Tabs
                indicatorColor="primary"
                textColor="primary"
                value={tab}
                onChange={(e, tab) => setTab(tab)}
            >
                <Tab label={`All Terms (${ontology.getNumTerms().toLocaleString()})`} value='terms' disabled={ontology.getNumTerms()==0} />
                <Tab label={`Classes (${ontology.getNumClasses().toLocaleString()})`} value='classes' disabled={ontology.getNumClasses()==0} />
                <Tab label={`Properties (${ontology.getNumProperties().toLocaleString()})`} value='properties' disabled={ontology.getNumProperties()==0} />
                <Tab label={`Individuals (${ontology.getNumIndividuals().toLocaleString()})`} value='individuals' disabled={ontology.getNumIndividuals()==0} />
            </Tabs>

<br/>
	<ButtonGroup variant="contained" aria-label="outlined primary button group">
		<Tooltip title="Tree view" placement="top">
		  <Button
			variant={viewMode === 'tree' ? 'contained' : 'outlined'}
			onClick={() => setViewMode('tree')}
			>
			<AccountTree />
		  </Button>
		  </Tooltip>
		<Tooltip title="List view" placement="top">
		  <Button
			variant={viewMode === 'list' ? 'contained' : 'outlined'}
			onClick={() => setViewMode('list')}
			>
			<FormatListBulletedIcon />
		   </Button>
		   </Tooltip>
	  </ButtonGroup>

	<br/>

<Box py={2}>
	{ viewMode === 'list' ?
		<TermList ontologyId={ontologyId} termType={tab} />
		: <TermTree ontologyId={ontologyId} termType={tab} />
	}
	</Box>

</Fragment>


}

}


