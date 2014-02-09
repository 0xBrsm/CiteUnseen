/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */

package citeunseen;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*; 
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.*;
import org.apache.commons.codec.binary.Base64;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class WebSearch {
	private Engine engine;
	
	// defaults, setters only
	private int maxConnections = 100;
	private int maxRetries = 4;
	
	// track thread counts
	private AtomicInteger threadsClosed = new AtomicInteger(0);
	private AtomicInteger threadsRetried = new AtomicInteger(0);
	private AtomicInteger threadsErrored = new AtomicInteger(0);

	//
	// subclass for individual search threads
	//
	class SearchThread implements Callable<String> {
		private final CloseableHttpClient httpClient;
		private final HttpGet httpGet;
		private final int id;
		private final int totalThreads;

		SearchThread (CloseableHttpClient httpClient, HttpGet httpGet, int id, int totalThreads) {
			this.httpClient = httpClient;
			this.httpGet = httpGet;
			this.id = id;
			this.totalThreads = totalThreads;
        }
		
		@Override
		public String call () {
			ResponseHandler<String> responseHandler = new BasicResponseHandler();
			String response = "";

			int retries = 0;
			int percentComplete = 0;
			while (true) {
				try {
					response = httpClient.execute(httpGet, responseHandler);
					percentComplete = 100 * threadsClosed.incrementAndGet() / totalThreads;
					System.out.print("Percent complete: "+percentComplete+"% (Retries: "+threadsRetried+" Errors: "+threadsErrored+")\r");
					break;
				} catch (Exception e) {
					if (retries < maxRetries) {
						retries++;
						percentComplete = 100 * threadsClosed.get() / totalThreads;
						System.out.print("Percent complete: "+percentComplete+"% (Retries: "+threadsRetried.incrementAndGet()+" Errors: "+threadsErrored+")\r");
					} else {
						percentComplete = 100 * threadsClosed.incrementAndGet() / totalThreads;
						System.out.println("Thread "+id+" error on "+GoogleAccountInfo.getAPI(id)+": "+e.getMessage());
						System.out.print("Percent complete: "+percentComplete+"% (Retries: "+threadsRetried+" Errors: "+threadsErrored.incrementAndGet()+")\r");
						break;
					}
				}
			}	
			return response;
		}
	}
	//---
	
	// Constructor
	//
	public WebSearch (Engine engine) {
		this.engine = engine;
	}
	
	// get/set key attributes
	public void setMaxRetries (int maxRetries) {
		this.maxRetries = maxRetries;
	}
	
	public void setMaxConnections (int maxConnections) {
		this.maxConnections = maxConnections;
	}

	// Cache import/export of search results
	//
	public static Object importCache(String path) {
		System.out.print("Loading cache...");
		long startTime = System.nanoTime();
		Object obj = null;
		
		try (
			FileInputStream dat = new FileInputStream(path);
			ObjectInputStream input = new ObjectInputStream(dat);
		) {
			obj = input.readObject();
			
			long endTime = System.nanoTime();
			long duration = endTime - startTime;
			System.out.println("complete! ("+TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS)/1000.0+"s)");
			
		} catch (Exception e) {
			System.out.println("failed! "+e.getMessage());
		}
		
		return obj;
	}
	
	public static void exportCache(Object obj, String path) {
		System.out.print("Saving cache...");
		long startTime = System.nanoTime();		
		
		try (
			FileOutputStream dat = new FileOutputStream(path);
			ObjectOutputStream output = new ObjectOutputStream(dat);		
		) {
			output.writeObject(obj);
			
			long endTime = System.nanoTime();
			long duration = endTime - startTime;
			System.out.println("complete! ("+TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS)/1000.0+"s)");
			
		} catch (IOException e) {
			System.out.println("failed! "+e.getMessage());
		}
	}
	
	// for converting cache, probably not needed anymore
	//
	@SuppressWarnings("unchecked")
	public static void convertCache(String path) throws Exception {
		String query = "";
	
		FileInputStream dat = new FileInputStream(path);
		ObjectInputStream input = new ObjectInputStream(dat);
		
		ArrayList<String> serpStrings = (ArrayList<String>)input.readObject();
		HashMap<String, String> serps = new HashMap<>();
		JsonParser parser = new JsonParser();

		System.out.println("Converting from array of Strings to Map...");
		for (String serp : serpStrings) {
			JsonObject json = parser.parse(serp).getAsJsonObject();
			if (path.contains(".Google."))
				query = json.getAsJsonObject("queries").getAsJsonArray("request").get(0).getAsJsonObject().getAsJsonPrimitive("searchTerms").getAsString();
			else if (path.contains(".Bing."))
				query = json.getAsJsonObject("d").getAsJsonArray("results").get(0).getAsJsonObject().getAsJsonObject("__metadata").getAsJsonPrimitive("uri").getAsString();
			String ngram = query.substring(query.indexOf("\"")+1, query.lastIndexOf("\""));
			serps.put(ngram, serp);
		}
		exportCache(serps, path);
	}

	// Main function, perform search
	//	
	public Map<String, String> search (SourceText sourceText) throws Exception {
		Map<String, NGram> nGrams = sourceText.getNGrams();
		Map<String, String> serps = new HashMap<String, String>();

			if (engine == Engine.Google)
				throw new Exception("Web search currently unavailable.");

			System.out.println("Searching "+engine+" for "+nGrams.values().size()+" ngrams...");
			
			ExecutorService executor = Executors.newFixedThreadPool(maxConnections);
			PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
			manager.setMaxTotal(maxConnections);
			manager.setDefaultMaxPerRoute(maxConnections);
			CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(manager).build();
			
			Map<String, Future<String>> tmp = new HashMap<>();
			
			int id = 0;
			int total = nGrams.values().size();
			for (NGram nGram : nGrams.values()) {
				String ngram = nGram.toString();
				HttpGet httpGet = getHttpGet(ngram, id);
				SearchThread thread = new SearchThread(httpClient, httpGet, id, total);
				tmp.put(ngram, executor.submit(thread));
				id++;
			}
			executor.shutdown();
			executor.awaitTermination((long)(nGrams.size() / 10), TimeUnit.SECONDS);	// failsafe, no more than ~30 seconds per page
			
			// Stupid hack because I can't serialize Futures
			// FIXME, can I just do serps = new Map<String, String>(tmp); ??
			for (Map.Entry<String, Future<String>> entry : tmp.entrySet()) {
				String json = entry.getValue().get();
				serps.put(entry.getKey(), json);
			}
			System.out.println();
			httpClient.close();
			System.out.println("Searching completed. All http connections closed.");
		

		return serps;
	}
	
	// Get an HTTP requestor for our query
	//
	private HttpGet getHttpGet(String query, int id) throws Exception {
		String searchURL = "";
		String headerName = "";
		String headerValue = "";
		HttpGet httpGet;
		
		switch (engine) {
			case Bing:
				String accountKey = BingAccountInfo.getKey(id);
				byte[] accountKeyBytes = Base64.encodeBase64(accountKey.getBytes());
				String accountKeyEnc = new String(accountKeyBytes);
				
				headerName = "Authorization";
				headerValue = "Basic " + accountKeyEnc;
				query = "%27%22"+query.replaceAll(" ","%20")+"%22%27";
				searchURL = "https://api.datamarket.azure.com/Bing/SearchWeb/Web?$format=json&Query="+query;
//				searchURL = "https://api.datamarket.azure.com/Bing/SearchWeb/Composite?Sources=%27web%27$format=json&Query="+query;
					
				break;
			case Google:
				String accountInfo = GoogleAccountInfo.getKey(id);
				String cx = accountInfo.substring(0, 33);
				String key = accountInfo.substring(34);

				headerName = "Accept";
				headerValue = "application/json";
				query = "%22"+query.replaceAll(" ","+")+"%22";
				searchURL = "https://www.googleapis.com/customsearch/v1?key="+key+"&cx="+cx+"&q="+query+"&alt=json";
				searchURL = "https://www.googleapis.com/customsearch/v1?key="+key+"&cx="+cx+"&q="+query+"&alt=json";
					
				break;
		}
		httpGet = new HttpGet(searchURL);
		httpGet.setHeader(headerName, headerValue);
		
		return httpGet;
	}

	// Process all our URL results, eliminating duplicates and building a list of ngrams which returned each URL
	//
	public Collection<URLResult> getURLResults (SourceText sourceText, Map<String, String> serps) throws Exception {
		return getURLResults(sourceText, serps, true);
	}
	
	public Collection<URLResult> getURLResults (SourceText sourceText, Map<String, String> serps, boolean snippetSearch) throws Exception {
		System.out.println("Parsing "+serps.size()+" SERPs...");
				
		String resultsElement = "";
		String urlElement = "";
		String snippetElement = "";
		
		// Make allowances for engine specific JSON formats
		if (engine == Engine.Google) {
			resultsElement = "items";
			urlElement = "link";
			snippetElement = "snippet";
		} else if (engine == Engine.Bing) {
			resultsElement = "results";	
			urlElement = "Url";
			snippetElement = "Description";
		}

		// Cycle through all the ngrams, adding urls as we go
		//
		int total = 0;
		JsonParser parser = new JsonParser();
		Map<String, URLResult> urlResults = new HashMap<>();
		for (NGram nGram : sourceText.getNGrams().values()) {
			Set<Integer> nGramPositions = nGram.getPositions();
			
			String ngram = nGram.toString();
			String serp = serps.get(ngram);

			if (serp == null) continue;		// skip blank entries
			if (serp.equals("")) continue;	// skip blank entries

			total++;
			JsonObject json = parser.parse(serp).getAsJsonObject();
			if (engine == Engine.Bing) json = json.getAsJsonObject("d");
			JsonArray results = json.getAsJsonArray(resultsElement);

			if (engine == Engine.Google) {
				JsonObject searchInformation = json.getAsJsonObject("searchInformation");
				long totalResults = searchInformation.getAsJsonPrimitive("totalResults").getAsLong();
				long currentTotal = nGram.getTotalResults(); 
				if (currentTotal < totalResults) nGram.setTotalResults(totalResults);
			}

			// Cycle through all results, if there are any results
			//
			if (results != null) for (JsonElement e : results) {
				JsonObject result = e.getAsJsonObject();
				String url = result.getAsJsonPrimitive(urlElement).getAsString();
				String snippet = result.getAsJsonPrimitive(snippetElement).getAsString();

				Set<NGram> matches;
				if (snippetSearch) 												// if we're searching snippets...
						matches = searchSnippet(snippet, sourceText);			//  locate any document ngrams in the snippet as these will also match the url
				else 	matches = new HashSet<>();								//	otherwise just create an empty map
				matches.add(nGram);												// add our known curent nGram to the list of matches found in the snippet (if any)
				
				URLResult urlResult = urlResults.get(url);						// is this URL already in the list?
				if (urlResult == null) urlResult = new URLResult(url);			// if not, create a new URL object
				if (urlResult.add(matches)) urlResults.put(url, urlResult);		// if these nGrams are not already in the list of hits, add them
				
				for (NGram nGramMatch : matches) {
					nGramMatch.addResult(urlResult);					// add this URL to each nGram as a result
					long newTotal = nGramMatch.getTotalResults() + 1;		//  make sure we add one to the total results for this new match
					nGramMatch.setTotalResults(newTotal);					//  this is key for scoring matches which have few or no results initially
				}
			}
		}
		System.out.println("Found "+total+" SERPs with data...");
		return urlResults.values();	// we only need the values now
	}
	
	// Search a snippet of text to see if it has any ngrams from our sourcetext
	//
	private Set<NGram> searchSnippet (String snippet, SourceText sourceText) {
		// use our sourceText parameters as a basis to create snippet ngrams
		Set<String> snippetNGrams = SourceText.generateNGrams(snippet, sourceText);	
		Set<NGram> matches = new HashSet<>();
		
		for (String ngram : snippetNGrams) {
			NGram nGram = sourceText.getNGrams().get(ngram);	// check if this snippet ngram is in the document
			if (nGram == null) continue;						// if it isn't, move on
			matches.add(nGram);									// if it is, add it to the matches
		}	
		return matches;											// return all matches
	}
}

