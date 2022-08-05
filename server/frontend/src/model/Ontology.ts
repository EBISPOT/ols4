import Thing from "./Thing"
export default class Ontology extends Thing {

    constructor(properties:any) {
	super(properties)
    }

    getOntologyId(): string {
        return this.properties['ontologyId']
    }

    getConfig():any {
	return this.properties['ontologyConfig']
    }

    getName():string {
        return this.properties["http://purl.org/dc/elements/1.1/title"] 
			|| this.getConfig()['title']
			|| ''
    }

    getDescription():string {
        return this.properties["http://purl.org/dc/elements/1.1/description"]
			|| this.getConfig()['description']
			|| ''
    }

    getNumEntities():number {
	return parseInt(this.properties['numberOfEntities'])
    }

    getNumClasses():number {
	return parseInt(this.properties['numberOfClasses'])
    }

    getNumProperties():number {
	return parseInt(this.properties['numberOfProperties'])
    }

    getNumIndividuals():number {
	return parseInt(this.properties['numberOfIndividuals'])
    }

    getLogoURL():string {

	let config = this.getConfig()

	return config['depicted_by'] || undefined
    }


}