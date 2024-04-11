package uk.ac.ebi.spot.ols.model;

/**
 * @author Erhun Giray TUNCAY
 * @email giray.tuncay@tib.eu
 * TIB-Leibniz Information Center for Science and Technology
 */
public class Node {
    String iri;
    String label;

    public Node(String iri, String label) {
        this.iri = iri;
        this.label = label;
    }

    public String getIri() {
        return iri;
    }

    public String getLabel() {
        return label;
    }

}
