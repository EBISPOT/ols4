import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFParser;

import java.util.*;

public class RdfVocabularies {

    public class UriLabelMapping {
        String uri;
        Set<String> labels = new TreeSet<>();
        String source;
    };

    Map<String, UriLabelMapping> mappings = new HashMap<>();

    public static final List<String> vocabUrls = List.of(
            "https://www.w3.org/2000/01/rdf-schema"
    );

    public RdfVocabularies() {

        for(String vocab : vocabUrls) {

            Model model = ModelFactory.createDefaultModel();

            RDFParser.create()
                    .strict(false)
                    .checking(false)
                    .source(vocab)
                    .parse(model);

            for(var it = model.listSubjects(); it.hasNext(); ) {

                Resource subj = it.next();

                if(!subj.isURIResource())
                    continue;

                String uri = subj.getURI();

                UriLabelMapping mapping = new UriLabelMapping();
                mapping.uri = uri;
                mapping.source = vocab;

                mappings.put(uri, mapping);

                for(var it2 = subj.listProperties(model.getProperty("http://www.w3.org/2000/01/rdf-schema#label")); it2.hasNext(); ) {

                    Statement stm = it2.next();

                    mapping.labels.add(stm.getObject().asLiteral().getString());
                }
            }
        }
    }

    public UriLabelMapping getMapping(String iri) {
        return mappings.get(iri);
    }

}
