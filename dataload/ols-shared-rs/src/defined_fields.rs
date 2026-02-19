/// Defined fields matching the Java DefinedFields enum
/// These are the standard field names used across OLS4 JSON processing
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[allow(dead_code)]
pub enum DefinedFields {
    AppearsIn,
    BaseUri,
    DefinedBy,
    Definition,
    DirectAncestor,
    DirectParent,
    ExportsTo,
    HasDirectChildren,
    HasDirectParents,
    HasHierarchicalChildren,
    HasHierarchicalParents,
    HasIndividuals,
    HasLocalDefinition,
    HierarchicalAncestor,
    HierarchicalParent,
    Imported,
    ImportsFrom,
    IsDefiningOntology,
    IsObsolete,
    IsPreferredRoot,
    Label,
    Language,
    MailingList,
    NumDescendants,
    NumHierarchicalDescendants,
    OntologyPurl,
    PreferredRoot,
    RelatedFrom,
    RelatedTo,
    LinksTo,
    Synonym,
}

impl DefinedFields {
    /// Get the JSON field name (primary name)
    pub fn text(&self) -> &'static str {
        match self {
            DefinedFields::AppearsIn => "appearsIn",
            DefinedFields::BaseUri => "baseUri",
            DefinedFields::DefinedBy => "definedBy",
            DefinedFields::Definition => "definition",
            DefinedFields::DirectAncestor => "directAncestor",
            DefinedFields::DirectParent => "directParent",
            DefinedFields::ExportsTo => "exportsTo",
            DefinedFields::HasDirectChildren => "hasDirectChildren",
            DefinedFields::HasDirectParents => "hasDirectParents",
            DefinedFields::HasHierarchicalChildren => "hasHierarchicalChildren",
            DefinedFields::HasHierarchicalParents => "hasHierarchicalParents",
            DefinedFields::HasIndividuals => "hasIndividuals",
            DefinedFields::HasLocalDefinition => "hasLocalDefinition",
            DefinedFields::HierarchicalAncestor => "hierarchicalAncestor",
            DefinedFields::HierarchicalParent => "hierarchicalParent",
            DefinedFields::Imported => "imported",
            DefinedFields::ImportsFrom => "importsFrom",
            DefinedFields::IsDefiningOntology => "isDefiningOntology",
            DefinedFields::IsObsolete => "isObsolete",
            DefinedFields::IsPreferredRoot => "isPreferredRoot",
            DefinedFields::Label => "label",
            DefinedFields::Language => "language",
            DefinedFields::MailingList => "mailingList",
            DefinedFields::NumDescendants => "numDescendants",
            DefinedFields::NumHierarchicalDescendants => "numHierarchicalDescendants",
            DefinedFields::OntologyPurl => "ontologyPurl",
            DefinedFields::PreferredRoot => "preferredRoot",
            DefinedFields::RelatedFrom => "relatedFrom",
            DefinedFields::RelatedTo => "relatedTo",
            DefinedFields::LinksTo => "linksTo",
            DefinedFields::Synonym => "synonym",
        }
    }

    /// Get the alternative name (if any) - used for legacy compatibility
    pub fn alt(&self) -> &'static str {
        match self {
            DefinedFields::BaseUri => "baseUris",
            DefinedFields::Definition => "description",
            DefinedFields::HasDirectChildren => "has_children",
            _ => "",
        }
    }
}

/// Constants for commonly used field names
pub mod fields {
    pub const BASE_URI: &str = "baseUri";
    pub const DEFINED_BY: &str = "definedBy";
    pub const DEFINITION: &str = "definition";
    pub const DIRECT_ANCESTOR: &str = "directAncestor";
    pub const DIRECT_PARENT: &str = "directParent";
    pub const HIERARCHICAL_ANCESTOR: &str = "hierarchicalAncestor";
    pub const HIERARCHICAL_PARENT: &str = "hierarchicalParent";
    pub const IMPORTED: &str = "imported";
    pub const IS_DEFINING_ONTOLOGY: &str = "isDefiningOntology";
    pub const IS_OBSOLETE: &str = "isObsolete";
    pub const IS_PREFERRED_ROOT: &str = "isPreferredRoot";
    pub const LABEL: &str = "label";
    pub const LANGUAGE: &str = "language";
    pub const NUM_DESCENDANTS: &str = "numDescendants";
    pub const NUM_HIERARCHICAL_DESCENDANTS: &str = "numHierarchicalDescendants";
    pub const ONTOLOGY_PURL: &str = "ontologyPurl";
    pub const PREFERRED_PREFIX: &str = "preferredPrefix";
    pub const PREFERRED_ROOT: &str = "preferredRoot";
    pub const SYNONYM: &str = "synonym";
}
