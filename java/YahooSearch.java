/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.util.*;

// For JSON parsing
import com.google.gson.*;

// For OAuth signing
import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;


public class YahooSearch extends SearchEngine {
	// The common name for this search engine
	private static final String NAME = "Yahoo";
	
	// The HTML attribution label for this engine
	private static final String LABEL =
		"<a href='http://developer.yahoo.com/search/boss/' target='_top'>"
		+ "<img src= 'http://l.yimg.com/rv/boss_v2/images/ysb_v1a.png'"
		+ "alt='Created with Yahoo! BOSS' border='0' /></a>";

	// The total size of the searchable index.
	// For consistency, this is found by searching for "a"
	// and using the number of returned results.
	//
	private static final long INDEX_SIZE = 4300000000L;
	
	// Hold the engine-specific search result elements
	// and parser used to extract information
	//
	private final Map<String, String> elements;
	private final SerpParser parser;
	
	// Constructors
	public YahooSearch () {
		super(NAME, LABEL, INDEX_SIZE);

		elements = setElements();
		parser = new JsonSerpParser(elements);
	}
	
	// Set defaults for chosen results format
	//
	private  Map<String, String> setElements () {
		Map<String, String> elements = new HashMap<>();
		
		elements.put("parentContainer",			"{\"bossresponse\":{\"responsecode\":\"200\",\"web\": }}");
		elements.put("resultsContainer", 		"results");
		elements.put("urlElement", 				"url");
		elements.put("snippetElement", 			"abstract");
		elements.put("totalResultsElement", 	"totalresults");
		
		return elements;
	}
	
	public SerpParser getSerpParser () {
		return parser;
	}

	//
	// Implementation of abstract methods
	//

	// Return an http getter with properly formatted query URL
	@Override
 	protected Map<String, String> buildQuery (String query, int id) {	
		String consumerKey = "REDACTED_YAHOO_CONSUMER_KEY";
		String consumerSecret = "REDACTED_YAHOO_CONSUMER_SECRET";
		
		String headerName = "Accept";
		String headerValue = "application/json";
		String encodedQuery = "%22"+query.replaceAll(" ","%2B")+"%22";
		String searchURL = "http://ysp.yahooapis.com/ysp/web?format=json&abstract=long&style=raw&q="+encodedQuery;
		
		OAuthConsumer consumer = new CommonsHttpOAuthConsumer(consumerKey, consumerSecret);
		try {
			searchURL = consumer.sign(searchURL);
		} catch (Exception e) { e.getMessage();}	

		// Map of size 3 that will never grow
		Map<String, String> queryData = new HashMap<>(3, 1.34f);
		
		queryData.put("headerName", headerName);
		queryData.put("headerValue", headerValue);
		queryData.put("searchURL", searchURL);
		
		return queryData;
	}
 }