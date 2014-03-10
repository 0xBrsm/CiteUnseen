/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 *	ALM in IT Thesis
 *	Overview
 *	<date>	  
 *
 *	Define the overview of the program process
 *
 */

package citeunseen;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashSet;

import org.apache.lucene.analysis.tokenattributes.*;

enum Cache {IMPORT, USE, EXPORT};

public class Main {

	@SuppressWarnings("unchecked")		
	public static void main (String[] args) throws Exception {	
		// -- Set defaults for ease of runtime
		int n = 3;							// default number of tokens		
		int urlThreshold = 1;				// default minimum number of ngrams matched
		int minScore = 8;					// default minimum document score to consider
		Cache cache = Cache.USE;			// default, use cache if present - if not, search and save cache
		boolean dumpNGrams = false;			// default not to save a file with all ngrams
		boolean ignoreCitations = false;	// default search on citations
		// --

		// Start timer
		Timer timer = new Timer("Main");		
		
		// Initialize base objects	
		Processor processor = new Processor();
		SearchEngine engine = new GoogleSearch();
		
		// Set processor parameters
		processor.setMinimumScore(minScore);
		processor.setURLThreshold(urlThreshold);
		
		// Figure out what to do with input
		//
		String fileName = "";
		for (int i = 0; i < args.length; i++)
			switch (args[i].charAt(0)) {
				case '-':
					switch (args[i].charAt(1)) {
						case 'q': ignoreCitations = true; break;
						case 'd': dumpNGrams = true; break;					
						case 'i': cache = Cache.IMPORT; break;
						case 'x': cache = Cache.EXPORT; break;
						case 'n': n = Integer.parseInt(args[i].substring(3)); break;
						case 'u': processor.setURLThreshold(Integer.parseInt(args[i].substring(3))); break;					
						case 's': processor.setMinimumScore(Integer.parseInt(args[i].substring(3))); break;
						case 'v': processor.setScoreByRarity(false); break;
						case 'm': processor.setScoringMethod(args[i].substring(3)); break;								
						case 'o': engine.setSnippetSearch(false); break;						
						case 'c': engine.setMaxConnections(Integer.parseInt(args[i].substring(3))); break;
						case 'r': engine.setMaxRetries(Integer.parseInt(args[i].substring(3))); break;
						case 'e':
							String e = args[i].substring(3);
							if (e.equalsIgnoreCase("Bing")) {
								engine = new BingSearch();
								break;
							} else if (e.equalsIgnoreCase("BingWeb")) {
								engine = new BingWebSearch();
								break;								
							} else if (e.equalsIgnoreCase("FAROO")) {
								engine = new FarooSearch();
								break;								
							} else if (e.equalsIgnoreCase("Google")) {
								engine = new GoogleSearch();
								break;
							} else if (e.equalsIgnoreCase("Offline")) {
								engine = new OfflineSearch();
								break;							
							} else if (e.equalsIgnoreCase("Yahoo")) {
								engine = new YahooSearch();
								break;
							}
							Dev.out.println("Illegal search engine specified: "+e);
							return;
						default:
							Dev.out.println("Illegal argument: "+args[i]);
							return;
					}
					break;
				default: 
					if (args.length-1 > i) {
						Dev.out.println("Illegal argument: "+args[i+1]);
						return;
					}
					fileName = args[i];
			}
			
		String baseDir = "."+File.separator+"Documents"+File.separator;
		String fileRoot = fileName.substring(0, fileName.lastIndexOf("."));
		String fileRootPath = baseDir+fileRoot+File.separator+fileRoot;
		String filePath = baseDir+fileRoot+File.separator+fileName;
	
		// Create our source text
		File document = new File(filePath);
		SourceText sourceText = new SourceText(document, n, ignoreCitations);
		
		
		
		// dump all ngrams to a file if requested
		if (dumpNGrams) sourceText.dumpNGrams(fileRootPath);

		// Set search parameters
		engine.setCache(cache, fileRootPath+"."+n+"."+engine+".dat");
		engine.setConsole(true);
			
		// Get search results
		Map<String, SearchResult> searchResults = engine.search(sourceText);
		
		// Use our search results to find all word sequences of any interest
		Set<Sequence> sequences = processor.process(searchResults, sourceText);
		
		// Mark our sequences by their matching URLs in HTML format
		Map<String, String> results = Builder.getHTMLResults(sequences, sourceText);

//		Sequence[] array = sequences.toArray(new Sequence[0]);
//		URLResult result = array[1].getBestResult();
//		Map<String, String> results = Builder.getHTMLResults(result, sourceText);	
//		Dev.out.println(result.getAsText());
		
		String summary = results.get("summary");
		String markedText = results.get("markedText");

		// Output results to HTML page
		Builder.outputHTMLPage(summary, markedText, fileRootPath);

		// End our timer and output time to run, debug
		//
		timer.stop();
	}
}