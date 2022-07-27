
import { ReactNode } from 'react'
import ReactLoadingOverlay from 'react-loading-overlay'

export default function LoadingOverlay(props: { active: boolean, children: ReactNode }) {
    return <ReactLoadingOverlay active={props.active} spinner text='Saving...' fadeSpeed={100} styles={{
        overlay: (base: any) => ({
            ...base,
            background: 'rgba(0, 0, 0, 0.2)'
        })
    }}>
        {props.children}
    </ReactLoadingOverlay>
}
