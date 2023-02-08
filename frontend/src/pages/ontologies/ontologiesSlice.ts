import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, getPaginated, Page } from "../../app/api";
import Entity from "../../model/Entity";
import { thingFromProperties } from "../../model/fromProperties";
import Ontology from "../../model/Ontology";

export interface OntologiesState {
  ontology: Ontology | undefined;
  entity: Entity | undefined;
  ancestors: Entity[];
  nodeChildren: Map<String, TreeNode[]>;
  rootEntities: Entity[];
  ontologies: Ontology[];
  totalOntologies: number;
  entities: Entity[];
  totalEntities:number;
  loadingOntologies: boolean;
  loadingEntities: boolean;
  loadingNodeChildren: boolean;
  loadingOntology: boolean;
  loadingEntity: boolean;
  classInstances:Page<Entity>|null
  loadingClassInstances:boolean
}
export interface TreeNode {
  absoluteIdentity: string; // the IRIs of this node and its ancestors delimited by a ;
  iri: string;
  title: string;
  expandable: boolean;
  entity: Entity;
  numDescendants:number;
}
const initialState: OntologiesState = {
  ontology: undefined,
  entity: undefined,
  ancestors: [],
  nodeChildren: new Map<String, TreeNode[]>(),
  rootEntities: [],
  ontologies: [],
  totalOntologies: 0,
  entities: [],
  totalEntities: 0,
  loadingOntologies: false,
  loadingEntities: false,
  loadingNodeChildren: false,
  loadingOntology: false,
  loadingEntity: false,
  classInstances: null,
  loadingClassInstances: false
};

