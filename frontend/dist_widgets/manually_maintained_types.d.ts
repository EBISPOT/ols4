
declare global {
	interface OLSWidgets {
		createEntityTree:(props:{
			iri?:string,
			ontologyId:string,
			apiUrl:string
		}, target:Element)=>void
	}
}

