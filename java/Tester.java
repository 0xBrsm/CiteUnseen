/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;

import java.io.*;
import java.util.*;
import java.nio.file.*;

import org.apache.commons.lang3.StringUtils;

public class Tester {
	private static SearchEngine engine;
	private static String rootPath = "corpora"+File.separator;
	private static String corpusPath;
	private static String sourcesPath;
	private static String suspectsPath;
	private static String indexPath;

	private static void setPaths (String corpus) {
		corpusPath = rootPath+corpus+File.separator;
		sourcesPath = corpusPath+"Sources"+File.separator;
		suspectsPath = corpusPath+"Suspects"+File.separator;
		indexPath = corpusPath+"index.dat";		
	}
	
	public static void main (String[] args) throws Exception {
			switch (args[1].toLowerCase()) {
				case "bing"		:	engine = new BingSearch();break;
				case "bingweb"	:	engine = new BingWebSearch();break;
				case "faroo"	:	engine = new FarooSearch();break;
				case "google"	:	engine = new GoogleSearch();break;
				case "yahoo"	:	engine = new YahooSearch();break;
				default			:	setPaths(args[1]);
									engine = new OfflineSearch(indexPath);
			}
			
			switch (args[0]) {
				case "-i": index(); break;			
				case "-q": query(); break;
				case "-t": testSuspectFiles(); break;
			}
	}

	//
	// Search index for query
	//
	private static void query () throws Exception {
		Scanner sc = new Scanner(Dev.in);
		while (true) {
			Dev.out.println();
			Dev.out.println("Enter query:");
			String query = sc.nextLine();
			Dev.out.println(search(query));
		}
	}
	
	private static SearchResult search (String query) {
		int n = query.split(" ").length;
		SourceText sourceText = new SourceText(query, n);
		Map<String, SearchResult> results = engine.search(sourceText);
		
		return results.get(query);		
	}	
	
	//
	// Test a batch of files
	//
	private static void testSuspectFiles () throws Exception {
		testSuspectFiles(3, 3);
	}
	private static void testSuspectFiles (int i, int n) throws Exception {	
		String docsFolder = "."+File.separator+"Documents"+File.separator;
		File[] suspects = (new File(suspectsPath)).listFiles();
		
//		SearchEngine engine = new OfflineSearch(indexPath);
		SearchEngine engine = new GoogleSearch();
		Processor processor = new Processor();
		
		engine.setConsole(false);
		
		while (i <= n) {
			StringBuilder builder = new StringBuilder();
			for (File file : suspects) {
/*
				String docPath = docsFolder+StringUtils.substringBeforeLast(file.getName(), ".")+File.separator;
				File document = new File(docPath+file.getName());
				if (!document.exists()) {
					document.getParentFile().mkdirs();
					Files.copy(file.toPath(), document.toPath());
					Dev.out.println("Local copy of document saved to "+document.getParentFile());
				}
*/
				Dev.out.println();
				Dev.out.println(file.getName());
				
				SourceText sourceText = new SourceText(file, i);

				String fileRoot = StringUtils.substringBeforeLast(file.getName(), ".");
				String fileRootPath = docsFolder+fileRoot+File.separator+fileRoot;
				engine.setCache(Cache.USE, fileRootPath+"."+i+"."+engine+".dat");
				
				processor.process(engine.search(sourceText), sourceText);
				Set<URLResult> urlResults = processor.urlResults();

				String task = StringUtils.substringAfterLast(file.getName(), "_");
				String csv = Dev.asCSV(file.getName(), "orig_"+task, 0.0);		// default for files that have no matches
				
				double top = 0.0;
				for (URLResult urlResult : urlResults) {
					double match = sourceText.getPercentMatch(urlResult) / 100.0;
					if (match >= top) {
						top = match;

						String url = urlResult.toString();
//						String url = StringUtils.substringAfterLast(urlResult.toString(), File.separator);
					
						csv = Dev.asCSV(file.getName(), url, match);
					}
				}
				builder.append(csv);
			}
			Dev.output(builder, "output."+i+".csv");
			i++;
		}
	}
	
	//
	// For building offline indexes
	//
	private static void index () throws Exception {
		index(3, 3);
	}
	@SuppressWarnings("unchecked")
	private static void index (int i, int n) throws Exception {
		Map<String, HashSet<File>> index = (Map<String, HashSet<File>>)Dev.importCache(indexPath);
		if (index == null) index = new HashMap<String, HashSet<File>>();	
		File[] sources = (new File(sourcesPath)).listFiles();
		
		Dev.out.println("Indexing "+sourcesPath+"...");
		
		int complete = 0;
		int total = sources.length;
		for (File file : sources) {
			int j = i;
			while (j <= n) {
				Set<String> ngrams = new HashSet<>((new SourceText(file, j)).getNGrams());
				for (String ngram : ngrams) {
					HashSet<File> matches = index.get(ngram);
					if (matches == null) {
						matches = new HashSet<>();
						index.put(ngram, matches);
					}
					matches.add(file);
				}
				j++;
			}
			complete++;
			int percent = 100 * complete / total;
			Dev.out.print("Percent complete: "+percent+"%\r");
		}
		Dev.out.println();
		Dev.exportCache(index, indexPath);	
	}
}