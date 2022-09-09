
package uk.ac.ebi.ols4.downloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
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
	this.importUrls = new ArrayList<>();
    }

    public void run() {

	if(!ontologyUrl.contains("://")) {
		// Nothing to download, this is a local file already
		return;
	}

	String path = downloadPath + "/" + urlToFilename(ontologyUrl);

	System.out.println("Starting download for " + ontologyUrl + " to " + path);

        long begin = System.nanoTime();

	try {

		downloadURL(ontologyUrl, path);

		// parse to look for imports only
		createParser().source(new FileInputStream(path)).parse(this);

	} catch (IOException e) {

		e.printStackTrace();
	}

        long end = System.nanoTime();

        System.out.println("Downloading and parsing for imports " + ontologyUrl + " took " + ((end-begin) / 1000 / 1000 / 1000) + "s");
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



    private static void downloadURL(String url, String filename) throws FileNotFoundException, IOException {

	HttpClient client = HttpClientBuilder.create().build();

	HttpGet request = new HttpGet(url);
	HttpResponse response = client.execute(request);
	HttpEntity entity = response.getEntity();
	if (entity != null) {
		entity.writeTo(new FileOutputStream(filename));
	}

	
    }


}
