use rusty_leveldb::{Options, DB};
use serde_json::Value;
use std::path::Path;

pub struct LevelDB {
    db: DB,
}

impl LevelDB {
    pub fn open<P: AsRef<Path>>(path: P) -> Result<Self, Box<dyn std::error::Error>> {
        let mut options = Options::default();
        options.create_if_missing = true;
        let db = DB::open(path, options)?;
        Ok(Self { db })
    }

    #[allow(dead_code)]
    pub fn get(&mut self, key: &str) -> Option<Value> {
        match self.db.get(key.as_bytes()) {
            Some(bytes) => {
                match String::from_utf8(bytes) {
                    Ok(s) => serde_json::from_str(&s).ok(),
                    Err(_) => None,
                }
            }
            None => None,
        }
    }
}
