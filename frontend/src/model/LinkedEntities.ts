import Reified from "./Reified"

export interface LinkedEntity {
	definedBy?:string[]
	iri?:string
	url?:string
	numAppearsIn:number
	hasLocalDefinition:boolean
	label:string|string[]
	type:string[]
}

export default class LinkedEntities {

	linkedEntities:{ [key:string]:LinkedEntity }

	constructor(linkedEntities:any) {
		if(linkedEntities)
			this.linkedEntities = {...linkedEntities}
		else
			this.linkedEntities = {}
	}

	mergeWith(linkedEntities:any):LinkedEntities {
		if(linkedEntities)
			return new LinkedEntities({ ...this.linkedEntities, linkedEntities })
		else
			return new LinkedEntities({ ...this.linkedEntities })
	}

	getLabelForIri(iri:string):string|undefined {

		let linkedEntity = this.linkedEntities[iri]

		if(linkedEntity) {
			let label = Reified.fromJson<string>( linkedEntity.label )
			if(label && label.length > 0) {
				return label[0].value
			}
		}
	}

	get(iri:string):LinkedEntity|undefined {
		return this.linkedEntities[iri]
	}
}
