/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;

import java.io.*;
import java.util.*;
import java.math.*;
import java.nio.file.*;
import java.util.concurrent.*; 

import org.apache.commons.lang3.StringUtils;

class Test {

	private static class TestThread extends Thread {
		private final int n;
		
		TestThread (int n) {
			this.n = n;
		}
		
		@Override
		public void run () {
			try { testBatch(n); }
			catch (Exception e) { Dev.out.println(e.getMessage()); }
		}
	}

	private static boolean online = true;
	private static SearchEngine engine;
	private static String cachePath = Dev.cachePath();
	private static String rootPath = "corpora"+File.separator;
	private static String corpus;
	private static String corpusPath;
	private static String sourcesPath;
	private static String suspectsPath;
	private static String indexPath;

	private static void setPaths (String input) {
		corpus = input;
		corpusPath = rootPath+corpus+File.separator;
		sourcesPath = corpusPath+"Sources"+File.separator;
		suspectsPath = corpusPath+"Suspects"+File.separator;
		indexPath = corpusPath+"index.dat";		
	}
	
	private static Map<String, String> sourceMap = new HashMap<>();
	static {
		sourceMap.put("http://en.wikipedia.org/wiki/Object_oriented_programming", 	"taska");
		sourceMap.put("http://en.wikipedia.org/wiki/PageRank", 						"taskb");
		sourceMap.put("http://en.wikipedia.org/wiki/Vector_space_model", 			"taskc");
		sourceMap.put("http://en.wikipedia.org/wiki/Bayes_theorem", 				"taskd");
		sourceMap.put("http://en.wikipedia.org/wiki/Dynamic_programming", 			"taske");
	}
	private static Set<String> skips = new HashSet<>();
	private static void useSkips () {
		skips.add("g2pE_taske.txt");
		skips.add("g4pC_taskb.txt");
		skips.add("g2pA_taske.txt");
		skips.add("g2pE_taskd.txt");
		skips.add("g4pE_taskd.txt");
		skips.add("g0pA_taske.txt");
		skips.add("g4pD_taskc.txt");
		skips.add("g4pE_taske.txt");
		skips.add("g4pC_taskc.txt");
		skips.add("g0pE_taskc.txt");
		skips.add("g4pD_taskd.txt");
		skips.add("g2pE_taskc.txt");
	}
	
	public static void main (String[] args) throws Exception {
		String index;
		if (args.length > 1)
				index = args[1];
		else	index = args[0];
	
		switch (index.toLowerCase()) {
			case "bing"		:	engine = new BingSearch();break;
			case "bingweb"	:	engine = new BingWebSearch();break;
			case "faroo"	:	engine = new FarooSearch();break;
			case "google"	:	engine = new GoogleSearch();break;
			case "yahoo"	:	engine = new YahooSearch();break;
			default			:	setPaths(index);
								online = false;
		}
		
		Timer timer = Timer.startNew("Test");
		List<Thread> tests = new ArrayList<>();
		switch (args[0]) {
			case "-i": index(); break;			
			case "-q": query(); break;
			case "-o": resultSizeTest(); break;
			case "-t": timeTest(); break;
			default	 : 
				setPaths(args[0]);
				if (online && corpus.equals("COPSA"))
					useSkips();
				
				for (int n = 1; n <= 5; n++) {
					testBatch(n);

	//				Thread test = new TestThread(n);
		//			test.start();
			//		tests.add(test);
				}
					
		}
		for (Thread test : tests)
			test.join();
			
		timer.stop();
	}
	
	//
	// Test suspect files against an index
	//	
	private static void testBatch (int n) throws Exception {
		Dev.out.println("Testing "+n+"-grams");

		if (!online) {
			if (corpus.equals("Webis-CPC-11"))
					engine = new OfflineSearch(corpusPath, n);
			else 	engine = new OfflineSearch(corpusPath);
		} else		engine = new GoogleSearch();

		Processor processor = Processor.build().setScoreByRarity(false, false).setMinimumScore(10);
		BatchTest batch = new BatchTest(corpus, engine, n, processor, skips);
		
		StringBuilder builder = new StringBuilder();
		for (int j = 1; j < 21; j++) {
			double C = j / 10.0;

			processor = Processor.build().setScoreByRarity(false, false, C);
			builder.append(getSummaryBlock("F/F/"+C, batch, processor));
		}
		Dev.out.println();
		Dev.output(builder, "Output"+File.separator+engine+"."+n+".csv");
		
		batch.end();
	}

	private static String getSummaryBlock (String label, BatchTest batch, Processor processor) {
		StringBuilder builder = new StringBuilder();
		builder.append(Dev.asCSV(label, "Total Files", "Similarity", "Precision", "Recall", "F1", "F2"));		
		for (int s = 1; s <= 20; s++) {
			processor = processor.copy().setMinimumScore(s);			
			batch.start(processor);
		}
		int s = 0;
		String summary = "";
		while (batch.next()) {
			s++;
			builder.append(s+","+batch.summary());
		}
		builder.append("\n");

		Dev.out.print(".");
		
		return builder.toString();
	}
	
	// Other test stuff
	//
	@SuppressWarnings("unchecked")	
	private static void resultSizeOfflineTest () throws Exception {
		int n = 3;	
		StringBuilder builder = new StringBuilder();
		SearchEngine engine = new OfflineSearch(corpusPath, n);	
		Processor processor = Processor.build().disable();
		
		File[] files = (new File(suspectsPath)).listFiles();
		for (File file : files) {
			SourceText sourceText = new SourceText(file, n);
			Set<SourceFragment> urls = processor.process(engine.search(sourceText), sourceText);
			builder.append(Dev.asCSV(file, sourceText.size(), urls.size()));
		}
		Dev.output(builder, "size_test.csv");
	}
	
