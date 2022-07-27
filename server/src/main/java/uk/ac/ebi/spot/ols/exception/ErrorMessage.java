package uk.ac.ebi.spot.ols.exception;

/**
 * @author Simon Jupp
 * @date 16/09/15
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
public class ErrorMessage {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ErrorMessage(String message) {

        this.message = message;
    }
}
