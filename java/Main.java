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
		Cache cache = Cache.USE;			// default, use cache if present - if not, search and save cache
		boolean dumpNGrams = false;			// default not to save a file with all ngrams
		boolean ignoreCitations = false;	// default search on citations
		// --

		// Start timer
		Timer timer = Timer.startNew("Main").print();		
		
		// Initialize base objects	
		Processor processor = new Processor();
		SearchEngine engine = new GoogleSearch();
		
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
						case 's': processor.setMinimumScore(Integer.parseInt(args[i].substring(3))); break;
						case 'v': processor.setScoreByRarity(false); break;
						case 'g': processor.setScoreGapsByRarity(true); break;						
						case 'm': processor.setScoringMethod(args[i].substring(3)); break;								
						case 'o': engine.setSnippetSearch(false); break;						
						case 'c': engine.setMaxConnections(Integer.parseInt(args[i].substring(3))); break;
						case 'r': engine.setMaxRetries(Integer.parseInt(args[i].substring(3))); break;
						case 'e':
							String e = args[i].substring(3);
							switch (e.toLowerCase()) {
								case "bing"		:	engine = new BingSearch();break;
								case "bingweb"	:	engine = new BingWebSearch();break;
								case "faroo"	:	engine = new FarooSearch();break;
								case "google"	:	engine = new GoogleSearch();break;
								case "yahoo"	:	engine = new YahooSearch();break;
								default			:
									Dev.out.println("Illegal search engine specified: "+e);
									return;
							}
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

		File submission = new File(fileName);					// create a new file object for the submitted file
		String cachePath = Dev.getCachePath(fileName);			// set the path for all output regarding this submission
		String cacheRoot = Dev.getCacheRoot(submission);		// set the root file name and path for objects to be cached for this file
		File localCopy = new File(cachePath, fileName);			// create a file object for the local cache of this file
		
		if (submission.exists())								// this is a newly submitted file
				Dev.copyToCache(submission);						// make a cached copy of it
		else 	submission = localCopy;							// or it's a reference to a previously cached file
	
		// Create our source text
		SourceText sourceText = new SourceText(submission, n, ignoreCitations);
		
		// dump all ngrams to a file if requested
		if (dumpNGrams) sourceText.dumpNGrams(cacheRoot);

		// Set search parameters
		engine.setCache(cache, Dev.datPath(submission, n, engine));
		engine.setConsole(true);
			
		// Get search results
		Map<String, SearchResult> searchResults = engine.search(sourceText);
		
		// Use our search results to find all word sequences of any interest
		Set<SourceFragment> sequences = processor.process(searchResults, sourceText);
		
		// Mark our sequences by their matching URLs in HTML format
		Map<String, String> results = PageBuilder.getHTMLResults(sequences, sourceText);
		
		String summary = results.get("summary");
		String markedText = results.get("markedText");

		// Output results to HTML page
		PageBuilder.outputHTMLPage(summary, markedText, cacheRoot);

		// End our timer and output time to run, debug
		//
		timer.stop().print("s");
	}
}