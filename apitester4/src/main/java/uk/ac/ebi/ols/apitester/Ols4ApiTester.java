
package uk.ac.ebi.ols.apitester;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;

import com.google.gson.*;
import org.apache.commons.io.IOUtils;


public class Ols4ApiTester {

	int size = 1000;


	Gson gson;
	String url, outDir;
	boolean ols3only;
	boolean deep;
	String ontologyId;

	public Ols4ApiTester(String url, String outDir, boolean ols3only, boolean deep, String ontologyId) {

		gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

		if(url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}

		this.url = url;
		this.outDir = outDir;
		this.ols3only = ols3only;
		this.deep = deep;
		this.ontologyId = ontologyId;
	}

	public boolean test() throws MalformedURLException, IOException {

		System.out.println("Waiting for API to become available...");

		JsonElement ontologies = null;

		int MAX_RETRIES = 60;

		for(int nRetries = 0; nRetries < MAX_RETRIES; ++ nRetries) {

			ontologies = ols3GetAll(url + "/api/ontologies");

			if(!ontologies.isJsonArray()) {
				try {
					System.out.println("Result of /api/ontologies was not an array; waiting 5 seconds and trying again");
					Thread.sleep(5000);
				} catch(InterruptedException e) {}

				continue;
			} else {
				break;
			}
		}

		System.out.println("API is available now");

		if(ontologyId != null) {
			System.out.println("Testing only ontologyId " + ontologyId);
			return testOntology(ontologyId);
		} else {
			System.out.println("Testing all ontologies");

			write(outDir + "/ontologies.json", ontologies);

			if(ontologies == null || !ontologies.isJsonArray()) {
				System.out.println("No ontologies returned! :-(");
				return false;
			} else {
				System.out.println("Got " + ontologies.getAsJsonArray().size() + " ontologies");
			}

			if(!ols3only) {
				JsonElement v2Ontologies = ols4GetAll(url + "/api/v2/ontologies");
				write(outDir + "/v2/ontologies.json", v2Ontologies);
			}

			List<String> ontologyIds = new ArrayList();
			for(JsonElement ontology : ontologies.getAsJsonArray()) {
				ontologyIds.add(ontology.getAsJsonObject().get("ontologyId").getAsString());
			}

			boolean success = true;

			for(String ontologyId : ontologyIds) {
				if(!testOntology(ontologyId)) {
					success = false;
				}
			}

			return success;
		}

	}

