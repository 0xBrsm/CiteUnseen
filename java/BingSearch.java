/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.util.*;

// For JSON parsing
import com.google.gson.*;
import org.apache.commons.codec.binary.Base64;

public class BingSearch extends SearchEngine {
	// The common name for this search engine
	private static final String NAME = "Bing";
	
	// The HTML attribution label for this engine
	private static final String LABEL = "";

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
	
	// Constructor	
	public BingSearch () {
		super(NAME, LABEL, INDEX_SIZE);

//		setMaxConnectionsPerSecond(5);
		elements = setElements();
		parser = new JsonSerpParser(elements);
	}
	
	// Set defaults for chosen results format
	//
	private  Map<String, String> setElements () {
		Map<String, String> elements = new HashMap<>();
		
		elements.put("parentContainer",			"{\"d\":{\"results\":[ ]}}");
		elements.put("resultsContainer", 		"Web");
		elements.put("urlElement", 				"Url");
		elements.put("snippetElement", 			"Description");
		elements.put("totalResultsElement", 	"WebTotal");
		
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
		String accountKey = BingAccountInfo.getKey(id);
		byte[] accountKeyBytes = Base64.encodeBase64(accountKey.getBytes());
		String accountKeyEnc = new String(accountKeyBytes);
				
		String headerName = "Authorization";
		String headerValue = "Basic " + accountKeyEnc;
		String encodedQuery = "%27%22"+query.replaceAll(" ","%20")+"%22%27";
		String searchURL = "https://api.datamarket.azure.com/Bing/Search/v1/Composite?Sources=%27web%27&$format=json&Query="+encodedQuery;	

		// Map of size 3 that will never grow
		Map<String, String> queryData = new HashMap<>(3, 1.34f);
		
		queryData.put("headerName", headerName);
		queryData.put("headerValue", headerValue);
		queryData.put("searchURL", searchURL);
		
		return queryData;
	}
 }