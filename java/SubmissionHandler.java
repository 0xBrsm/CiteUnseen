/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 *	ALM in IT Thesis
 *	<date>	  
 *
 *	Servlet to handle submissions and output results
 *
 */

package citeunseen;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FilenameUtils;

import org.apache.lucene.analysis.tokenattributes.*;

public class SubmissionHandler extends HttpServlet {
  
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// space in log for readability
		Dev.out.println();
	
		// Start timer, debug
		Timer timer = Timer.startNew("SubmissionHandler");
	
		//process only multipart content
		if (ServletFileUpload.isMultipartContent(request)) {
			try {
				File tmp = new File(".");				
				List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory(104857600, tmp)).parseRequest(request);
				Map<String, String> options = new HashMap<>();			
				FileItem file = null;
				String path = "";
				
				for (FileItem item : items) {
					if (item.isFormField()) {
						String fieldName = item.getFieldName();
						String fieldValue = item.getString();
						options.put(fieldName, fieldValue);
					} else {

						file = item;
					}
				}
				if (file == null) throw new Exception("File upload missing.");
				
				Map<String, String> results = processSubmission(file, options);
				request.setAttribute("results", results.get("summary"));
				request.setAttribute("highlights", results.get("markedText"));
				
			} catch (Exception e) {
				request.setAttribute("results", "File processing failed: " + e.getMessage());
				e.printStackTrace();
			}
		} else request.setAttribute("results", "Not a file or file corrupt.");
		
		request.getRequestDispatcher("/results.jsp").forward(request, response);

		// End our timer and output time to run, debug
		//
		timer.stop().print("s");
	
    }

	@SuppressWarnings("unchecked")	
	public Map<String, String> processSubmission (FileItem fileItem, Map<String, String> options) throws Exception {
		// -- Static defaults - these cannot be changed by the user
		Cache useCache = Cache.USE;			// default, use cache if present - if not, search and save cache			
		String corporaPath = "corpora"+File.separator;
		
		// Initialize base objects	
		Processor processor = new Processor();
		SearchEngine engine = null;
		
		// Get our user options and update our default values, if necessary
		//
		int n = 0;							// number of tokens	specified by user
		boolean ignoreCitations = false;	// ignore citations in matching
		boolean snippetSearch = true;		// search snippets
		
		for (Map.Entry<String, String> entry : options.entrySet()) {
			String fieldName = entry.getKey();
			String fieldValue = entry.getValue();
			
			switch (fieldName) {	
				case "minScore" :
					processor.setMinimumScore(Integer.parseInt(fieldValue));
					break;
				case "weightFactor" :
					processor.setWeightFactor(Double.parseDouble(fieldValue));
					break;					
				case "scoreByRarity" :
					processor.setScoreByRarity(Boolean.parseBoolean(fieldValue));
					break;
				case "scoreGapsByRarity" :
					processor.setScoreGapsByRarity(Boolean.parseBoolean(fieldValue));
					break;					
				case "scoringMethod" :
					processor.setScoringMethod(fieldValue);
					break;
				case "disableScoring" :
					if (Boolean.parseBoolean(fieldValue))
						processor.disable();
					break;						
				case "ignoreCitations" :
					ignoreCitations = Boolean.parseBoolean(fieldValue);
					break;					
				case "snippetSearch" :
					snippetSearch = Boolean.parseBoolean(fieldValue);
					break;
				case "n" :
					n = Integer.parseInt(fieldValue);
					break;					
				case "searchEngine" :
					switch (fieldValue) {
						case "bing"			:	engine = new BingSearch();break;
						case "bingweb"		:	engine = new BingWebSearch();break;
						case "faroo"		:	engine = new FarooSearch();break;
						case "google"		:	engine = new GoogleSearch();break;
						case "yahoo"		:	engine = new YahooSearch();break;
						case "COPSA"		:	engine = new OfflineSearch(corporaPath+fieldValue+File.separator);break;
						case "Webis-CPC-11"	:	engine = new OfflineSearch(corporaPath+fieldValue+File.separator, n);break;
					}
			}
		}
		// Extra nonsense courtesy of IE
		String fileName = FilenameUtils.getName(fileItem.getName());
		
		// Save a local copy of this file and set up the cache folder
		Dev.copyToCache(fileItem);		
		
		// Configure search engine options
		engine.setCache(useCache, Dev.datPath(fileName, n, engine));			
		engine.setSnippetSearch(snippetSearch);		
		
		// Create our source text
		SourceText sourceText = new SourceText(fileItem.getInputStream(), n, ignoreCitations);		

		// Setup complete...
		Dev.out.println("Processing "+fileName+"...");

		// Get search results
		Map<String, SearchResult> searchResults = engine.search(sourceText);
		
		// Use our search results to find all word sequences of any interest
		Set<SourceFragment> sequences = processor.process(searchResults, sourceText);

		// Mark our sequences by their matching URLs in HTML format
		Map<String, String> results = PageBuilder.getHTMLResults(sequences, sourceText);			
	
		Dev.out.println("Results output to client.");
		
		return results;
	}
}