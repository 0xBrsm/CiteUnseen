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

import java.io.*;
import java.util.*;
import java.util.concurrent.*; 

import com.google.gson.*;
import org.apache.lucene.analysis.tokenattributes.*;

enum Cache {IMPORT, EXPORT};

public class Main {
	public static boolean dumpNGrams = false;		// default not to save a file with all ngrams
	
	@SuppressWarnings("unchecked")		
	public static void main (String args[]) throws Exception {
	// Temp main to simulate web server submission call
	//
	
		// instance variables
		Cache cache = null;
		int maxRetries = 4;
		int maxConnections = 100;
		boolean snippetSearch = true;
		
		// -- Set defaults for ease of runtime
		int n = 3;							// default number of tokens		
		int urlThreshold = 1;				// default minimum number of ngrams matched
		int wordThreshold = 5;				// default minimum number of words in a row to count for scoring
		int minScore = 8;					// default minimum document score to consider
		boolean scoreByRarity = true;		// default, use the total results of an ngram to determine its worth
		Engine engine = Engine.Google;		// default search engine
		boolean alternateCache = false;		// default, if true we are running in the cloud, so load cache from alternate location
		// --
	
		// Figure out what to do with input
		//
		String fileName = "";
		for (int i = 0; i < args.length; i++)
			switch (args[i].charAt(0)) {
				case '-':
					switch (args[i].charAt(1)) {
						case 'c': alternateCache = true; break;
						case 'i': cache = Cache.IMPORT; break;
						case 'x': cache = Cache.EXPORT; break;
						case 'n': n = Integer.parseInt(args[i].substring(3)); break;
						case 'u': urlThreshold = Integer.parseInt(args[i].substring(3)); break;
						case 'w': wordThreshold = Integer.parseInt(args[i].substring(3)); break;						
						case 's': minScore = Integer.parseInt(args[i].substring(3)); break;
						case 'v': scoreByRarity = false; break;
						case 'd': dumpNGrams = true; break;
						case 'm': maxConnections = Integer.parseInt(args[i].substring(3)); break;
						case 'r': maxRetries = Integer.parseInt(args[i].substring(3)); break;
						case 'p': snippetSearch = true; break;
						case 'o': snippetSearch = false; break;
						case 'e':
							String e = args[i].substring(3);
							if (e.equals("Bing")) {
								engine = Engine.Bing;
								break;
							} else if (e.equals("Google")) {
								engine = Engine.Google;
								break;
							}
							System.out.println("Illegal search engine specified: "+e);
							return;
						default:
							System.out.println("Illegal argument: "+args[i]);
							return;
					}
					break;
				default: 
					if (args.length-1 > i) {
						System.out.println("Illegal argument: "+args[i+1]);
						return;
					}
					fileName = args[i];
			}
			
		String baseDir = "."+File.separator+"Documents"+File.separator;
		String fileRoot = fileName.substring(0, fileName.lastIndexOf("."));
		String fileRootPath = baseDir+fileRoot+File.separator+fileRoot;
		String filePath = baseDir+fileRoot+File.separator+fileName;

		// Start timer, debug
		long startTime = System.nanoTime();			
	
		// Initialize base objects, this code will be the same
		File document = new File(filePath);
		SourceText sourceText = new SourceText(document, n);
		WebSearch searcher = new WebSearch(engine);
		Processor processor = new Processor(urlThreshold, wordThreshold, minScore, scoreByRarity);

		// Set websearch properties
		searcher.setMaxRetries(maxRetries);
		searcher.setMaxConnections(maxConnections);
		
		// Get search results, either from cache or from the web
		Map<String, String> serps = null;
		if (cache == Cache.IMPORT) serps = (Map<String, String>) searcher.importCache(fileRootPath+"."+n+"."+engine+".dat");
		if (serps == null) {
			// Make sure the user intends to search
			System.out.println("Searching "+engine+" for "+sourceText.getNGrams().size()+" ngrams...");
			System.out.print("Continue? (y/n) ");

			int key = System.in.read();
			if (key != 121) System.exit(0);		
			
			serps = searcher.search(sourceText);
			if (cache == Cache.EXPORT) searcher.exportCache(serps, fileRootPath+"."+n+"."+engine+".dat");
		}				
		
		// Get a list of url results
		Collection<URLResult> urlResults = searcher.getURLResults(sourceText, serps, snippetSearch);
		
		Map<Sequence, Sequence> subSequences = processor.findBestSubsequences(sourceText, urlResults);
		
		PrintWriter writer = new PrintWriter(fileRootPath+".csv", "UTF-8");	
		for (Sequence sequence : subSequences.keySet()) {
			int x = sequence.getPositions().size();
			int y = (int)sequence.getScore();
			writer.println(y+","+x+",\""+sequence.getPositions()+"\",\""+subSequences.get(sequence)+"\"");
		}
		writer.close();
		
		Map<OffsetAttribute, Sequence> bestMatches = processor.convertToOffsets(sourceText, subSequences);

		System.out.println(bestMatches.keySet().size());
/*		
		for (Map.Entry<OffsetAttribute, URLResult> entry : bestMatches.entrySet()) {
			OffsetAttribute offset = entry.getKey();
			URLResult urlMatch = entry.getValue();
			int i = sourceText.getOffsets().indexOf(offset);
			NGram nGram = sourceText.getNGramsByOrder().get(i);
			
			if (i > 305 && i < 310)
				System.out.println(i+" "+nGram+" "+urlMatch);
		}
		
		NGram test = sourceText.getNGrams().get("dark man did");
		System.out.println(test.getResults());

/*		// Score all the URLs
		processor.scoreResults(sourceText, urlResults);
		
		// Get our best matches per ngram
		Map<OffsetAttribute, URLResult> bestMatches = processor.findBestMatches(sourceText);
		
		// Get the overall stats on the matches
		URLResult urlGroupResult = processor.getSingleResult(sourceText, bestMatches.values());
		
		// Mark all our matches and output to HTML
		String markedText = processor.markBestMatches(sourceText, bestMatches);
		
		// Build our HTML page
		String html = Builder.buildHTML(urlGroupResult, markedText);

		processor.output(urlResults, fileRootPath);		
*/
		// dump all ngrams to a file if required, debug
		if (dumpNGrams) sourceText.dumpNGrams(fileRootPath);

		// End our timer and output time to run, debug
		//
		long endTime = System.nanoTime();
		long duration = endTime - startTime;
		System.out.print("Total run time: "+TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS)/1000.0+"s ");
		System.out.println();
	}
}