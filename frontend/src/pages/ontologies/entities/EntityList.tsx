import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../../app/hooks";
import DataTable, { Column } from "../../../components/DataTable";
import Entity from "../../../model/Entity";
import { getEntities, getDirectChildrenEntities } from "../ontologiesSlice";
import Individual from "../../../model/Individual";
import Property from "../../../model/Property";

export default function EntityList({
  ontologyId,
  entityType,
  parentEntityIri,
  lang,
  showObsoleteEnabled,
  onNavigateToEntity,
  title,
}: {
  ontologyId: string;
  entityType: "entities" | "classes" | "properties" | "individuals";
  parentEntityIri?: string;
  lang?: string;
  showObsoleteEnabled?: boolean;
  onNavigateToEntity?: (ontologyId: string, entity: Entity) => void;
  title?: string;
}) {
  const dispatch = useAppDispatch();
  

  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(10);
  const [search, setSearch] = useState<string>("");

  const entities = useAppSelector((state) => 
    parentEntityIri ? state.ontologies.directChildrenEntities : state.ontologies.entities
  );
  const totalEntities = useAppSelector((state) => 
    parentEntityIri ? state.ontologies.totalDirectChildrenEntities : state.ontologies.totalEntities
  );
  const loading = useAppSelector((state) => 
    parentEntityIri ? state.ontologies.loadingDirectChildrenEntities : state.ontologies.loadingEntities
  );

  useEffect(() => {
    if (parentEntityIri) {
      // Fetch direct children using the specific API endpoint
      dispatch(
        getDirectChildrenEntities({
          ontologyId,
          entityIri: parentEntityIri,
          entityType,
          page,
          size: rowsPerPage,
          search,
          lang,
          showObsoleteEnabled,
        })
      );
    } else {
      // Use regular entities endpoint for general browsing
      const searchParam = search ? `*${search}*` : search;
      const params: any = { 
        ontologyId, 
        entityType, 
        page, 
        rowsPerPage, 
        search: searchParam 
      };
      
      if (lang) params.lang = lang;
      if (showObsoleteEnabled !== undefined) params.includeObsoleteEntities = showObsoleteEnabled;
      
      dispatch(getEntities(params));
    }
  }, [dispatch, ontologyId, entityType, page, rowsPerPage, search, parentEntityIri, lang, showObsoleteEnabled]);

  useEffect(() => {
    setPage(0);
  }, [entityType, parentEntityIri]);

  // Reset page when search changes
  useEffect(() => {
    setPage(0);
  }, [search]);

  const navigate = useNavigate();

  // base columns are shown for all entity types.
  const baseColumns: readonly Column[] = [
    {
      name: "Name",
      sortable: true,
      selector: (entity: Entity) => entity.getName(),
    },
    {
      name: "ID",
      sortable: true,
      selector: (entity: Entity) => entity.getShortForm(),
    },
  ];

  // If the entity type is "individuals", add a "associated class" column.
  const individualTypeColumn: Column = {
    name: "Associated Class",
    sortable: true,
    selector: (entity: Entity) => {
      if(entity instanceof Individual) {
        const types = entity.getIndividualTypes();
        const linkedEntities = entity.getLinkedEntities();
        if (types && types.length > 0) {
          return types
              .map((val: any) => {
                if (!(typeof val === "object" && !Array.isArray(val))) {
                  return (
                      linkedEntities.getLabelForIri(val) ||
                      val.split("/").pop() ||
                      val
                  );
                }
                if (typeof val === "object" && !Array.isArray(val)) {
                  // If the object has the "someValuesFrom" property, use the custom format.
                  if (val["http://www.w3.org/2002/07/owl#someValuesFrom"]) {
                    const someValuesFromIri = val["http://www.w3.org/2002/07/owl#someValuesFrom"];
                    const onPropertyIri = val["http://www.w3.org/2002/07/owl#onProperty"];
                    const someValuesLabel =
                        linkedEntities.getLabelForIri(someValuesFromIri) ||
                        (typeof someValuesFromIri === "string" && someValuesFromIri.split("/").pop()) ||
                        someValuesFromIri;
                    const propertyLabel =
                        linkedEntities.getLabelForIri(onPropertyIri) ||
                        (typeof onPropertyIri === "string" && onPropertyIri.split("/").pop()) ||
                        onPropertyIri;
                    return `${propertyLabel} some ${someValuesLabel}`;
                  }
                }
              })
              .join(", ");
        }
      }
      return "";
    },
  };

  // Define domain column for properties
  const domainColumn: Column = {
    name: "Domain",
    sortable: true,
    selector: (entity: Entity) => {
      if(entity instanceof Property) {
        const domains = entity.getDomain();
        if (domains && domains.length > 0) {
          const linkedEntities = entity.getLinkedEntities();
          return domains
              .map((domain: any) => {
                // Handle string IRIs
                if (typeof domain === "string") {
                  return formatIri(domain, linkedEntities);
                }

                // Handle array (could be a list of domains)
                if (Array.isArray(domain)) {
                  return domain.map((item: any) => {
                    if (typeof item === "string") {
                      return formatIri(item, linkedEntities);
                    }
                    if (typeof item === "object") {
                      // Handle unionOf inside an object in the array
                      if (item["http://www.w3.org/2002/07/owl#unionOf"] &&
                          Array.isArray(item["http://www.w3.org/2002/07/owl#unionOf"])) {

                        const unionOf = item["http://www.w3.org/2002/07/owl#unionOf"];
                        return unionOf.map((unionItem: any) => {
                          if (typeof unionItem === "string") {
                            return formatIri(unionItem, linkedEntities);
                          }
                          if (typeof unionItem === "object" &&
                              unionItem["http://www.w3.org/2002/07/owl#someValuesFrom"] &&
                              unionItem["http://www.w3.org/2002/07/owl#onProperty"]) {
                            return formatRestriction(unionItem, linkedEntities);
                          }
                          return "";
                        }).filter(Boolean).join(" or ");
                      }
                    }
                    return "";
                  }).filter(Boolean).join(", ");
                }

                // Handle complex domain objects
                if (typeof domain === "object" && !Array.isArray(domain)) {
                  // Check for someValuesFrom restriction
                  if (domain["http://www.w3.org/2002/07/owl#someValuesFrom"] &&
                      domain["http://www.w3.org/2002/07/owl#onProperty"]) {
                    return formatRestriction(domain, linkedEntities);
                  }

                  // Check for unionOf
                  const unionOf = domain["http://www.w3.org/2002/07/owl#unionOf"];
                  if (unionOf && Array.isArray(unionOf)) {
                    return unionOf.map((item: any) => {
                      if (typeof item === "string") {
                        return formatIri(item, linkedEntities);
                      }
                      if (typeof item === "object" &&
                          item["http://www.w3.org/2002/07/owl#someValuesFrom"] &&
                          item["http://www.w3.org/2002/07/owl#onProperty"]) {
                        return formatRestriction(item, linkedEntities);
                      }
                      return "";
                    }).filter(Boolean).join(" or ");
                  }

                  // Check for owl:intersectionOf
                  const intersectionOf = domain["http://www.w3.org/2002/07/owl#intersectionOf"];
                  if (intersectionOf && Array.isArray(intersectionOf)) {
                    return intersectionOf.map((item: any) => {
                      // Handle string IRIs in the intersection
                      if (typeof item === "string") {
                        return formatIri(item, linkedEntities);
                      }

                      // Handle objects in the intersection, particularly looking for owl:oneOf
                      if (typeof item === "object" && !Array.isArray(item)) {
                        // Handle restriction in intersection
                        if (item["http://www.w3.org/2002/07/owl#someValuesFrom"] &&
                            item["http://www.w3.org/2002/07/owl#onProperty"]) {
                          return formatRestriction(item, linkedEntities);
                        }

                        // Handle oneOf in intersection
                        const oneOf = item["http://www.w3.org/2002/07/owl#oneOf"];
                        if (oneOf && Array.isArray(oneOf)) {
                          // Format the oneOf elements as a comma-separated list inside curly braces
                          return `{${oneOf.join(", ")}}`;
                        }
                      }

                      return "";
                    }).filter(Boolean).join(" and ");
                  }
                }

                return "";
              })
              .filter(Boolean)
              .join(", ");
        }
      }
      return "";
    },
  };

  // Define range column for properties
  const rangeColumn: Column = {
    name: "Range",
    sortable: true,
    selector: (entity: Entity) => {
      if(entity instanceof Property) {
        const ranges = entity.getRange();
        if (ranges && ranges.length > 0) {
          const linkedEntities = entity.getLinkedEntities();
          return ranges
              .map((range: any) => {
                // Handle string IRIs
                if (typeof range === "string") {
                  return formatIri(range, linkedEntities);
                }

                // Handle array (could be a list of ranges)
                if (Array.isArray(range)) {
                  return range.map((item: any) => {
                    if (typeof item === "string") {
                      return formatIri(item, linkedEntities);
                    }
                    if (typeof item === "object") {
                      // Handle unionOf inside an object in the array
                      if (item["http://www.w3.org/2002/07/owl#unionOf"] &&
                          Array.isArray(item["http://www.w3.org/2002/07/owl#unionOf"])) {

                        const unionOf = item["http://www.w3.org/2002/07/owl#unionOf"];
                        return unionOf.map((unionItem: any) => {
                          if (typeof unionItem === "string") {
                            return formatIri(unionItem, linkedEntities);
                          }
                          if (typeof unionItem === "object" &&
                              unionItem["http://www.w3.org/2002/07/owl#someValuesFrom"] &&
                              unionItem["http://www.w3.org/2002/07/owl#onProperty"]) {
                            return formatRestriction(unionItem, linkedEntities);
                          }
                          return "";
                        }).filter(Boolean).join(" or ");
                      }
                    }
                    return "";
                  }).filter(Boolean).join(", ");
                }

                // Handle complex range objects
                if (typeof range === "object" && !Array.isArray(range)) {
                  // Check for someValuesFrom restriction
                  if (range["http://www.w3.org/2002/07/owl#someValuesFrom"] &&
                      range["http://www.w3.org/2002/07/owl#onProperty"]) {
                    return formatRestriction(range, linkedEntities);
                  }

                  // Check for unionOf
                  const unionOf = range["http://www.w3.org/2002/07/owl#unionOf"];
                  if (unionOf && Array.isArray(unionOf)) {
                    return unionOf.map((item: any) => {
                      if (typeof item === "string") {
                        return formatIri(item, linkedEntities);
                      }
                      if (typeof item === "object" &&
                          item["http://www.w3.org/2002/07/owl#someValuesFrom"] &&
                          item["http://www.w3.org/2002/07/owl#onProperty"]) {
                        return formatRestriction(item, linkedEntities);
                      }
                      return "";
                    }).filter(Boolean).join(" or ");
                  }

                  // Check for owl:intersectionOf
                  const intersectionOf = range["http://www.w3.org/2002/07/owl#intersectionOf"];
                  if (intersectionOf && Array.isArray(intersectionOf)) {
                    return intersectionOf.map((item: any) => {
                      // Handle string IRIs in the intersection
                      if (typeof item === "string") {
                        return formatIri(item, linkedEntities);
                      }

                      // Handle objects in the intersection, particularly looking for owl:oneOf
                      if (typeof item === "object" && !Array.isArray(item)) {
                        // Handle restriction in intersection
                        if (item["http://www.w3.org/2002/07/owl#someValuesFrom"] &&
                            item["http://www.w3.org/2002/07/owl#onProperty"]) {
                          return formatRestriction(item, linkedEntities);
                        }

                        // Handle oneOf in intersection
                        const oneOf = item["http://www.w3.org/2002/07/owl#oneOf"];
                        if (oneOf && Array.isArray(oneOf)) {
                          // Format the oneOf elements as a comma-separated list inside curly braces
                          return `{${oneOf.join(", ")}}`;
                        }
                      }

                      return "";
                    }).filter(Boolean).join(" and ");
                  }
                }
                return "";
              })
              .filter(Boolean)
              .join(", ");
        }
      }
      return "";
    },
  };

  // Helper function to format someValuesFrom restrictions
  const formatRestriction = (restriction: any, linkedEntities: any) => {
    const someValuesFromIri = restriction["http://www.w3.org/2002/07/owl#someValuesFrom"];
    const onPropertyIri = restriction["http://www.w3.org/2002/07/owl#onProperty"];

    if (!someValuesFromIri || !onPropertyIri) return "";

    const someValuesLabel =
        linkedEntities.getLabelForIri(someValuesFromIri) ||
        (typeof someValuesFromIri === "string" && someValuesFromIri.split("/").pop()) ||
        someValuesFromIri;

    const propertyLabel =
        linkedEntities.getLabelForIri(onPropertyIri) ||
        (typeof onPropertyIri === "string" && onPropertyIri.split("/").pop()) ||
        onPropertyIri;

    return `${propertyLabel} some ${someValuesLabel}`;
  };

  // Helper function to format IRIs
  const formatIri = (iri: string, linkedEntities: any) => {
    return linkedEntities.getLabelForIri(iri) || iri.split("/").pop() || iri;
  };

  // Merge columns based on the entity type.
  let columns = [...baseColumns];

  if (entityType === "individuals") {
    columns.push(individualTypeColumn);
  } else if (entityType === "properties") {
    columns.push(domainColumn, rangeColumn);
  }

  return (
    <div className={parentEntityIri ? "mt-4 p-4 bg-gray-50 rounded-lg" : "mt-2"}>
      {title && (
        <div className="mb-3">
          <h4 className="text-lg font-semibold text-gray-700">
            {title}
          </h4>
          <p className="text-sm text-gray-600">
            This node has more than 1000 direct children. Use the list below to browse them.
          </p>
        </div>
      )}
      
      <DataTable
        columns={columns}
        data={entities}
        dataCount={totalEntities}
        placeholder={`Search ${entityType}...`}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={(pg: number) => {
          setPage(pg);
        }}
        onRowsPerPageChange={(rows: number) => {
          setRowsPerPage((prev) => {
            if (rows !== prev) setPage(0);
            return rows;
          });
        }}
        onSelectRow={(row) => {
          if (onNavigateToEntity) {
            onNavigateToEntity(ontologyId, row);
          } else {
            const termUrl = encodeURIComponent(
              encodeURIComponent(row.properties.iri)
            );
            navigate(
              `/ontologies/${ontologyId}/${row.getTypePlural()}/${termUrl}`
            );
          }
        }}
        onFilter={(searchTerm: string) => {
          setSearch(searchTerm);
        }}
      />
    </div>
  );
}