export const getOntology = createAsyncThunk(
  "ontologies_ontology",
  async ({ontologyId, lang}:{ontologyId:string,lang:string}) => {
    const ontologyProperties = await get<any>(
      `api/v2/ontologies/${ontologyId}`, {lang}
    );
    return new Ontology(ontologyProperties);
  }
);
export const getEntity = createAsyncThunk(
  "ontologies_entity",
  async ({ ontologyId, entityType, entityIri, lang }: any) => {
    const doubleEncodedTermUri = encodeURIComponent(
      encodeURIComponent(entityIri)
    );
    const termProperties = await get<any>(
      `api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedTermUri}`, {lang}
    );
    return thingFromProperties(termProperties);
  }
);
export const getClassInstances = createAsyncThunk(
  "ontologies_entity_class_instances",
  async ({ ontologyId, classIri, lang }: any) => {
    const doubleEncodedTermUri = encodeURIComponent(
      encodeURIComponent(classIri)
    );
    const instances = (await getPaginated<any>(
      `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedTermUri}/instances`, {lang}
    )).map(i => thingFromProperties(i));
    return instances;
  }
);
export const getOntologies = createAsyncThunk(
  "ontologies_ontologies",
  async ({ page, rowsPerPage, search }: any, { rejectWithValue }) => {
    try {
      const data = (
        await getPaginated<any>(
          `api/v2/ontologies?page=${page}&size=${rowsPerPage}${
            search ? "&search=" + search : ""
          }`
        )
      ).map((o) => new Ontology(o));
      return data;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
export const getEntities = createAsyncThunk(
  "ontologies_entities",
  async ({ ontologyId, entityType, page, rowsPerPage, search }: any, { rejectWithValue }) => {
    try {
      const data = (
        await getPaginated<any>(`api/v2/ontologies/${ontologyId}/${entityType}?page=${page}&size=${rowsPerPage}${
            search ? "&search=" + search : ""
	}`)
      ).map((e) => thingFromProperties(e));
      return data;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
export const getAncestors = createAsyncThunk(
  "ontologies_ancestors",
  async ({ ontologyId, entityType, entityIri, lang }: any) => {
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    if(entityType === 'classes') {
	var ancestorsPage = await getPaginated<any>(
	`api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/hierarchicalAncestors?${new URLSearchParams(
		{ size: "100", lang }
	)}`
	);
    } else {
	var ancestorsPage = await getPaginated<any>(
	`api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedUri}/ancestors?${new URLSearchParams(
		{ size: "100", lang }
	)}`
	);
    }
    return ancestorsPage.elements.map((obj) => thingFromProperties(obj));
  }
);
export const getNodeChildren = createAsyncThunk(
  "ontologies_node_children",
  async ({
    ontologyId,
    entityTypePlural,
    entityIri,
    absoluteIdentity,
    lang
  }: any) => {
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    if(entityTypePlural === 'classes') {
	var childrenPage = await getPaginated<any>(
	`api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/hierarchicalChildren?${new URLSearchParams(
		{
		size: "100", lang
		}
	)}`
	);
    } else if(entityTypePlural === 'individuals') {
	var childrenPage = await getPaginated<any>(
	`api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/instances?${new URLSearchParams(
		{
		size: "100", lang
		}
	)}`
	);
    } else {
	var childrenPage = await getPaginated<any>(
	`api/v2/ontologies/${ontologyId}/${entityTypePlural}/${doubleEncodedUri}/children?${new URLSearchParams(
		{
		size: "100", lang
		}
	)}`
	);
    }
    return new Map([
      [
        absoluteIdentity,
        childrenPage.elements
          .map((obj) => thingFromProperties(obj))
          .map((term) => {
            return {
              iri: term.getIri(),
              absoluteIdentity: absoluteIdentity + ";" + term.getIri(),
              title: term.getName(),
              expandable: term.hasDirectChildren(),
              entity: term,
              numDescendants: term.getNumHierarchicalDescendants() || term.getNumDescendants()
            };
          }),
      ],
    ]);
  }
);
export const getRootEntities = createAsyncThunk(
  "ontologies_roots",
  async ({ ontologyId, entityType, preferredRoots, lang }: any) => {
    if(entityType === 'individuals') {
	const rootsPage = await getPaginated<any>(
	`api/v2/ontologies/${ontologyId}/classes?${new URLSearchParams({
		hasIndividuals: "true",
		size: "100",
		lang
	})}`
	);
	return rootsPage.elements.map((obj) => thingFromProperties(obj));
    } else if(entityType === 'classes' && preferredRoots) {
	const rootsPage = await getPaginated<any>(
	`api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
		isPreferredRoot: "true",
		size: "100",
		lang
	})}`
	);
	return rootsPage.elements.map((obj) => thingFromProperties(obj));
    } else {
	const rootsPage = await getPaginated<any>(
	`api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
		hasDirectParent: "false",
		size: "100",
		lang
	})}`
	);
	return rootsPage.elements.map((obj) => thingFromProperties(obj));
    }
  }
);

const ontologiesSlice = createSlice({
  name: "ontologies",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(
      getOntology.fulfilled,
      (state: OntologiesState, action: PayloadAction<Ontology>) => {
        state.ontology = action.payload;
        state.loadingOntology = false;
      }
    );
    builder.addCase(getOntology.pending, (state: OntologiesState) => {
      state.loadingOntology = true;
    });
    builder.addCase(
      getEntity.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity>) => {
        state.entity = action.payload;
        state.loadingEntity = false;
      }
    );
    builder.addCase(getEntity.pending, (state: OntologiesState) => {
      state.loadingEntity = true;
    });
    builder.addCase(
      getClassInstances.fulfilled,
      (state: OntologiesState, action: PayloadAction<Page<Entity>>) => {
        state.classInstances = action.payload;
        state.loadingClassInstances = false;
      }
    );
    builder.addCase(getClassInstances.pending, (state: OntologiesState) => {
      state.loadingClassInstances = true;
    });
    builder.addCase(
      getAncestors.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity[]>) => {
        state.ancestors = action.payload;
      }
    );
    builder.addCase(
      getNodeChildren.fulfilled,
      (
        state: OntologiesState,
        action: PayloadAction<Map<String, TreeNode[]>>
      ) => {
        state.nodeChildren = action.payload;
        state.loadingNodeChildren = false;
      }
    );
    builder.addCase(getNodeChildren.pending, (state: OntologiesState) => {
      state.loadingNodeChildren = true;
    });
    builder.addCase(
      getRootEntities.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity[]>) => {
        state.rootEntities = action.payload;
      }
    );
    builder.addCase(
      getOntologies.fulfilled,
      (state: OntologiesState, action: PayloadAction<Page<Ontology>>) => {
        state.ontologies = action.payload.elements;
        state.totalOntologies = action.payload.totalElements;
        state.loadingOntologies = false;
      }
    );
    builder.addCase(getOntologies.pending, (state: OntologiesState) => {
      state.loadingOntologies = true;
    });
    builder.addCase(getOntologies.rejected, (state: OntologiesState) => {
      state.ontologies = initialState.ontologies;
      state.loadingOntologies = false;
    });
    builder.addCase(
      getEntities.fulfilled,
      (state: OntologiesState, action: PayloadAction<Page<Entity>>) => {
        state.entities = action.payload.elements;
        state.totalEntities = action.payload.totalElements;
        state.loadingEntities = false;
      }
    );
    builder.addCase(getEntities.pending, (state: OntologiesState) => {
      state.loadingEntities = true;
    });
    builder.addCase(getEntities.rejected, (state: OntologiesState) => {
      state.entities = initialState.entities;
      state.loadingEntities = false;
    });
  },
});

export default ontologiesSlice.reducer;
