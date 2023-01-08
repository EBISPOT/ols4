
package uk.ac.ebi.ols.apitester;

import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class App 
{
    public static void main( String[] args ) throws MalformedURLException, IOException
    {
	Options options = new Options();

        Option optUrl = new Option(null, "url", true, "URL of a running OLS4 instance");
        optUrl.setRequired(false);
        options.addOption(optUrl);

        Option optOutDir = new Option(null, "outDir", true, "Directory to write output to");
        optOutDir.setRequired(true);
        options.addOption(optOutDir);

        Option optCompareUrl = new Option(null, "compareUrl", true, "URL of a second OLS4 instance to compare with (mutually exclusive with --compareDir)");
        optCompareUrl.setRequired(false);
        options.addOption(optCompareUrl);

        Option optCompareDir = new Option(null, "compareDir", true, "Directory to compare output with. If --compareUrl is specified files will be downloaded here.");
        optCompareDir.setRequired(true);
        options.addOption(optCompareDir);

        Option optOLS3only = new Option(null, "ols3-only", false, "Set to only test OLS3 endpoints, not OLS4 (useful if one or both of the instances is OLS3)");
        optOLS3only.setRequired(false);
        options.addOption(optOLS3only);

        Option optOntology = new Option(null, "ontology", true, "Optionally a specific ontology ID to test, rather than testing everything");
        optOntology.setRequired(false);
        options.addOption(optOntology);

        Option optDeep = new Option(null, "deep", false, "Set to retrieve every entity individually");
        optDeep.setRequired(false);
        options.addOption(optDeep);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);

            System.exit(1);
        }

        String url = cmd.getOptionValue("url");
        String outDir = cmd.getOptionValue("outDir");
        String compareUrl = cmd.getOptionValue("compareUrl");
        String compareDir = cmd.getOptionValue("compareDir");
        boolean ols3only = cmd.hasOption("ols3-only");
	boolean deep = cmd.hasOption("deep");
	String ontology = cmd.getOptionValue("ontology");

        boolean success = true;

	if (url != null) {
		if (!new Ols4ApiTester(url, outDir, ols3only, deep, ontology).test()) {
			System.out.println("Ols4ApiTester.test() reported failure");
			success = false;
		} else {
			System.out.println("Ols4ApiTester.test() reported success");
		}
	}

	if(compareUrl != null) {
		if(!new Ols4ApiTester(compareUrl, compareDir, ols3only, deep, ontology).test()) {
		System.out.println("Ols4ApiTester.test() reported failure for compareUrl");
		success = false;
		} else {
		System.out.println("Ols4ApiTester.test() reported success for compareUrl");
		}
	}

        if(compareDir != null) {
            if(!new RecursiveJsonDiff(outDir, compareDir).diff()) {
		System.out.println("RecursiveJsonDiff.diff() reported failure");
                success = false;
            } else {
		System.out.println("RecursiveJsonDiff.diff() reported success");
	    }
        }

	if(success) {
		System.out.println("apitester reported success; exit code 0");
		System.exit(0);
	} else {
		System.out.println("apitester reported failure; exit code 1");
		System.exit(1);
	}
    }
}
