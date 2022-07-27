package uk.ac.ebi.spot.ols.service;


import org.springframework.stereotype.Component;

@Component
public class IndividualJsTreeBuilder extends AbstractJsTreeBuilder {
    @Override
    String getJsTreeParentQuery() {
        StringBuilder query = new StringBuilder();

        query.append("MATCH path = (n:Individual)-[r:INSTANCEOF|SUBCLASSOF*]->(parent)\n");
        query.append("USING INDEX n:Individual(iri)\n");
        query.append("WHERE n.ontology_name = {0} AND n.iri = {1}\n");
        query.append("UNWIND rels(path) as r1\n");
        query.append("WITH r1\n");
        query.append("WHERE startNode(r1).is_obsolete=false\n");
        query.append("RETURN distinct id(startNode(r1)) as startId, startNode(r1).iri as startIri, ");
        query.append("startNode(r1).label as startLabel, startNode(r1).has_children as hasChildren, r1.label as relation,");
        query.append("collect( distinct id(endNode(r1)) ) as parents");

        return query.toString();
    }

    @Override
    String getJsTreeParentQuery(ViewMode viewMode) {
        throw new UnsupportedOperationException("Implementation not necessary.");
    }

    @Override
    String getJsTreeParentSiblingQuery() {
        throw new UnsupportedOperationException("Implementation not necessary.");
    }

    @Override
    String getJsTreeParentSiblingQuery(ViewMode viewMode) {
        throw new UnsupportedOperationException("Implementation not necessary.");
    }

    @Override
    String getJsTreeChildrenQuery() {
        throw new UnsupportedOperationException("Implementation not necessary.");
    }

    @Override
    String getRootName() {
        return "Thing";
    }

    @Override
    String getJsTreeRoots(ViewMode viewMode) {
        throw new UnsupportedOperationException("Implementation not necessary.");
    }
}
