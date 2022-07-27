

import { Box } from '@mui/material'
import React, { Fragment } from 'react'
import { Redirect } from 'react-router-dom'
import Header from '../../components/Header'
import OntologyList from './OntologyList'

interface Props {
}

export default function OntologiesPage(props:Props) {

    return <Fragment>
        <Header section='ontologies' />
        <main>
            <Box>
            <OntologyList />
            </Box>
        </main>
    </Fragment>
}

