package uk.ac.ebi.owl2json.operations;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.NodeFactory;

import uk.ac.ebi.owl2json.OwlTranslator;

public class HierarchyFlagsAnnotator {

	public static void annotateHierarchyFlags(OwlTranslator translator) {

		long startTime3 = System.nanoTime();




		long endTime3 = System.nanoTime();
		System.out.println("annotate types: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}

