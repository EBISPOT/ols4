
package uk.ac.ebi.owl2json.downloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;

public class OntologyDownloaderThread extends Thread implements StreamRDF {

    BulkOntologyDownloader downloader;
    String ontologyUrl;
    String downloadPath;
    boolean loadLocalFiles;
    List<String> importUrls;


    public OntologyDownloaderThread(BulkOntologyDownloader downloader, String ontologyUrl, String downloadPath, boolean loadLocalFiles) {
        this.downloader = downloader;
        this.ontologyUrl = ontologyUrl;
        this.downloadPath = downloadPath;
        this.loadLocalFiles = loadLocalFiles;
    }

    public void run() {

        long begin = System.nanoTime();

        InputStream inputStream = null;

	    if(!ontologyUrl.contains("://")) {

            // Nothing to do, this is a local file already
            return;

	    } else {
            try {
                inputStream = new URL(ontologyUrl).openStream();
            } catch (MalformedURLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
	    }

        if(inputStream != null) {

            String path = downloadPath + "/" + urlToFilename(ontologyUrl);

            try {
                FileUtils.copyInputStreamToFile(inputStream, new File(path));

                // parse to look for imports only
                createParser().source(new FileInputStream(path)).parse(this);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        long end = System.nanoTime();

        System.out.println("Downloading " + ontologyUrl + " took " + ((end-begin) / 1000 / 1000 / 1000) + "s");
    }

    private String urlToFilename(String url) {
        return url.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
    }
    
    private RDFParserBuilder createParser() {

        return RDFParser.create()
                .forceLang(Lang.RDFXML)
                .strict(false)
                .checking(false);
    }


    public List<String> getImports() {
        return importUrls;
    }

    @Override
    public void start() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void triple(Triple triple) {
        
        if (triple.getPredicate().getURI().equals("http://www.w3.org/2002/07/owl#imports")) {
            importUrls.add(triple.getObject().getURI());
        }
    }

    @Override
    public void quad(Quad quad) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void base(String base) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void prefix(String prefix, String iri) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void finish() {
        // TODO Auto-generated method stub
        
    }

}
