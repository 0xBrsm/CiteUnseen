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
		Timer timer = new Timer("SubmissionHandler");
	
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
		timer.stop();
	
    }

	@SuppressWarnings("unchecked")	
	public Map<String, String> processSubmission (FileItem fileItem, Map<String, String> options) throws Exception {
		// -- Static defaults - these cannot be changed by the user
		String cacheDir = "."+File.separator+"Documents"+File.separator;
		Cache useCache = Cache.USE;			// default, use cache if present - if not, search and save cache		
		boolean saveLocalCopy = true;		// default, save a local copy of the document			

		// File/data locator paths
		String baseDir = "."+File.separator+"Documents"+File.separator;
		String fileName = FilenameUtils.getName(fileItem.getName()); 		// Extra nonsense courtesy of IE
		String fileRoot = fileName.substring(0, fileName.lastIndexOf("."));
		String fileRootPath = baseDir+fileRoot+File.separator+fileRoot;
		String filePath = baseDir+fileRoot+File.separator+fileName;

		// Save a local copy
		if (saveLocalCopy) {
			File document = new File(filePath);
			if (!document.exists()) {
				document.getParentFile().mkdirs();
				fileItem.write(document);
				Dev.out.println("Local copy of document saved to "+document.getParentFile());
			}
		}

		// Initialize base objects	
		Processor processor = new Processor();
		SearchEngine engine = new GoogleSearch();
		
		// Get our user options and update our default values, if necessary
		//
		int n = 0;							// number of tokens	specified by user
		boolean ignoreCitations = false;	// ignore citations in matching
		
		for (Map.Entry<String, String> entry : options.entrySet()) {
			String fieldName = entry.getKey();
			String fieldValue = entry.getValue();
			
			switch (fieldName) {
				case "urlThreshold" :
					processor.setURLThreshold(Integer.parseInt(fieldValue));
					break;			
				case "minScore" :
					processor.setMinimumScore(Integer.parseInt(fieldValue));
					break;
				case "n" :
					n = Integer.parseInt(fieldValue);
					break;
				case "scoreByRarity" :
					processor.setScoreByRarity(Boolean.parseBoolean(fieldValue));
					break;
				case "scoringMethod" :
					processor.setScoringMethod(fieldValue);
					break;					
				case "snippetSearch" :
					engine.setSnippetSearch(Boolean.parseBoolean(fieldValue));
					break;
				case "ignoreCitations" :
					ignoreCitations = Boolean.parseBoolean(fieldValue);
					break;					
			}
		}
		
		// Create our source text
		SourceText sourceText = new SourceText(fileItem.getInputStream(), n, ignoreCitations);
		
		// Set our hard coded, non-standard defaults
		engine.setCache(useCache, fileRootPath+"."+n+"."+engine+".dat");			

		// Setup complete...
		Dev.out.println("Processing "+fileName+"...");

		// Get search results
		Map<String, SearchResult> searchResults = engine.search(sourceText);
		
		// Use our search results to find all word sequences of any interest
		Set<Sequence> sequences = processor.process(searchResults, sourceText);
		
		// Mark our sequences by their matching URLs in HTML format
		Map<String, String> results = Builder.getHTMLResults(sequences, sourceText);			
	
		Dev.out.println("Results output to client.");
		
		return results;
	}
}