//! Error type for rdf2json.

use thiserror::Error;

#[derive(Error, Debug)]
pub enum Rdf2JsonError {
    #[error("Config error: {0}")]
    Config(String),

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Parse error: {0}")]
    Parse(String),

    #[error("{0}")]
    Other(String),
}
