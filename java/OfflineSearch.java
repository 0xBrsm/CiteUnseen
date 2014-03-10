/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;

import java.io.*;
import java.util.*;


public class OfflineSearch extends SearchEngine {
	// The common name for this search engine
	private static final String NAME = "Offline";
	
	// The HTML attribution label for this engine
	private static final String LABEL = "";	

	// The total size of the searchable index.
	// For consistency, this is found by searching for "a"
	// and using the number of returned results.
	//
	private static final long INDEX_SIZE = 5;
	
	// Offline index for searching
	//
	private final Map<String, HashSet<File>> index;
	
	// Constructor
	//
	public OfflineSearch () throws Exception {
		this("COPSA"+File.separator+"index.dat");
	}
	
	public OfflineSearch (String path) throws Exception {
		super(NAME, LABEL, INDEX_SIZE);
		index = loadIndex(path);
	}

	@SuppressWarnings("unchecked")
	private Map<String, HashSet<File>> loadIndex (String path) throws Exception {
		Map<String, HashSet<File>> index = (Map<String, HashSet<File>>)Dev.importCache(path);
		if (index == null)
			throw new Exception("Could not load index file from: "+path);
		return index;
	}
	
	@Override
	public Map<String, SearchResult> search (SourceText sourceText) {
		Map<String, SearchResult> searchResults = new HashMap<>();		
		Set<String> ngrams = sourceText.getNGrams();
		for (String ngram : ngrams) {
			Set<File> files = index.get(ngram);
			if (files != null) {
				SearchResult searchResult = searchResults.get(ngram);
				if (searchResult == null) {
					searchResult = new SearchResult(this);
					searchResults.put(ngram, searchResult);
				}
				for (File file : files) {
					String url = file.getPath();
					searchResult.put(url, "");		// no snippet text
				}
			}
		}
		return searchResults;
	}
	
	//
	// Implementation of abstract methods
	//
	@Override	
	public SerpParser getSerpParser () {
		return null;
	}	

	// Return an http getter with properly formatted query URL
	@Override
 	protected Map<String, String> buildQuery (String query, int id) {	
		return null;
	}
 }