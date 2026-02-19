use regex::Regex;
use std::sync::LazyLock;

/// Extract IRI from property names that have prefixes like "relatedTo+http://..."
/// or "negativePropertyAssertion+http://..."
///
/// If the property name matches the pattern "^[A-z]+\+(.+)$", returns the IRI part.
/// Otherwise returns the property name unchanged.

static NAME_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"^[A-Za-z]+\+(.+)$").unwrap()
});

pub fn extract(property_name: &str) -> &str {
    if let Some(captures) = NAME_PATTERN.captures(property_name) {
        if let Some(m) = captures.get(1) {
            // Return the captured group - we need to return a reference to the input
            // The capture matches a slice of the input string
            return m.as_str();
        }
    }
    property_name
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_simple() {
        assert_eq!(extract("label"), "label");
        assert_eq!(extract("http://example.org/foo"), "http://example.org/foo");
    }

    #[test]
    fn test_extract_with_prefix() {
        assert_eq!(extract("relatedTo+http://example.org/foo"), "http://example.org/foo");
        assert_eq!(extract("negativePropertyAssertion+http://example.org/bar"), "http://example.org/bar");
    }
}
