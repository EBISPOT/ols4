package uk.ac.ebi.spot.ols.repository;

import java.util.List;

public class Validation {

    public static void validateLang(String lang) {

        if (!lang.matches("^[-A-Za-z]+$"))
            throw new IllegalArgumentException();

    }

    public static void validateOntologyId(String ontologyId) {

        if (!ontologyId.matches("^[-A-Za-z0-9_]+$"))
            throw new IllegalArgumentException();

    }

    public static void validateVector(List<Double> vector) {

        if(vector.size() != 1536) {
            throw new IllegalArgumentException("expected 1536 element vector");
        }

    }

}
