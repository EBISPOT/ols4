
type ReqParams = {[k:string]:string}|undefined 

export async function request(
  path: string,
  reqParams:ReqParams,
  init?: RequestInit | undefined,
  apiUrl?: string
): Promise<any> {
  const url = (apiUrl || process.env.REACT_APP_APIURL) + path;
  //const res = await fetch(url.replace(/([^:]\/)\/+/g, "$1"), {
  const res = await fetch(url + (reqParams ? ('?' + new URLSearchParams(Object.entries(reqParams)).toString()) : ''), {
    ...(init ? init : {}),
    //headers: { ...(init?.headers || {}), ...getAuthHeaders() }
  });
  if (!res.ok) {
    const message = `Failure loading ${res.url} with status ${res.status} (${res.statusText})`;
    console.dir(message);
    return Promise.reject(new Error(message))
  }
  return await res.json();
}

export class Page<T> {
  constructor(
    public page: number,
    public numElements: number,
    public totalPages: number,
    public totalElements: number,
    public elements: T[],
    public facetFieldsToCounts: Map<string, Map<string, number>>
  ) {}

  map<NewType>(fn: (T) => NewType) {
    return new Page<NewType>(
      this.page,
      this.numElements,
      this.totalPages,
      this.totalElements,
      this.elements.map(fn),
      this.facetFieldsToCounts
    );
  }
}

export async function getPaginated<ResType>(
  path: string,
  reqParams?: ReqParams,
  apiUrl?: string
): Promise<Page<ResType>> {
  const res = await get<any>(path, reqParams, apiUrl);

  return new Page<ResType>(
	res.page || 0,
	res.numElements || 0,
	res.totalPages || 0,
	res.totalElements || 0,
	res.elements || [],
	res.facetFieldsToCounts || new Map()
  );
}

export async function get<ResType>(path: string, reqParams?:ReqParams, apiUrl?: string): Promise<ResType> {
  return request(path, reqParams, undefined, apiUrl);
}

export async function post<ReqType, ResType = any>(
  path: string,
  reqParams: ReqParams,
  body: ReqType
): Promise<ResType> {
  return request(path, reqParams, {
    method: "POST",
    body: JSON.stringify(body),
    headers: {
      "content-type": "application/json",
    },
  });
}

export async function put<ReqType, ResType = any>(
  path: string,
  reqParams: ReqParams,
  body: ReqType
): Promise<ResType> {
  return request(path, reqParams, {
    method: "PUT",
    body: JSON.stringify(body),
    headers: {
      "content-type": "application/json",
    },
  });
}
