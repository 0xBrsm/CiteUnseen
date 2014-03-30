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
	private final String NAME;
	
	// The HTML attribution label for this engine
	private static final String LABEL = "";	

	// The total size of the searchable index.
	//
	private final long INDEX_SIZE;
	
	// Offline index for searching
	//
	private final Map<String, HashSet<File>> index;
	
	// Static helper methods
	//
	private static String getName (String path) {
		String[] tokens = path.split("\\"+File.separator);
		return tokens[tokens.length-1];
	}
	private static long getIndexSize (String path) {
		return (new File(path, "Sources")).listFiles().length;
	}	
	
	// Constructors
	//
	public OfflineSearch (String corpus) throws Exception {
		this(corpus, 0);
	}	
	public OfflineSearch (String corpus, int n) throws Exception {
		super(getName(corpus), LABEL, getIndexSize(corpus));
		NAME = getName(corpus);
		INDEX_SIZE = getIndexSize(corpus);
		
		String indexPath = n > 0 ? corpus+"index."+n+".dat" : corpus+"index.dat";
		index = loadIndex(indexPath);
	}
	
	// Indexing and searching
	//
	@SuppressWarnings("unchecked")
	private Map<String, HashSet<File>> loadIndex (String indexPath) throws Exception {
		Map<String, HashSet<File>> index = (Map<String, HashSet<File>>)Dev.importCache(indexPath);
		if (index == null)
			throw new Exception("Could not load index file from: "+indexPath);
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