	public boolean testOntology(String ontologyId) throws IOException{


		/// v1

		JsonElement classes = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/terms?size=" + size);
		write(outDir + "/ontologies/" + ontologyId + "/terms.json", classes);

		JsonElement properties = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/properties?size=" + size);
		write(outDir + "/ontologies/" + ontologyId + "/properties.json", properties);

		JsonElement individuals = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/individuals?size=" + size);
		write(outDir + "/ontologies/" + ontologyId + "/individuals.json", individuals);

		if(deep) {
			for(JsonElement _class : classes.getAsJsonArray()) {

				String iri = _class.getAsJsonObject().get("iri").getAsString();
				String doubleEncodedIri = doubleEncode(iri);

				JsonElement classJson = get(url + "/api/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "?size=" + size);
				write(outDir + "/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + ".json", classJson);

				JsonElement parentsJson = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/parents?size=" + size);
				write(outDir + "/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/parents.json", parentsJson);

				JsonElement ancestorsJson = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/ancestors?size=" + size);
				write(outDir + "/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/ancestors.json", ancestorsJson);

				JsonElement hierarchicalParentsJson = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/hierarchicalParents?size=" + size);
				write(outDir + "/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/hierarchicalParents.json", hierarchicalParentsJson);

				JsonElement hierarchicalAncestorsJson = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/hierarchicalAncestors?size=" + size);
				write(outDir + "/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/hierarchicalAncestors.json", hierarchicalAncestorsJson);

				JsonElement childrenJson = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/children?size=" + size);
				write(outDir + "/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/children.json", childrenJson);

				JsonElement descendantsJson = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/descendants?size=" + size);
				write(outDir + "/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/descendants.json", descendantsJson);

				JsonElement hierarchicalChildrenJson = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/hierarchicalChildren?size=" + size);
				write(outDir + "/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/hierarchicalChildren.json", hierarchicalChildrenJson);

				JsonElement hierarchicalDescendantsJson = ols3GetAll(url + "/api/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/hierarchicalDescendants?size=" + size);
				write(outDir + "/ontologies/" + ontologyId + "/terms/" + doubleEncodedIri + "/hierarchicalDescendants.json", hierarchicalDescendantsJson);
			}

			for(JsonElement property : properties.getAsJsonArray()) {

				String iri = property.getAsJsonObject().get("iri").getAsString();
				String doubleEncodedIri = doubleEncode(iri);

				JsonElement propertyJson = get(url + "/api/ontologies/" + ontologyId + "/properties/" + doubleEncodedIri);
				write(outDir + "/ontologies/" + ontologyId + "/properties/" + doubleEncodedIri + ".json", propertyJson);

				// TODO
			}

			for(JsonElement individual : individuals.getAsJsonArray()) {

				String iri = individual.getAsJsonObject().get("iri").getAsString();
				String doubleEncodedIri = doubleEncode(iri);

				JsonElement individualJson = get(url + "/api/ontologies/" + ontologyId + "/individuals/" + doubleEncodedIri);
				write(outDir + "/ontologies/" + ontologyId + "/individuals/" + doubleEncodedIri + ".json", individualJson);

				// TODO
			}
		}

		if(ols3only) {
			return true;
		}



		/// v2

		JsonElement v2Entities = ols4GetAll(url + "/api/v2/ontologies/" + ontologyId + "/entities?size=" + size);
		write(outDir + "/v2/ontologies/" + ontologyId + "/entities.json", v2Entities);

		JsonElement v2Classes = ols4GetAll(url + "/api/v2/ontologies/" + ontologyId + "/classes?size=" + size);
		write(outDir + "/v2/ontologies/" + ontologyId + "/classes.json", v2Classes);

		JsonElement v2Properties = ols4GetAll(url + "/api/v2/ontologies/" + ontologyId + "/properties?size=" + size);
		write(outDir + "/v2/ontologies/" + ontologyId + "/properties.json", v2Properties);

		JsonElement v2Individuals = ols4GetAll(url + "/api/v2/ontologies/" + ontologyId + "/individuals?size=" + size);
		write(outDir + "/v2/ontologies/" + ontologyId + "/individuals.json", v2Individuals);


		if(deep) {
			for(JsonElement v2Entity : v2Entities.getAsJsonArray()) {

				String iri = v2Entity.getAsJsonObject().get("iri").getAsString();
				String doubleEncodedIri = doubleEncode(iri);

				JsonElement entityJson = get(url + "/api/ontologies/" + ontologyId + "/entities/" + doubleEncodedIri);
				write(outDir + "/ontologies/" + ontologyId + "/entities/" + doubleEncodedIri + ".json", entityJson);

				// TODO
			}

			for(JsonElement v2Class : v2Classes.getAsJsonArray()) {

				String iri = v2Class.getAsJsonObject().get("iri").getAsString();
				String doubleEncodedIri = doubleEncode(iri);

				JsonElement classJson = get(url + "/api/ontologies/" + ontologyId + "/classes/" + doubleEncodedIri);
				write(outDir + "/ontologies/" + ontologyId + "/classes/" + doubleEncodedIri + ".json", classJson);

				// TODO
			}

			for(JsonElement v2Property : v2Properties.getAsJsonArray()) {

				String iri = v2Property.getAsJsonObject().get("iri").getAsString();
				String doubleEncodedIri = doubleEncode(iri);

				JsonElement propertyJson = get(url + "/api/ontologies/" + ontologyId + "/properties/" + doubleEncodedIri);
				write(outDir + "/ontologies/" + ontologyId + "/properties/" + doubleEncodedIri + ".json", propertyJson);

				// TODO
			}

			for(JsonElement v2Individual : v2Individuals.getAsJsonArray()) {

				String iri = v2Individual.getAsJsonObject().get("iri").getAsString();
				String doubleEncodedIri = doubleEncode(iri);

				JsonElement individualJson = get(url + "/api/ontologies/" + ontologyId + "/individuals/" + doubleEncodedIri);
				write(outDir + "/ontologies/" + ontologyId + "/individuals/" + doubleEncodedIri + ".json", individualJson);

				// TODO
			}
		}

		return true;
	}

	public void write(String path, JsonElement element) throws FileNotFoundException, IOException {

		Files.createDirectories(  Paths.get(path).toAbsolutePath().getParent() );

		File file = new File(path);

		FileOutputStream os = new FileOutputStream(file);

		try {
			os.write( gson.toJson(element).getBytes());
		} finally {
			os.close();
		}
	}

	// get all, HATEOAS style (OLS3 API)
	public JsonElement ols3GetAll(String url) {

		try {
			JsonArray allEntries = new JsonArray();

			for(JsonObject res = get(url).getAsJsonObject();;) {

				if(res.has("error")) {
					return res;
				}

				JsonElement embedded = res.get("_embedded");

				if(embedded == null) {
					break;
				}

				String resourceName = embedded.getAsJsonObject().keySet().iterator().next();
				JsonArray entries = embedded.getAsJsonObject().get(resourceName).getAsJsonArray();
				allEntries.addAll(entries);

				JsonObject links = res.get("_links").getAsJsonObject();

				JsonElement nextObj = links.get("next");

				if(nextObj == null) {
					System.out.println("no next link, we are done");
					break;
				}

				String next = nextObj.getAsJsonObject().get("href").getAsString();

				System.out.println("next link is " + next);

				res = get(next).getAsJsonObject();
			}

			System.out.println("sorting and returning result...");
			return deepSort(removeDates(normalizeURLs(allEntries))).getAsJsonArray();

		} catch(Exception e) {
			return gson.toJsonTree(e);
		}
	}

	// get all paginated style (OLS4 API)
	public JsonElement ols4GetAll(String url) {

		try {
			JsonArray allEntries = new JsonArray();

			String reqUrl = url + "?page=0&numElements=100";

			for(JsonObject res = get(url).getAsJsonObject();;) {

				int page = res.get("page").getAsInt();
				int numElements = res.get("numElements").getAsInt();
				// int totalPages = res.get("totalPages").getAsInt();
				// int totalElements = res.get("totalElements").getAsInt();
				JsonArray elements = res.get("elements").getAsJsonArray();

				allEntries.addAll(elements);

				if(numElements < 100) {
					break;
				}

				reqUrl = url + "?page=" + page + "&numElements=100";
				res = get(reqUrl).getAsJsonObject();
			}

			System.out.println("sorting and returning result...");
			return deepSort(removeDates(normalizeURLs(allEntries))).getAsJsonArray();

		} catch(Exception e) {
			return gson.toJsonTree(e);
		}
	}

	public static JsonElement get(String url) throws IOException {

		System.out.println("GET " + url);

		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		InputStream is = null;

		if (100 <= conn.getResponseCode() && conn.getResponseCode() <= 399) {
			is = conn.getInputStream();
		} else {
			is = conn.getErrorStream();
		}
		Reader reader = new InputStreamReader(is, "UTF-8");
        return JsonParser.parseReader(reader);
	}

	public JsonElement normalizeURLs(JsonElement element) {

		if(element.isJsonArray()) {

			JsonArray arr = element.getAsJsonArray();
			JsonArray res = new JsonArray();
			
			for(int i = 0; i < arr.size(); ++ i) {
				res.add(normalizeURLs(arr.get(i)));
			}

			return res;

		} else if(element.isJsonObject()) {

			JsonObject obj = element.getAsJsonObject();
			JsonObject res = new JsonObject();

			for(Entry<String, JsonElement> entry : obj.entrySet()) {
				res.add(entry.getKey(), normalizeURLs(entry.getValue()));
			}

			return res;

		} else if(element.isJsonPrimitive()) {

			JsonPrimitive p = element.getAsJsonPrimitive();

			if(p.isString()) {

				String replaced = p.getAsString().replace(url, "<base>");
				return new JsonPrimitive(replaced);
			}
		} 

		return element.deepCopy();
	}

	public JsonElement deepSort(JsonElement element) {

		if(element.isJsonArray()) {

			JsonArray arr = element.getAsJsonArray();

			JsonElement[] elems = new JsonElement[arr.size()];

			for(int i = 0; i < arr.size(); ++ i) {
				elems[i] = deepSort(arr.get(i));
			}
			
			Arrays.sort(elems, Comparator.comparing(elem -> gson.toJson(elem)));

			JsonArray res = new JsonArray();

			for(int i = 0; i < elems.length; ++ i) {
				res.add(elems[i]);
			}

			return res;

		} else if(element.isJsonObject()) {

			JsonObject obj = element.getAsJsonObject();

			TreeSet<String> sortedKeys = new TreeSet<String>(obj.keySet());

			JsonObject res = new JsonObject();

			for(String key : sortedKeys) {
				res.add(key, deepSort(obj.get(key)));
			}

			return res;

		}

		return element.deepCopy();
	}

	public static JsonElement removeDates(JsonElement element) {

		if(element.isJsonArray()) {

			JsonArray arr = element.getAsJsonArray();
			JsonArray res = new JsonArray();
			
			for(int i = 0; i < arr.size(); ++ i) {
				res.add(removeDates(arr.get(i)));
			}

			return res;

		} else if(element.isJsonObject()) {

			JsonObject obj = element.getAsJsonObject();
			JsonObject res = new JsonObject();

			for(Entry<String, JsonElement> entry : obj.entrySet()) {

				if(entry.getKey().equals("loaded")) {
					res.add(entry.getKey(), new JsonPrimitive("<loaded>"));
					continue;
				}

				if(entry.getKey().equals("updated")) {
					res.add(entry.getKey(), new JsonPrimitive("<updated>"));
					continue;
				}

				res.add(entry.getKey(), removeDates(entry.getValue()));
			}

			return res;

		}

		return element.deepCopy();
	}

	/*
	public String removeBaseUrl(String url, String baseUrl) {

		if(!url.startsWith(baseUrl)) {
			throw new RuntimeException("url does not start with base url");
		}

		return url.substring(url.length());
	}*/

	public static String doubleEncode(String iri) throws UnsupportedEncodingException {

		return URLEncoder.encode(URLEncoder.encode(iri, "utf-8"), "utf-8");
	}

	public static String sanitizeFilename(String filename) {
		return filename.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
	}

}
