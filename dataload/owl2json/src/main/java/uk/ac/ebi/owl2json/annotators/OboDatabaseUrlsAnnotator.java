package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OboDatabaseUrlService;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.PropertySet;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;

import java.util.List;

public class OboDatabaseUrlsAnnotator {

    public static final OboDatabaseUrlService dbUrls = new OboDatabaseUrlService();

    public static void annotateOboDatabaseUrls(OwlGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {

            OwlNode c = graph.nodes.get(id);

            List<PropertyValue> oboXrefs = c.properties.getPropertyValues("http://www.geneontology.org/formats/oboInOwl#hasDbXref");

            if(oboXrefs != null) {
                for(PropertyValue v : oboXrefs) {
                    if(v.getType() == PropertyValue.Type.LITERAL) {

                        PropertyValueLiteral literal = (PropertyValueLiteral) v;
                        String shortForm = literal.getValue();

                        if(!shortForm.contains(":")) {
                            continue;
                        }

                        String oboDatabase = shortForm.substring(0, shortForm.indexOf(':'));
                        String oboId = shortForm.substring(shortForm.indexOf(':') + 1);

//                        System.out.println("Lookup database " + oboDatabase + " id " + oboId);

                        String url = dbUrls.getUrlForId(oboDatabase, oboId);

                        if(url != null) {

//                            System.out.println("URL " + url);

                            // if there's already reification, add the new info to those axioms
                            if(v.axioms.size() > 0) {
                                for(PropertySet axiom : v.axioms) {
                                    axiom.addProperty("url", PropertyValueLiteral.fromString(url));
                                }
                            } else {
                                // reify with the new info
                                PropertySet axiom = new PropertySet();
                                axiom.addProperty("url", PropertyValueLiteral.fromString(url));
                                c.properties.annotatePropertyWithAxiom(
                                        "http://www.geneontology.org/formats/oboInOwl#hasDbXref",
                                        v,
                                        axiom,
                                        graph);
                            }
                        }
                    }
                }
            }
        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate obo database urls: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }
}
