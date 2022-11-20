
export default class Reified<T> {

	value:T
	metadata:any|null

	private constructor(value:T, metadata:any) {
		this.value = value
		this.metadata = metadata
	}

	public static fromJson<T>(jsonNode:any):Reified<T>[] {

		if(!jsonNode) {
			return []
		}

		if(!Array.isArray(jsonNode)) {
			jsonNode = [ jsonNode ]
		}

		return jsonNode.map((value:any) => {

			// is this a reification?
			if(typeof(value) === 'object' && value.value !== undefined) {

				// yes, separate out the metadata from the value

				let theValue = value.value

				let metadata:any = {}

				for(let k of Object.keys(value)) {
					if(k === 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type')
						continue
					metadata[k] = value[k]
				}

				return new Reified<T>(theValue, metadata)

			} else  {

				// no, just return the value

				return new Reified<T>(value, null)
			}
		})
	}
}