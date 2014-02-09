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

import java.io.*;
import java.util.*;
import java.util.concurrent.*; 
import org.apache.lucene.analysis.tokenattributes.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FilenameUtils;

public class SubmissionHandler extends HttpServlet {
  
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// Start timer, debug
		long startTime = System.nanoTime();	
	
		//process only if its multipart content
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
				if (file == null) throw new IOException("File upload missing.");
				
				String[] results = processSubmission(file, options);
				request.setAttribute("results", results[0]);
				request.setAttribute("highlights", results[1]);
				
			} catch (Exception e) {
				request.setAttribute("results", "File processing failed: " + e.getMessage());
				e.printStackTrace();
			}
		} else request.setAttribute("results", "Sorry this Servlet only handles file upload requests.");
		
		request.getRequestDispatcher("/results.jsp").forward(request, response);

		// End our timer and output time to run, debug
		//
		long duration = System.nanoTime() - startTime;
		System.out.println("Total run time: "+TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS)/1000.0+"s ");		
    }

	@SuppressWarnings("unchecked")	
	public String[] processSubmission (FileItem fileItem, Map<String, String> options) throws Exception {
		// -- Static defaults - these cannot be changed by the user
		String cacheDir = "."+File.separator+"Documents"+File.separator;
		
		boolean saveLocalCopy = true;		// default, save a local copy of the document
		boolean useCache = true;			// default, use cache if present - if not, search and save cache
		Engine engine = Engine.Google;		// default search engine
		
		// -- Initial defaults - these can be changed by the user
		int urlThreshold = 1;				// default minimum number of ngrams matched
		int wordThreshold = 5;				// default minimum number of words in a row to count for scoring
		int minScore = 8;					// default minimum document score to consider
		int n = 3;							// default number of tokens		
		boolean scoreByRarity = false;		// default, do not use the total results of an ngram to determine its worth
		boolean snippetSearch = false;		// default, do not search snippets for ngram matches		
		// --

		String baseDir = "."+File.separator+"Documents"+File.separator;
		String fileName = FilenameUtils.getName(fileItem.getName()); 		// Extra nonsense courtesy of IE
		String fileRoot = fileName.substring(0, fileName.lastIndexOf("."));
		String fileRootPath = baseDir+fileRoot+File.separator+fileRoot;
		String filePath = baseDir+fileRoot+File.separator+fileName;
		
		if (saveLocalCopy) {
			File document = new File(filePath);
			if (!document.exists()) {
				document.getParentFile().mkdirs();
				fileItem.write(document);
				System.out.println("Local copy of document saved to "+document.getParentFile());
			}
		}
		
		System.out.println();
		System.out.println("Processing "+fileName+"...");

		Processor processor = new Processor(engine.getIndexSize());
		
		// Get our user options and update our default values, if necessary
		//
		for (Map.Entry<String, String> entry : options.entrySet()) {
			String fieldName = entry.getKey();
			String fieldValue = entry.getValue();
			
			switch (fieldName) {
				case "urlThreshold" :
					processor.setURLThreshold(Integer.parseInt(fieldValue));
					break;
				case "wordThreshold" :
					processor.setWordThreshold(Integer.parseInt(fieldValue));
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
				case "snippetSearch" :
					snippetSearch = true;
					break;
			}
		}
		
		// Initialize base objects
		SourceText sourceText = new SourceText(fileItem.getInputStream(), n);
		WebSearch searcher = new WebSearch(engine);

		// Get search results
		//	Map<String, String> serps = searcher.search(sourceText);
		
		// Get search results, either from cache or from the web
		Map<String, String> serps = null;
		if (useCache) serps = (Map<String, String>) searcher.importCache(fileRootPath+"."+n+"."+engine+".dat");
		if (serps == null) {
			serps = searcher.search(sourceText);
			searcher.exportCache(serps, fileRootPath+"."+n+"."+engine+".dat");
		}
		
		// Get a list of url results
		Collection<URLResult> urlResults = searcher.getURLResults(sourceText, serps, snippetSearch);
		
		// Find the best URL for each subsequence in the document
		Map<Sequence, Sequence> subSequences = processor.findBestSubsequences(sourceText, urlResults);		
		Map<OffsetAttribute, Sequence> bestMatches = processor.convertToOffsets(sourceText, subSequences);		

/*		// Score all the URLs
		processor.scoreResults(sourceText, urlResults);
		
		// Get our best matches per ngram
		Map<OffsetAttribute, URLResult> bestMatches = processor.findBestURLMatches(sourceText);
*/		
		// Get the overall stats on the matches
		Collection<URLResult> urlGroupResults = new HashSet<>();
		for (Sequence sequence : bestMatches.values()) {
			urlGroupResults.addAll(sequence.getResults());
		}
		URLResult urlGroupResult = processor.getSingleResult(sourceText, urlGroupResults);
		String result = urlGroupResult.getPercent()+"% match with "+urlGroupResult.getResults().size()+" URLs above a score of "+minScore+"."; //bug
		
		// Mark all our matches, highlighted as URLs
		String markedText = processor.markBestMatches(sourceText, bestMatches);
		
		// Return our results summary and marked text
		String[] results = {result, markedText};
		System.out.println("Results output to client.");
		
		return results;
	}
}