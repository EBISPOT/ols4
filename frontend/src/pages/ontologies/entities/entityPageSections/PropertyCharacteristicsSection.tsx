import { Fragment } from "react"
import { randomString, sortByKeys } from "../../../../app/util"
import Entity from "../../../../model/Entity"

export default function PropertyCharacteristicsSection({entity}:{entity:Entity}) {

if(entity.getType() !== 'property')
  return <Fragment/>  

  let characteristics = entity.getRdfTypes().map(type => {

    return ({
      'http://www.w3.org/2002/07/owl#FunctionalProperty': 'Functional',
      'http://www.w3.org/2002/07/owl#InverseFunctionalProperty': 'Inverse Functional',
      'http://www.w3.org/2002/07/owl#TransitiveProperty': 'Transitive',
      'http://www.w3.org/2002/07/owl#SymmetricProperty': 'Symmetric',
      'http://www.w3.org/2002/07/owl#AsymmetricProperty': 'Asymmetric',
      'http://www.w3.org/2002/07/owl#ReflexiveProperty': 'Reflexive',
      'http://www.w3.org/2002/07/owl#IrreflexiveProperty': 'Irreflexive',
    })[type]

  }).filter((type) => !!type)

  if(characteristics.length === 0)
    return <Fragment/>

  return <div>
              <div className="font-bold">Characteristics</div>
              {characteristics.length === 1 ? (
                <p>{characteristics[0]}</p>
              ) : (
                <ul className="list-disc list-inside">
                  {characteristics
                    .map((characteristic) => {
                      return (
                        <li key={randomString()}>
                          {characteristic}
                        </li>
                      );
                    })
                    .sort((a, b) => sortByKeys(a, b))}
                </ul>
              )}
          </div>

}