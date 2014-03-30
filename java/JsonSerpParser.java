/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.util.*;

import com.google.gson.*;
import org.apache.commons.lang3.StringUtils;

public class JsonSerpParser extends SerpParser {
	private final String errorContainer;
	private final String errorMessage;
	
	private final String parentContainers;
	private final String resultsArray;
	private final String informationContainer;
	private final String urlElement;
	private final String snippetElement;
	private final String totalResultsElement;
	
	private final JsonParser parser;
	
	// Constructor
	// Get all unique elements for the requesting engine
	// Create our base parser for use in all parsing
	//
	public JsonSerpParser (Map<String, String> elements) {		
		errorContainer = elements.get("errorContainer");
		errorMessage = elements.get("errorMessage");
		
		parentContainers = elements.get("parentContainers");
		resultsArray = elements.get("resultsArray");
		informationContainer = elements.get("informationContainer");
		urlElement = elements.get("urlElement");
		snippetElement = elements.get("snippetElement");
		totalResultsElement = elements.get("totalResultsElement");
		
		parser = new JsonParser();
	}
	
	// Retrieve the JsonObject we care about
	// Many JSON responses have wrappers for additional info which isn't relevant
	// to our needs, so this needs to be stripped off
	//
	private JsonObject getJsonRoot (String serp) {
		// Parse our serp into a JSON object
		// All JSON starts with a base object
		JsonElement json = parser.parse(serp).getAsJsonObject();
		
		// If we have parent wrapping JsonElements, remove them
		// Sometimes, errors don't have the parent elements,
		// so be sure to check for them
		//
		if (parentContainers != null) {		
			JsonElement child = null;
			String[] parents = parentContainers.split(" ");
			for (String parent : parents) {
				if (json.isJsonObject())
					child = json.getAsJsonObject().get(parent);
				else if (json.isJsonArray()) {
					int i = Integer.parseInt(StringUtils.substringBetween(parent, "[", "]"));
					child = json.getAsJsonArray().get(i);
				}
				if (child != null)
					json = child;
			}
		}
		return json.getAsJsonObject();
	}

	// Implementation for JSON parsing of error responses
	@Override
	public String parseError (String serp) {
		JsonObject json = getJsonRoot(serp);			
		JsonObject container = json.getAsJsonObject(errorContainer);
		String error = container.getAsJsonPrimitive(errorMessage).getAsString();
		
		return error;
	}
	
	// Implementation for JSON parsing of results
	@Override
	public SearchResult parse (String serp) {
		long totalResults = 0;

		// Get the root of the JSON object we're interested in
		JsonObject json = getJsonRoot(serp);
				
		// Get our results array
		JsonArray results = json.getAsJsonArray(resultsArray);

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