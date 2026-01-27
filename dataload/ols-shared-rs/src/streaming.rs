use serde_json::{Map, Value};
use struson::reader::{JsonReader, JsonStreamReader, ValueType};

/// Read a JSON value from a struson JsonStreamReader
pub fn read_value<R: std::io::Read>(json: &mut JsonStreamReader<R>) -> Value {
    match json.peek().unwrap() {
        ValueType::Array => {
            let mut elems: Vec<Value> = Vec::new();
            json.begin_array().unwrap();
            while json.has_next().unwrap() {
                elems.push(read_value(json));
            }
            json.end_array().unwrap();
            Value::Array(elems)
        }
        ValueType::Object => {
            let mut obj: Map<String, Value> = Map::new();
            json.begin_object().unwrap();
            while json.has_next().unwrap() {
                let k = json.next_name_owned().unwrap();
                obj.insert(k, read_value(json));
            }
            json.end_object().unwrap();
            Value::Object(obj)
        }
        ValueType::String => Value::String(json.next_string().unwrap().to_string()),
        ValueType::Number => Value::Number(json.next_number().unwrap().unwrap()),
        ValueType::Boolean => Value::Bool(json.next_bool().unwrap()),
        ValueType::Null => {
            json.next_null().unwrap();
            Value::Null
        }
    }
}

/// Skip a value in a struson JsonStreamReader
pub fn skip_value<R: std::io::Read>(json: &mut JsonStreamReader<R>) {
    match json.peek().unwrap() {
        ValueType::Array => {
            json.begin_array().unwrap();
            while json.has_next().unwrap() {
                skip_value(json);
            }
            json.end_array().unwrap();
        }
        ValueType::Object => {
            json.begin_object().unwrap();
            while json.has_next().unwrap() {
                let _ = json.next_name_owned().unwrap();
                skip_value(json);
            }
            json.end_object().unwrap();
        }
        ValueType::String => {
            let _ = json.next_string().unwrap();
        }
        ValueType::Number => {
            let _ = json.next_number::<f64>().unwrap();
        }
        ValueType::Boolean => {
            let _ = json.next_bool().unwrap();
        }
        ValueType::Null => {
            json.next_null().unwrap();
        }
    }
}
