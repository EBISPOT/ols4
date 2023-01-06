package uk.ac.ebi.spot.ols.model.v1;

import java.util.*;

public class V1OboSynonym {

    public String name;
    public String scope;
    public String type;
    public List<V1OboXref> xrefs;

    @Override
    public boolean equals(Object other) {

        if(! (other instanceof V1OboSynonym)) {
            return false;
        }

        return Objects.equals(((V1OboSynonym) other).name, this.name) &&
                Objects.equals(((V1OboSynonym) other).scope, this.scope)  &&
                Objects.equals(((V1OboSynonym) other).type, this.type)  &&
                ((V1OboSynonym) other).xrefs.equals(this.xrefs);
    }
}

