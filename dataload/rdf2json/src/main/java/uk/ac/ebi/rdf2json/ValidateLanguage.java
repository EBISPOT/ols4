package uk.ac.ebi.rdf2json;

public class ValidateLanguage {

    public static String validateLanguage(String lang) {

        // https://github.com/EBISPOT/ols4/issues/1100

        if(lang == null ||
            lang.equals("") || 
            !lang.matches("^[a-zA-Z0-9\\-]+$") ||
            lang.length() > 10
        ) {
            return "";
        }

        return lang;
    }
    
}