	@SuppressWarnings("unchecked")	
	private static void resultSizeTest () throws Exception {
		StringBuilder builder = new StringBuilder();
		SearchEngine engine = new GoogleSearch();
		engine.setCache(Cache.USE, "");
		engine.setSnippetSearch(true);
		engine.setMaxRetries(2);
		Processor processor = Processor.build().disable();
		
		int n = 2;
		File[] files = (new File("Time Tests")).listFiles();
		for (File file : files) {
			SourceText sourceText = new SourceText(file, n);
			engine.setCache(Cache.USE, Dev.datPath(file, n, engine));
//			Map<String, String> serps = (Map<String, String>)Dev.importCache(Dev.datPath(file, n, engine));
//			Set<SourceFragment> urls = processor.process(engine.parseResults(serps, sourceText), sourceText);
			Set<SourceFragment> urls = processor.process(engine.search(sourceText), sourceText);			
			builder.append(Dev.asCSV(file, sourceText.size(), urls.size()));
		}
		Dev.output(builder, "size_test.csv");
	}
	
	private static void testOneRun (int n) throws Exception {
		Processor processor = Processor.build().setScoreByRarity(false, false);
		engine = new OfflineSearch(corpusPath, n);

		StringBuilder builder = new StringBuilder();
		BatchTest batch = new BatchTest(corpus, engine, n);
		
		Timer timer = new Timer("Batch Test");
		for (int i = 10; i <= 10; i++) {
			processor = processor.copy().setMinimumScore(i);
			batch.start(processor);
		}
		int s = 0;
		while (batch.next()) {
			s++;
			builder.append(batch.results());
		}
		timer.stop();			
		Dev.output(builder, "test.csv");
	}
	
	private static void testDisabled () throws Exception {
		StringBuilder builder = new StringBuilder();
		builder.append(Dev.asCSV("n=", "Total Files", "Similarity", "Precision", "Recall", "F1", "F2"));		
		for (int n = 1; n <= 9; n++) {
			Dev.out.println("Testing "+n+"-grams");
			if (corpus.equals("Webis-CPC-11")) engine = new OfflineSearch(corpusPath, n);
			else engine = new GoogleSearch();
			engine.setSnippetSearch(false);
			useSkips();
			Processor processor = Processor.build().setScoreByRarity(false, false).setMinimumScore(10);			
			BatchTest batch = new BatchTest(corpus, engine, n, processor, skips);
			builder.append(n+","+batch.test(Processor.build().disable()).summary());
		}
		Dev.out.println();
		Dev.output(builder, "Output"+File.separator+engine+".disabled.csv");
	}
	
	//===========================================================//
	// Time testing
	//===========================================================//	
	@SuppressWarnings("unchecked")
	public static void timeTest () throws Exception {
		StringBuilder builder = new StringBuilder();
		Timer timer = new Timer();
		
		SearchEngine engine = new GoogleSearch();
		engine.setCache(Cache.IMPORT, "");
		engine.setSnippetSearch(true);			
		Processor processor = Processor.build().setScoreByRarity(false, true);
		
		int n = 3;
		File[] files = (new File("Time Tests")).listFiles();
		for (File file : files) {
			Map<String, String> serps = (Map<String, String>)Dev.importCache(Dev.datPath(file, n, engine));
			
			long total = 0;
			SourceText sourceText = null;
			Set<SourceFragment> urls = new HashSet<>();
			for (int i = 0; i < 11; i++) {
				if (i > 0) timer.start();
				
				sourceText = new SourceText(file, n, true);
				Map<String, SearchResult> results = engine.parseResults(serps, sourceText);				
				processor.process(results, sourceText);
				
				if (i > 0) total += timer.stop().ns();
			}
			builder.append(Dev.asCSV(file, sourceText.size(), total/10));
		}
		Dev.output(builder, "time_test.csv");
	}
	
	//===========================================================//	
	// Build the meta data for the Webis corpus
	//
	public static void buildWebis () throws Exception {
		String metaPath = corpusPath+"Metadata"+File.separator;
		File[] metaFiles = (new File(metaPath)).listFiles();
		
		StringBuilder builder = new StringBuilder();
		for (File meta : metaFiles) {
			String entry = StringUtils.substringBefore(meta.getName(), "-")+"-paraphrase.txt";
			String line = "";
			try (
				BufferedReader reader = new BufferedReader(new FileReader(meta));
			) {
				String copied = "";
				String id = "";
				while ((line = reader.readLine()) != null) {
					String[] fields = line.split(": ");
					String field = fields[0];
					if (field.equals("Paraphrase"))
						copied = fields[1];
					else if (field.equals("HITId"))
						id = fields[1];
				}
				builder.append(Dev.asCSV(entry, copied, id));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		Dev.output(builder, corpusPath+"metadata.csv");
	}	

	//
	// Query the given index
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
	// For building offline indexes
	//
	private static void index () throws Exception {
		for (int n = 1; n <= 9; n++) {
//			String indexPath = corpusPath+"index."+n+".dat";
			index(n, indexPath);
		}
	}
	
	private static void index (int n, String path) throws Exception {
		index(n, n, path);
	}
	@SuppressWarnings("unchecked")
	private static void index (int i, int n, String indexPath) throws Exception {
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