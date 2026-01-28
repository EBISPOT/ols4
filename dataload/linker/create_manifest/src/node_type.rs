/// Node type for tracking what type(s) a URI represents
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub enum NodeType {
    Ontology,
    Class,
    Property,
    Individual,
}

impl NodeType {
    pub fn as_str(&self) -> &'static str {
        match self {
            NodeType::Ontology => "ONTOLOGY",
            NodeType::Class => "CLASS",
            NodeType::Property => "PROPERTY",
            NodeType::Individual => "INDIVIDUAL",
        }
    }
}

impl std::fmt::Display for NodeType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.as_str())
    }
}
