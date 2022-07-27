import { scryRenderedComponentsWithType } from "react-dom/test-utils"

export async function request(path:string, init?:RequestInit|undefined):Promise<any> {

    try {
        let res = await fetch(`${process.env.REACT_APP_APIURL}${path}`, {
            ...(init ? init : {}),
            //headers: { ...(init?.headers || {}), ...getAuthHeaders() }
        })

        return await res.json()
    } catch(e) {
        console.dir(e)
        // window.location.href = '/login'
    }

}

export class Page<T> {

    constructor(public size:number, public totalElements:number, public totalPages:number, public number:number, public elements:T[]) {
    }

    map<NewType>(fn:(T)=>NewType) {
        return new Page<NewType>(this.size, this.totalElements, this.totalPages, this.number, this.elements.map(fn))
    }
}

export async function getPaginated<ResType>(path:string):Promise<Page<ResType>> {
    let res = await get<any>(path)

    let elements = res['_embedded'] ?
    	res['_embedded'][Object.keys(res['_embedded'])[0]] : []

    return new Page<ResType>(
        res.page.size,
        res.page.totalElements,
        res.page.totalPages,
        res.page.number,
        elements
    )

}

export async function get<ResType>(path:string):Promise<ResType> {
    return request(path)
}

export async function post<ReqType, ResType = any>(path:string, body:ReqType):Promise<ResType> {
    return request(path, {
        method: 'POST',
        body: JSON.stringify(body),
        headers: {
            'content-type': 'application/json'
        }
    })
}

export async function put<ReqType, ResType = any>(path:string, body:ReqType):Promise<ResType> {
    return request(path, {
        method: 'PUT',
        body: JSON.stringify(body),
        headers: {
            'content-type': 'application/json'
        }
    })
}