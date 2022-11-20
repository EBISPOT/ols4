import { Fragment } from "react"
import { asArray } from "../app/util"

export default function ClassExpression(props:{ expr:any }) {

	let { expr } = props

	if(typeof(expr) !== 'object') {

		// This is just an IRI
		//
		return <div className="classExpressionIri">{expr}</div>
	}

	///
	/// 1. owl:Class expressions
	///
	let intersectionOf = asArray(expr['http://www.w3.org/2002/07/owl#intersectionOf'])
	if(intersectionOf.length > 0) {

		let nodes:JSX.Element[] = [
			<div className="classExpressionIntersectionStart">(</div>
		]

		for(let subExpr of intersectionOf) {
			if(nodes.length > 0) {
				nodes.push(  <div className="classExpressionIntersection">and</div> )
			}
			nodes.push(<ClassExpression expr={subExpr} />)
		}

		nodes.push( <div className="classExpressionIntersectionEnd">)</div> )

		return <Fragment>{nodes}</Fragment>
	}

	let unionOf = asArray(expr['http://www.w3.org/2002/07/owl#unionOf'])
	if(unionOf.length > 0) {
		
		let nodes:JSX.Element[] = [
			<div className="classExpressionUnionStart">(</div>
		]

		for(let subExpr of intersectionOf) {
			if(nodes.length > 0) {
				nodes.push(  <div className="classExpressionUnion">or</div> )
			}
			nodes.push(<ClassExpression expr={subExpr} />)
		}

		nodes.push( <div className="classExpressionUnionEnd">)</div> )

		return <Fragment>{nodes}</Fragment>
	}

	let complementOf = asArray(expr['http://www.w3.org/2002/07/owl#complementOf'])[0]
	if(complementOf) {

		return <Fragment>
			<div className="classExpressionNot">not</div>
			<ClassExpression expr={complementOf} />
		</Fragment>
	}

	let oneOf = asArray(expr['http://www.w3.org/2002/07/owl#oneOf'])
	if(oneOf.length > 0) {
		
		let nodes:JSX.Element[] = [
			<div className="classExpressionOneOfStart">&lbrace;</div>
		]

		for(let subExpr of intersectionOf) {
			if(nodes.length > 0) {
				nodes.push(  <div className="classExpressionOneOfDelimiter">, </div> )
			}
			nodes.push(<ClassExpression expr={subExpr} />)
		}

		nodes.push(
			<div className="classExpressionOneOfEnd">&rbrace;</div>
		)

		return <Fragment>{nodes}</Fragment>
	}

	///
	/// 2. owl:Restriction expressions
	///
	let onProperty = expr['http://www.w3.org/2002/07/owl#onProperty']
	// let onProperties = expr['http://www.w3.org/2002/07/owl#onProperties'])

	if(!onProperty) {
		return <div>unknown class expression</div>
	}

	let someValuesFrom = asArray(expr['http://www.w3.org/2002/07/owl#someValuesFrom'])[0]

	if(someValuesFrom) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionSome">some</div>
			<ClassExpression expr={someValuesFrom} />
		</Fragment>
	}

	let allValuesFrom = asArray(expr['http://www.w3.org/2002/07/owl#allValuesFrom'])[0]
	if(allValuesFrom) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionAll">only</div>
			<ClassExpression expr={allValuesFrom} />
		</Fragment>
	}

	let hasValue = asArray(expr['http://www.w3.org/2002/07/owl#hasValue'])[0]
	if(hasValue) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionHasValue">value</div>
			<ClassExpression expr={hasValue} />
		</Fragment>
	}

	let minCardinality = asArray(expr['http://www.w3.org/2002/07/owl#minCardinality'])[0]
	if(minCardinality) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionMin">min</div>
			<ClassExpression expr={minCardinality} />
		</Fragment>
	}

	let maxCardinality = asArray(expr['http://www.w3.org/2002/07/owl#maxCardinality'])[0]
	if(maxCardinality) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionMax">max</div>
			<ClassExpression expr={maxCardinality} />
		</Fragment>
	}

	let minQualifiedCardinality = asArray(expr['http://www.w3.org/2002/07/owl#minQualifiedCardinality'])[0]
	if(minQualifiedCardinality) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionMin">min</div>
			<ClassExpression expr={minQualifiedCardinality} />
		</Fragment>
	}

	let maxQualifiedCardinality = asArray(expr['http://www.w3.org/2002/07/owl#maxQualifiedCardinality'])[0]
	if(maxQualifiedCardinality) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionMax">max</div>
			<ClassExpression expr={maxQualifiedCardinality} />
		</Fragment>
	}

	let exactCardinality = asArray(expr['http://www.w3.org/2002/07/owl#exactCardinality'])[0]
	if(exactCardinality) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionExact">exactly</div>
			<ClassExpression expr={exactCardinality} />
		</Fragment>
	}

	let exactQualifiedCardinality = asArray(expr['http://www.w3.org/2002/07/owl#exactQualifiedCardinality'])[0]
	if(exactQualifiedCardinality) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionExact">exactly</div>
			<ClassExpression expr={exactQualifiedCardinality} />
		</Fragment>
	}

	let hasSelf = asArray(expr['http://www.w3.org/2002/07/owl#hasSelf'])[0]
	if(hasSelf) {
		return <Fragment>
			<ClassExpression expr={onProperty} />
			<div className="classExpressionSelf">Self</div>
		</Fragment>
	}

	return <div>unknown class expression</div>
}

