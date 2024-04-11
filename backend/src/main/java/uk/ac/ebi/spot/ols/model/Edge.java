package uk.ac.ebi.spot.ols.model;

/**
 * @author Erhun Giray TUNCAY
 * @email giray.tuncay@tib.eu
 * TIB-Leibniz Information Center for Science and Technology
 */
public class Edge {
    String source;
    String target;
    String label;
    String uri;

    public Edge(String source, String target, String label, String uri) {
        this.source = source;
        this.target = target;
        this.label = label;
        this.uri = uri;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public String getLabel() {
        return label;
    }

    public String getUri() {
        return uri;
    }

}
