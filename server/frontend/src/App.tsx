import React, { Fragment, useState } from 'react';
import { BrowserRouter, Link, Redirect, Route, RouteComponentProps, Switch, withRouter } from "react-router-dom";
import logo from './logo.svg';
import './App.css';
import { AppBar, createStyles, Tab, Tabs, Theme, withStyles } from '@mui/material';

import Home from './pages/home/Home'
import ProjectsPage from './pages/ontologies/OntologiesPage';
import About from './pages/About';
import Help from './pages/help/HelpPage';
import { get } from './api';
import OntologiesPage from './pages/ontologies/OntologiesPage';
import OntologyPage from './pages/ontologies/OntologyPage';
import EntityPage from './pages/ontologies/EntityPage';

let styles = (theme:Theme) => createStyles({
    main: {
      padding: theme.spacing(3),
      [theme.breakpoints.down('xs')]: {
        padding: theme.spacing(2),
      },
    },
  });

interface Props {
}

interface State {
}
  
class App extends React.Component<Props, State> {

	constructor(props:Props) {
		super(props)

		this.state = {
		}
	}

	componentDidMount() {

	}

	render() {


		return (
			<BrowserRouter basename={process.env.PUBLIC_URL}>
					<Switch>
					<Route exact path={`/`} component={Home} />

					<Route exact path={`/ontologies`} component={OntologiesPage} />

					<Route exact path={`/ontologies/:id`}
						component={(props:any) => <OntologyPage ontologyId={props.match.params.id}/>}></Route>

					<Route exact path={`/ontologies/:id/classes/:uri`}
						component={(props:any) =>
							<EntityPage
								ontologyId={props.match.params.id}
								entityType="classes"
								entityUri={decodeURIComponent(decodeURIComponent(props.match.params.uri))}
							/>}></Route>

					<Route exact path={`/ontologies/:id/properties/:uri`}
						component={(props:any) =>
							<EntityPage
								ontologyId={props.match.params.id}
								entityType="properties"
								entityUri={decodeURIComponent(decodeURIComponent(props.match.params.uri))}
							/>}></Route>

					<Route exact path={`/ontologies/:id/individuals/:uri`}
						component={(props:any) =>
							<EntityPage
								ontologyId={props.match.params.id}
								entityType="individuals"
								entityUri={decodeURIComponent(decodeURIComponent(props.match.params.uri))}
							/>}></Route>

					<Route exact path={`/help`}
						component={(props:any) => <Help />}></Route>

					<Route exact path={`/about`}
						component={(props:any) => <About />}></Route>
					</Switch>
			</BrowserRouter>

		);

	}
}

export default App


