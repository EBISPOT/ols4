import Reified from "./Reified"

export default class ReferencedEntities {

	referencedEntities:any

	constructor(referencedEntities:any) {
		this.referencedEntities = {...referencedEntities}
	}

	mergeWith(referencedEntities:any):ReferencedEntities {
		return new ReferencedEntities({ ...this.referencedEntities, referencedEntities })
	}

	getLabelForIri(iri:string):string|undefined {

		let referencedEntity = this.referencedEntities[iri]

		if(referencedEntity) {
			let label = Reified.fromJson<string>( referencedEntity.label )
			return label[0].value
		}
	}

	get(iri:string) {
		return this.referencedEntities[iri]
	}
}
