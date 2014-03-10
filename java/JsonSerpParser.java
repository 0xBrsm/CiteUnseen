/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.util.*;

// For JSON parsing
import com.google.gson.*;

public class JsonSerpParser extends SerpParser {
	private final String parentContainer;
	private final String resultsContainer;
	private final String informationContainer;
	private final String urlElement;
	private final String snippetElement;
	private final String totalResultsElement;
	
	private final JsonParser parser;
	
	public JsonSerpParser (Map<String, String> elements) {
		parentContainer = elements.get("parentContainer");
		resultsContainer = elements.get("resultsContainer");
		informationContainer = elements.get("informationContainer");
		urlElement = elements.get("urlElement");
		snippetElement = elements.get("snippetElement");
		totalResultsElement = elements.get("totalResultsElement");
		
		parser = new JsonParser();
	}

	// Implementation for JSON parsing of results
	@Override
	public SearchResult parse (String serp) {
		long totalResults = 0;

		// If we have parent wrapping elements, remove them
		if (parentContainer != null) {
			String[] split = parentContainer.split(" ");
			int start = serp.indexOf(split[0]) + split[0].length();
			int end = serp.lastIndexOf(split[1]);
			serp = serp.substring(start, end);
		}	
		
		// Parse our serp into a JSON object
		JsonObject json = parser.parse(serp).getAsJsonObject();
		
		// Get our results array
		JsonArray results = json.getAsJsonArray(resultsContainer);

		// If the total results is contained in a separate informational section
		if (informationContainer != null)
			json = json.getAsJsonObject(informationContainer);
		
		// If this engine supports total results
		if (totalResultsElement != null)
			totalResults = json.getAsJsonPrimitive(totalResultsElement).getAsLong();
			
		SearchResult searchResult = new SearchResult();
		searchResult.setTotalResults(totalResults);

		// Cycle through all results, if there are any results
		//
		if (results != null) for (JsonElement e : results) {
			JsonObject result = e.getAsJsonObject();
			String url = result.getAsJsonPrimitive(urlElement).getAsString();
			String snippet = result.getAsJsonPrimitive(snippetElement).getAsString();				

			searchResult.put(url, snippet);		
		}
		return searchResult;
	}
}