
package uk.ac.ebi.ols4.predownloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;

public class OntologyDownloaderThread implements Runnable {

    BulkOntologyDownloader downloader;
    String ontologyUrl;
    Consumer<Collection<String>> consumeImports;

    public OntologyDownloaderThread(BulkOntologyDownloader downloader, String ontologyUrl, Consumer<Collection<String>> consumeImports) {

        super();

        this.downloader = downloader;
        this.ontologyUrl = ontologyUrl;
	this.consumeImports = consumeImports;
    }


    @Override
    public void run() {

	if(!ontologyUrl.contains("://")) {
		// Nothing to download, this is a local file already
		return;
	}

	String path = downloader.downloadPath + "/" + urlToFilename(ontologyUrl);

	System.out.println(Thread.currentThread().getName() + " Starting download for " + ontologyUrl + " to " + path);

	Set<String> importUrls = new LinkedHashSet<>();

        long begin = System.nanoTime();

        try {

            downloadURL(ontologyUrl, path);

            // parse to look for imports only
            createParser().source(new FileInputStream(path)).parse(new StreamRDF() {
                public void start() {}
                public void quad(Quad quad) {}
                public void base(String base) {}
                public void prefix(String prefix, String iri) {}
                public void finish() {}
                public void triple(Triple triple) {

                    if (triple.getPredicate().getURI().equals("http://www.w3.org/2002/07/owl#imports")) {
                        importUrls.add(triple.getObject().getURI());
                    }
                }
            });

        } catch (Exception e) {

            e.printStackTrace();
        }

        long end = System.nanoTime();

        System.out.println(Thread.currentThread().getName() + " Downloading and parsing for imports " + ontologyUrl + " took " + ((end-begin) / 1000 / 1000 / 1000) + "s");

        consumeImports.accept(importUrls);
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

    private static void downloadURL(String url, String filename) throws FileNotFoundException, IOException {

	RequestConfig config = RequestConfig.custom()
			.setConnectTimeout(5000)
			.setConnectionRequestTimeout(5000)
			.setSocketTimeout(5000).build();

	CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();

        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            entity.writeTo(new FileOutputStream(filename));
        }
    }


}
