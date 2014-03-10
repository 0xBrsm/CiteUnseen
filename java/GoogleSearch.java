/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.util.*;

// For JSON parsing
import com.google.gson.*;

public class GoogleSearch extends SearchEngine {
	// The common name for this search engine
	private static final String NAME = "Google";
	
	// The HTML attribution label for this engine
	private static final String LABEL = 
		"<img src= 'https://www.google.com/cse/images/google_custom_search_smnar.gif'/>";	

	// The total size of the searchable index.
	// For consistency, this is found by searching for "a"
	// and using the number of returned results.
	//
	private static final long INDEX_SIZE = 3700000000L;
	
	// Hold the engine-specific search result elements
	// and parser used to extract information
	//
	private final Map<String, String> elements;
	private final SerpParser parser;
	
	// Constructors
	public GoogleSearch () {
		super(NAME, LABEL, INDEX_SIZE);
	
		elements = setElements();
		parser = new JsonSerpParser(elements);
	}
	
	// Set defaults for chosen results format
	//
	private  Map<String, String> setElements () {
		Map<String, String> elements = new HashMap<>();
		
		elements.put("resultsContainer", 		"items");
		elements.put("urlElement", 				"link");
		elements.put("snippetElement", 			"snippet");
		elements.put("informationContainer", 	"searchInformation");		
		elements.put("totalResultsElement", 	"totalResults");
		
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
		String cx = "013476539332271337914:qdvq0snx-he";
		String key = "REDACTED_GOOGLE_API_KEY";

		String headerName = "Accept";
		String headerValue = "application/json";
		String encodedQuery = "%22"+query.replaceAll(" ","+")+"%22";
		String searchURL = "https://www.googleapis.com/customsearch/v1?alt=json&key="+key+"&cx="+cx+"&q="+encodedQuery;

		// Map of size 3 that will never grow
		Map<String, String> queryData = new HashMap<>(3, 1.34f);

		queryData.put("headerName", headerName);
		queryData.put("headerValue", headerValue);
		queryData.put("searchURL", searchURL);

		return queryData;
	}
 }