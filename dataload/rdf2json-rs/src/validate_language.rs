//! Port of the Java `ValidateLanguage` class (see ols4 issue #1100).

/// Return the language tag if it is a plausible BCP-47-ish tag, else "".
/// Matches `^[a-zA-Z0-9-]+$` with length <= 10.
pub fn validate_language(lang: &str) -> String {
    if lang.is_empty() || lang.len() > 10 {
        return String::new();
    }
    if !lang
        .chars()
        .all(|c| c.is_ascii_alphanumeric() || c == '-')
    {
        return String::new();
    }
    lang.to_string()
}
