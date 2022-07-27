import { Tooltip } from "@mui/material"
import React, { Fragment } from "react"
import { Link } from "react-router-dom"

export default function Header(props: { section:string, projectId?:string }) {

    let { section, projectId } = props

    return <header style={{ padding: '16px', backgroundColor: 'black', backgroundImage: 'url(\'' + process.env.PUBLIC_URL + '/embl-ebi-background-4.jpg\')', backgroundPosition: '100% 100%' }}>
            <a href={process.env.PUBLIC_URL}>
                <img style={{ height: '100px' }} src={process.env.PUBLIC_URL + "/logo.png"} />
            </a>
            <nav>
                <ul className="dropdown menu float-left" data-description="navigational" role="menubar" data-dropdown-menu="6mg2ht-dropdown-menu">
                    <li role="menuitem" className={section === 'home' ? 'active' : ''}><Link to="/">Home</Link></li>
                    <li role="menuitem" className={section === 'ontologies' ? 'active' : ''}><Link to="/ontologies">Ontologies</Link></li>
                    <li role="menuitem" className={section === 'help' ? 'active' : ''}><Link to={`/help`}>Help</Link></li>
                    <li role="menuitem" className={section === 'about' ? 'active' : ''}><Link to={`/about`}>About</Link></li>
                </ul>
            </nav>
        </header>
}
