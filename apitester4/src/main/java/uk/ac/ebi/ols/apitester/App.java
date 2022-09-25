
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
        optUrl.setRequired(true);
        options.addOption(optUrl);

        Option optOutDir = new Option(null, "outDir", true, "Directory to write output to");
        optOutDir.setRequired(true);
        options.addOption(optOutDir);

        Option optCompareDir = new Option(null, "compareDir", true, "Directory to compare output with");
        optCompareDir.setRequired(false);
        options.addOption(optCompareDir);

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
        String compareDir = cmd.getOptionValue("compareDir");

        boolean success = true;

        if(!new Ols4ApiTester(url, outDir).test()) {
	    System.out.println("Ols4ApiTester.test() reported failure");
            success = false;
        } else {
	    System.out.println("Ols4ApiTester.test() reported success");
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
