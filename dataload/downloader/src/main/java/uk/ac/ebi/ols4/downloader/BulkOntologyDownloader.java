
package uk.ac.ebi.ols4.downloader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BulkOntologyDownloader {

    static final int NUM_THREADS = 16;

    List<String> ontologyUrls;
    Set<String> alreadyStarted;
    String downloadPath;
    boolean loadLocalFiles;

    public BulkOntologyDownloader(List<String> ontologyUrls, String downloadPath, boolean loadLocalFiles) {
        this.ontologyUrls = ontologyUrls;
	this.alreadyStarted = new HashSet<>();
        this.downloadPath = downloadPath;
        this.loadLocalFiles = loadLocalFiles;
    }

    public void downloadAll() {

        Set<OntologyDownloaderThread> threads = new HashSet<>();

        while(ontologyUrls.size() > 0) {

            while(threads.size() < NUM_THREADS && ontologyUrls.size() > 0) {

                String nextUrl = ontologyUrls.get(0);
                ontologyUrls.remove(0);

		alreadyStarted.add(nextUrl);

                OntologyDownloaderThread t =
                    new OntologyDownloaderThread(this, nextUrl, downloadPath, loadLocalFiles);

                threads.add(t);

                t.run();
            }

            for (OntologyDownloaderThread thread : threads) {

                try {
                    thread.join();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

		for(String url : thread.getImports()) {
			if(!alreadyStarted.contains(url)) {
				ontologyUrls.add(url);
			}
		}
            }
        }

    }

    
}
