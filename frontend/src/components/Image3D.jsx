
import { useEffect, useState } from 'react';
import { useRef } from 'react';

export default function Image3D({src}) {

	let divRef = useRef(null);

	useEffect(() => {
		
		async function load() {

			if(divRef.current) {
				let div = divRef.current

			}

		} 

		load()

	}, [ ])

	return <div className="rounded-lg mx-auto object-contain" ref={divRef}>
		<model-viewer style={{width:'300px',height:'300px'}} src={src} shadow-intensity="1" camera-controls touch-action="pan-y"></model-viewer>
	</div>
}



