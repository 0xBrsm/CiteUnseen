
/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.util.*;
 
public class SearchResult {
	private final SearchEngine engine;
	private Map<String, String> urlMap = new HashMap<>();
	private long totalResults;
	
	public SearchResult () {
		this(null);
	}
	
	public SearchResult (SearchEngine engine) {
		this.engine = engine;
	}
	
	// Setters	
	public boolean put (String url, String snippet) {
		String current = urlMap.put(url, snippet);
		if (current == null) {
			int resultsSize = urlMap.size();		
			if (totalResults < resultsSize)
				totalResults = resultsSize;
			return true;
		} 
//		else if (!snippet.equals(current)) Dev.out.println(Dev.asCSV(url, current, snippet));
		
		return false;		
	}
	
	public void setTotalResults (long results) {
		if (results > urlMap.size())
			totalResults = results;
	}

	// Getters
	public Map<String,String> urlMap () {
		return urlMap;
	}
	
	public Set<String> urls () {
		return urlMap.keySet();
	}
	
	public long totalResults () {
		return totalResults;
	}
	
	public String toString () {
		return totalResults+" "+urls();
	}
	
	public String getSnippet (String url) {
		return urlMap.get(url);
	}

	// Merge two search results, skipping snippet text
	//
	public void merge (SearchResult mergedResult) {
		urlMap.putAll(mergedResult.urlMap());
		if (totalResults < mergedResult.totalResults())
			totalResults = mergedResult.totalResults();
		if (totalResults < urlMap.size())
			totalResults = urlMap.size();
	}
	
	// Various methods for getting the relative value
	// of this search result
	//
	public double getRelativeValue(String method) {
		switch (method) {
			case "idf" : return getIDF();
			case "log" : return getLog();
			case "pow" : return getPower();
		}
		return 1 - ((double)totalResults / engine.indexSize());
	}
	
	private double getIDF () {
		double fraction = engine.indexSize() / (double)totalResults;
		return logOfBase(engine.indexSize(), fraction);
	}
	
	private double getLog () {
		if (totalResults == 1) 	return Math.log10(engine.indexSize()) / 2;
		else					return (logOfBase(totalResults, engine.indexSize()) - 1) / 4;
	}
	
	private double getPower () {
		double fraction = 1 - ((double)totalResults / engine.indexSize());
		double power = Math.log(engine.indexSize());
		
		return Math.pow(fraction, power);
	}	
	
	private double logOfBase(double base, double num) {
		return Math.log(num) / Math.log(base);
	}
 }