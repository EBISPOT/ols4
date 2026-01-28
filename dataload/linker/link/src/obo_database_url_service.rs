use serde::Deserialize;
use std::collections::HashMap;

use crate::curie_map_result::CurieMapResult;

const DEFAULT_XREF_URL: &str = "https://raw.githubusercontent.com/geneontology/go-site/master/metadata/db-xrefs.yaml";

#[derive(Debug, Deserialize)]
struct XrefEntry {
    database: String,
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    entity_types: Option<Vec<EntityType>>,
}

#[derive(Debug, Deserialize)]
struct EntityType {
    #[serde(default)]
    url_syntax: Option<String>,
}

struct OboDatabase {
    #[allow(dead_code)]
    database_id: String,
    #[allow(dead_code)]
    database_name: Option<String>,
    url_syntax: String,
}

impl OboDatabase {
    fn get_url_for_id(&self, id: &str) -> String {
        self.url_syntax.replace("[example_id]", id)
    }
}

pub struct OboDatabaseUrlService {
    xref_urls: String,
    databases: HashMap<String, OboDatabase>,
}

impl OboDatabaseUrlService {
    pub fn new() -> Result<Self, Box<dyn std::error::Error>> {
        Self::with_url(DEFAULT_XREF_URL)
    }

    pub fn with_url(xref_urls: &str) -> Result<Self, Box<dyn std::error::Error>> {
        let mut databases: HashMap<String, OboDatabase> = HashMap::new();
        
        for xref_url in xref_urls.split(';') {
            let xref_url = xref_url.trim();
            if xref_url.is_empty() {
                continue;
            }
            
            let content = if xref_url.contains("://") {
                match ureq::get(xref_url).call() {
                    Ok(response) => {
                        response.into_string()?
                    }
                    Err(e) => {
                        eprintln!("db-xrefs.yaml failed to load from {}: {}. URLs will be missing in OLS3 API OBO xrefs.", xref_url, e);
                        continue;
                    }
                }
            } else {
                match std::fs::read_to_string(xref_url) {
                    Ok(content) => content,
                    Err(e) => {
                        eprintln!("db-xrefs.yaml failed to load from {}: {}. URLs will be missing in OLS3 API OBO xrefs.", xref_url, e);
                        continue;
                    }
                }
            };
            
            let entries: Vec<XrefEntry> = match serde_yaml::from_str(&content) {
                Ok(entries) => entries,
                Err(e) => {
                    eprintln!("Failed to parse db-xrefs.yaml: {}", e);
                    continue;
                }
            };
            
            for entry in entries {
                if let Some(entity_types) = entry.entity_types {
                    for entity_type in entity_types {
                        if let Some(url_syntax) = entity_type.url_syntax {
                            let db = OboDatabase {
                                database_id: entry.database.clone(),
                                database_name: entry.name.clone(),
                                url_syntax,
                            };
                            databases.insert(entry.database.to_lowercase(), db);
                            break; // Use first matching url_syntax
                        }
                    }
                }
            }
        }
        
        Ok(Self {
            xref_urls: xref_urls.to_string(),
            databases,
        })
    }

    pub fn get_xref_urls(&self) -> &str {
        &self.xref_urls
    }

    pub fn get_url_for_id(&self, database_id: &str, id: &str) -> Option<String> {
        let db = self.databases.get(&database_id.to_lowercase())?;
        Some(db.get_url_for_id(id))
    }
}

/// Map a CURIE to a URL using OBO db-xrefs and Bioregistry
pub fn map_curie(
    db_urls: &OboDatabaseUrlService,
    bioregistry: &mut crate::bioregistry::Bioregistry,
    database_id: &str,
    entry_id: &str,
) -> Option<CurieMapResult> {
    // Check GO db-xrefs for a URL
    if let Some(url) = db_urls.get_url_for_id(database_id, entry_id) {
        return Some(CurieMapResult {
            url,
            source: db_urls.get_xref_urls().to_string(),
        });
    }
    
    // Check bioregistry for a URL
    if let Some(url) = bioregistry.get_url_for_id(database_id, entry_id) {
        return Some(CurieMapResult {
            url,
            source: bioregistry.get_registry_url().to_string(),
        });
    }
    
    None
}
