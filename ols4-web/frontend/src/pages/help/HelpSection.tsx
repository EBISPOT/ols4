import { ExpandLess, ExpandMore } from "@mui/icons-material";
import React from "react";


interface Props {
    title:string
}

interface State {
    expanded:boolean
}

export default class HelpSection extends React.Component<Props, State> {

    constructor(props:Props) {
        super(props)

        this.state = {
            expanded: false
        }
    }

    render() {

        let { expanded } = this.state
        let { title } = this.props

        return <h1>
            {expanded ? <ExpandLess/> : <ExpandMore/>}
            {title}
        </h1>
    }
}
