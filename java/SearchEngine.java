/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;

import java.io.*;
import java.util.*;
import java.util.concurrent.*; 
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.util.EntityUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
 
 public abstract class SearchEngine {
	//===========================================================//
	// inner class for individual search threads
	//===========================================================//
	
	// track thread counts
	private AtomicInteger threadsClosed;
	private AtomicInteger threadsRetried;
	private AtomicInteger threadsErrored;	
	
	private class SearchThread implements Callable<String> {
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
					HttpResponse httpResponse = httpClient.execute(httpGet);
					int statusCode = httpResponse.getStatusLine().getStatusCode();
					if (statusCode != 200) {
						String responseBody = EntityUtils.toString(httpResponse.getEntity());
						String errorMsg = SearchEngine.this.getSerpParser().parseError(responseBody);
						throw new Exception(statusCode+" - "+errorMsg);
					}				
					response = responseHandler.handleResponse(httpResponse);

					percentComplete = 100 * threadsClosed.incrementAndGet() / totalThreads;
					Dev.out.print("Percent complete: "+percentComplete+"% (Retries: "+threadsRetried+" Errors: "+threadsErrored+")\r");
					break;
				} catch (Exception e) {
					if (retries < maxRetries) {
						retries++;
						percentComplete = 100 * threadsClosed.get() / totalThreads;
						Dev.out.print("Percent complete: "+percentComplete+"% (Retries: "+threadsRetried.incrementAndGet()+" Errors: "+threadsErrored+")\r");
					} else {				
						percentComplete = 100 * threadsClosed.incrementAndGet() / totalThreads;
						Dev.out.print("\r                                                                 ");
						Dev.out.println("\rThread "+id+" error: "+e.getMessage());
						Dev.out.print("Percent complete: "+percentComplete+"% (Retries: "+threadsRetried+" Errors: "+threadsErrored.incrementAndGet()+")\r");
						break;
					}
				}
			}	
			return response;
		}
	}

	//
	// Abstract methods defined by subclass
	//
	abstract SerpParser getSerpParser ();	
	abstract Map<String, String> buildQuery (String query, int id);

	// The common name and HTML attribution label for this search engine
	private final String NAME;
	private final String LABEL;	
	
	// The total size of the searchable index.
	// For consistency, this is found by searching for "a"
	// and using the number of returned results.
	//
	private final long INDEX_SIZE;
	
	// configuration options, defaults
	private int maxConnections = 100;
	private int maxRetries = 4;	
	private int connectionDelay = 0;
	private String cachePath = "";
	private Cache cacheOption = null;	
	private boolean snippetSearch = true;
	private boolean console = false;
	
	// objects saved once search is executed
	private Map<String, SearchResult> searchResults;
	
	// Constructor
	public SearchEngine(String name, String label, long size) {
		NAME = name;
		LABEL = label;	
		INDEX_SIZE = size;
	}
	
	// Setters
	public void setMaxRetries (int maxRetries) {
		this.maxRetries = maxRetries;
	}
	
	public void setSnippetSearch (boolean snippetSearch) {
		this.snippetSearch = snippetSearch;
	}
	
	public void setMaxConnections (int maxConnections) {
		this.maxConnections = maxConnections;
	}
	
	public void setMaxConnectionsPerSecond (double maxConnectionsPerSecond) {
		if (maxConnectionsPerSecond <= 0)
				connectionDelay = 0;
		else	connectionDelay = (int)(1000 / maxConnectionsPerSecond);	// milliseconds
	}
	
	public void setCache (Cache cache, String path) {
		cachePath = path;
		cacheOption = cache;
	}
	
	public void setConsole (boolean console) {
		this.console = console;
	}

	// Getters
	public String toString () {
		return NAME;
	}
	
	public String getLabel () {
		return LABEL;
	}
	
	public Long indexSize () {
		return INDEX_SIZE;
	}
	
	public boolean snippetSearch () {
		return snippetSearch;
	}
	
	// For returning current text or results	
	public Map<String, SearchResult> getSearchResults () {
		return searchResults;
	}
	
	// For returning a specific result
	public SearchResult query (String query) {
		return searchResults.get(query);
	}
	
	// Wrapper to handle cache options and return search results in standard format
	//
	@SuppressWarnings("unchecked")
	public Map<String, SearchResult> search (SourceText sourceText) {
		Set<String> ngrams = sourceText.getNGrams();	// set as the default text until a new search is done

		Map<String, String> serps = null;
		if (cacheOption == Cache.IMPORT || cacheOption == Cache.USE) 
			serps = (Map<String, String>)Dev.importCache(cachePath);
			
		if (serps == null) {
			if (cacheOption == Cache.IMPORT) {
				Dev.out.println("Failed loading cache file: "+cachePath);
			} else {
				// Make sure the user intends to search if we are at the console
				String msg = "Searching "+this.toString()+" for "+ngrams.size()+" ngrams...";
				if (Dev.confirm(console, msg))
					serps = goSearch(ngrams);
				if (cacheOption == Cache.EXPORT || cacheOption == Cache.USE)
					Dev.exportCache(serps, cachePath);
			}
		}
		searchResults = parseResults(serps, sourceText);
				
		return searchResults;
	}
	


	// Main function, perform search
	//	
	private Map<String, String> goSearch (Set<String> ngrams) {
		// Disable all the HttpClient log noise
		org.apache.log4j.Logger.getLogger("org.apache.http").setLevel(org.apache.log4j.Level.OFF);				
		
		Map<String, String> serps = new HashMap<String, String>();
			
		ExecutorService executor = Executors.newFixedThreadPool(maxConnections);
		PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
		manager.setMaxTotal(maxConnections);
		manager.setDefaultMaxPerRoute(maxConnections);
		
		threadsClosed = new AtomicInteger(0);
		threadsRetried = new AtomicInteger(0);
		threadsErrored = new AtomicInteger(0);			
		
		try (
			CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(manager).build();
		) {
			Map<String, Future<String>> futures = new HashMap<>();
			
			int id = 0;
			int total = ngrams.size();
			for (String ngram : ngrams) {
				HttpGet httpGet = getHttpGet(ngram, id);
				Callable<String> thread = new SearchThread(httpClient, httpGet, id, total);
				futures.put(ngram, executor.submit(thread));
				id++;
				
				Thread.sleep(connectionDelay);
			}
			executor.shutdown();
			
			try {
				executor.awaitTermination((long)(ngrams.size() / 10), TimeUnit.SECONDS);	// failsafe, no more than ~30 seconds per page
			
				// Stupid hack because I can't serialize Futures
				// FIXME, can I just do serps = new Map<String, String>(tmp); ??
				for (Map.Entry<String, Future<String>> entry : futures.entrySet()) {
					String json = entry.getValue().get();
					serps.put(entry.getKey(), json);
				}
			} catch (InterruptedException e) {
				Dev.out.println(e.getMessage());
			}
			
			Dev.out.println();
			Dev.out.println("Searching completed. All http connections closed.");
		} catch (Exception e) {
			Dev.out.println(e.getMessage());
		}

		return serps;
	}
	
	// Get an HTTP requestor for our query
	//
	private HttpGet getHttpGet(String query, int id) {
		// build the query per the subclass
		Map<String, String> queryData = buildQuery(query, id);
	
		String headerName = queryData.get("headerName");
		String headerValue = queryData.get("headerValue");
		String searchURL = queryData.get("searchURL");

		HttpGet httpGet = new HttpGet(searchURL);
		httpGet.setHeader(headerName, headerValue);
			
		return httpGet;
	}
	
	public Map<String, SearchResult> parseResults (Map<String, String> serps, SourceText sourceText) {
		return parseResults(serps, sourceText.getNGrams(), sourceText, 0);
	}
	private Map<String, SearchResult> parseResults (Map<String, String> serps, Set<String> ngrams, SourceText sourceText, int level) {
		int total = serps.size();
		Dev.out.println("Parsing "+total+" SERPs...");	
	
		// Cycle through all the ngrams in the source text, adding urls as we go
		//
		Set<String> errors = new HashSet<>();
		SerpParser parser = this.getSerpParser();
		Map<String, SearchResult> searchResults = new HashMap<>();
		
		for (String ngram : ngrams) {
			String serp = serps.get(ngram);
			
			// track broken searches for re-searching
			if (serp == null || serp.equals("")) {
				errors.add(ngram);
				continue;
			}

			SearchResult parsedResult = parser.parse(serp);
			SearchResult searchResult = searchResults.get(ngram);
			if (searchResult == null) {
				searchResult = new SearchResult(this);
				searchResults.put(ngram, searchResult);
			}
			searchResult.merge(parsedResult);

			// If we're searching snippets, look for additional matches
			//
			if (snippetSearch()) {
				for (Map.Entry<String, String> parsedEntry : parsedResult.urlMap().entrySet()) {
					String url = parsedEntry.getKey();
					String snippet = parsedEntry.getValue();
					
					Set<String> matches = new HashSet<>();
					matches = searchSnippet(snippet, sourceText);
					
					for (String match : matches) {
						SearchResult matchResult = searchResults.get(match);
						if (matchResult == null) {
							matchResult = new SearchResult(this);
							searchResults.put(match, matchResult);
						}
						matchResult.put(url, snippet);
					}
				}
			}	
		}

		// Check for errors and repair as necessary
		//
		Dev.out.println("Found "+(total - errors.size())+" SERPs with data ("+errors.size()+" errors)...");
		if (errors.size() > 0 && level < maxRetries && cacheOption != Cache.IMPORT) {
			level++;
			String msg = "Searching "+this.toString()+" for "+errors.size()+" ngrams to repair errors...";
			if (Dev.confirm(console, msg)) {
				Map<String, String> newSerps = goSearch(errors);
				Map<String, SearchResult> newResults = parseResults(newSerps, errors, sourceText, level);
				if (newResults.size() > 0) {
					serps.putAll(newSerps);
					searchResults.putAll(newResults);
				
					if (level == 1 && cacheOption != null && Dev.confirm(console, "Updating cache file with new results..."))
						Dev.exportCache(serps, cachePath);
				}
			}
		}
		
		return searchResults;
	}		
	
	// Search a snippet of text to see if it has any ngrams from our sourcetext
	//
	public static Set<String> searchSnippet (String snippet, SourceText sourceText) {
		// use our sourceText parameters as a basis to create snippet ngrams
		Set<String> snippetNGrams = SourceText.generateNGrams(snippet, sourceText);	
		Set<String> matches = new HashSet<>();
		
		for (String ngram : snippetNGrams) {	
			if (sourceText.contains(ngram))			// check if this snippet ngram is in the document
				matches.add(ngram);					// if it is, add it to the matches
		}	
		return matches;								// return all matches
	}
}