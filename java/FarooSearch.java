/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.util.*;

// For JSON parsing
import com.google.gson.*;

public class FarooSearch extends SearchEngine {
	// The common name for this search engine
	private static final String NAME = "FAROO";
	
	// The HTML attribution label for this engine
	private static final String LABEL = 
		"<a href='http://www.faroo.com' target='_top' title='FAROO Web Search'>"
		+ "<img src= 'http://www.faroo.com/hp/api/faroo_attribution.png'"
		+ "alt='FAROO Web Search // Full privacy // No spam.' border='0' /></a>";

	// The total size of the searchable index.
	// For consistency, this is found by searching for "a"
	// and using the number of returned results.
	//
	private static final long INDEX_SIZE = 200000000L;
	
	// Hold the engine-specific search result elements
	// and parser used to extract information
	//
	private final Map<String, String> elements;
	private final SerpParser parser;
	
	// Constructors
	public FarooSearch () {
		super(NAME, LABEL, INDEX_SIZE);

		setMaxConnectionsPerSecond(1);		
		elements = setElements();
		parser = new JsonSerpParser(elements);
	}
	
	// Set defaults for chosen results format
	//
	private  Map<String, String> setElements () {
		Map<String, String> elements = new HashMap<>();
		
		elements.put("resultsContainer", 		"results");
		elements.put("urlElement", 				"url");
		elements.put("snippetElement", 			"kwic");
		elements.put("totalResultsElement", 	"count");
		
		return elements;
	}
	
	@Override
	public SerpParser getSerpParser () {
		return parser;
	}

	//
	// Implementation of abstract methods
	//

	// Return an http getter with properly formatted query URL
	@Override
 	protected Map<String, String> buildQuery (String query, int id) {	
		String accountKey = "REDACTED_FAROO_API_KEY";
				
		String headerName = "Accept";
		String headerValue = "application/json";
		String encodedQuery = "%22"+query.replaceAll(" ","%20")+"%22";
		String searchURL = "http://www.faroo.com/api?src=web&f=json&key="+accountKey+"&q="+encodedQuery;		

		// Map of size 3 that will never grow
		Map<String, String> queryData = new HashMap<>(3, 1.34f);
		
		queryData.put("headerName", headerName);
		queryData.put("headerValue", headerValue);
		queryData.put("searchURL", searchURL);
		
		return queryData;
	}
 }