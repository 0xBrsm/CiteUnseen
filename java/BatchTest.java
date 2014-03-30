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

class BatchTest {

	private class BatchRun implements Callable<Collection<FileTest>> {
		private final Processor processor;
		private final int id;
	
		BatchRun (Processor processor, int id) {
			this.processor = processor;
			this.id = id;
		}
		
		//===========================================================//	
		// Run a series of tests and return a collection of results
		//
		@Override
		public Collection<FileTest> call () {
			if (dynamic) buildSources(processor);
		Dev.out.println("Running...");
			Collection<FileTest> tests = new ArrayList<>();			
			for (File file : suspectFiles) {
				FileTest fileTest = new FileTest(file, processor);
				tests.add(fileTest);
			}
			return tests;
		}
	}
	
	//===========================================================//
	
	//===========================================================//
	private class Source {
		private final String id;
		private Map<String, Double> matches = new HashMap<>();
		
		Source (String id) {
			this.id = id;
		}
		
		private boolean add (String match, double value) {
			return matches.put(match, value) != null ? true : false;
		}
		
		private void reset () {
			matches = new HashMap<>();
		}
		
		private double value (SourceFragment match) {
			Double v = matches.get(match.toString());
			return v == null ? 0 : v;
		}
		
		private int relevant () {
			return id == null ? 0 : 1;
		}
		
		@Override
		public String toString () {
			return id;
		}
		
		// Comparators - based on id only
		@Override
		public int hashCode () {
			return this.toString().hashCode();
		}
		
		@Override	
		public boolean equals (Object obj) {
			if (obj == null) return false;
			if (!(obj instanceof Source)) return false;
			if (!((Source)obj).toString().equals(this.toString())) return false;
			
			return true;
		}		
	}
	//===========================================================//	

	//===========================================================//	
	private class FileTest {
		private final String name;
		
		FileTest (File file, Processor processor) {
			this.name = file.getName();

			Source source = sources.get(file);
			SourceText text = texts.get(file);		
			Set<SourceFragment> fragments = processor.process(results.get(file), text);
			
			runTest(source, text, fragments, processor.disabled());
		}
		
		// ======= Start building output =======//

		private int relevant;
		private double similarity;
		private int hitsReturned;
		private int sourcesFound;
		private double precision;
		private double recall;
		
		private void runTest (Source source, SourceText text, Set<SourceFragment> fragments, boolean disabled) {
			//
			// Set the number of relevants
			//
			relevant = source.relevant();
			
			//
			// Find the overlap between the fragments and the file
			//
			similarity = text.getSimilarity(fragments);
		
			//
			// Find the hits that are actually displayed to the user
			//
			
			// if Processor is disabled, this is just the visible hits
			//
			Set<SourceFragment> hits = disabled ? text.getVisible(fragments) : SourceFragment.switchToMatches(text.getVisible(fragments));
			hitsReturned = hits.size();
			
			//
			// Count the source files found
			// And add to our relevant value
			//
			double truePositives = 0;
			if (relevant > 0) for (SourceFragment hit : hits) {
				double v = source.value(hit);
				if (v > 0) {
					sourcesFound++;
					truePositives = v > truePositives ? v : truePositives;
				}
			}
			
			//
			// Figure out how much we got wrong
			//
			int falsePositives = hitsReturned - sourcesFound;
			double falseNegatives = relevant - truePositives;
		
			//
			// Determine our precision and recall
			//
			BigDecimal numerator;
			BigDecimal denominator;
		
			precision = 1.0;
			if (falsePositives != 0) {
				numerator = new BigDecimal(sourcesFound);
				denominator = new BigDecimal(sourcesFound + falsePositives);		
				precision = numerator.divide(denominator, 2, BigDecimal.ROUND_HALF_UP).doubleValue();
			}
		
			recall = 1.0;
			if (falseNegatives != 0) {
				numerator = new BigDecimal(truePositives);
				denominator = new BigDecimal(truePositives + falseNegatives);		
				recall = numerator.divide(denominator, 2, BigDecimal.ROUND_HALF_UP).doubleValue();
			}
		}
		
		@Override
		public String toString () {
			return Dev.asCSV(name, similarity, hitsReturned, sourcesFound, precision, recall, relevant);
		}
	}
	
	//===========================================================//	
	// Create batch test results. Can be returned as summary or full
	//
	private Collection<FileTest> tests;	
	private final ExecutorService executor = Executors.newFixedThreadPool(100);	
	private final LinkedList<Future<Collection<FileTest>>> queue = new LinkedList<>();
	
	public void start (Processor processor) {
		Callable<Collection<FileTest>> batch = new BatchRun(processor, queue.size());
		queue.add(executor.submit(batch));
	}
	
	public BatchTest test (Processor processor) {
		BatchRun batch = new BatchRun(processor, 0);
		tests = batch.call();
		return this;
	}
	
	public String results () {
		return tests == null ? null : results(tests);
	}
	public String summary () {
		return tests == null ? null : summarize(tests);
	}
	
	public boolean next () {
		if (queue.size() == 0) {
			tests = null;
			return false;
		}
		try {
			tests = queue.pop().get();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public void end () {
		executor.shutdown();
	}

	//===========================================================//	
	// Run a series of tests and return a collection of results
	//
/*	private Collection<FileTest> runBatch (Processor processor) {
		if (online) buildSources(processor);
		
		int count = 0;
		Collection<FileTest> tests = new ArrayList<>();		
		for (File file : suspectFiles) {
			Dev.out.print((100 * count / suspectFiles.size())+"% complete.\r");
			FileTest fileTest = new FileTest(file, processor);
			tests.add(fileTest);
			count++;
		}
		return tests;
	}
	*/
	//===========================================================//	
	// Return results as summary or full results
	//
	private String results (Collection<FileTest> fileTests) {
		StringBuilder builder = new StringBuilder();
		builder.append(Dev.asCSV("File Name", "Similarity", "Hits Returned", "Sources Found", "Precision", "Recall", "Relevant"));
		for (FileTest fileTest : fileTests)
			builder.append(fileTest);
		return builder.toString();
	}	

	private String summarize (Collection<FileTest> fileTests) {
		int total = 0;
		BigDecimal similarity = new BigDecimal(0);
		BigDecimal precision = new BigDecimal(0);
		BigDecimal recall = new BigDecimal(0);

		for (FileTest fileTest : fileTests) {
			total++;
			similarity = similarity.add(new BigDecimal(fileTest.similarity));
			precision = precision.add(new BigDecimal(fileTest.precision));
			recall = recall.add(new BigDecimal(fileTest.recall));
		}
		similarity = similarity.divide(new BigDecimal(total), 2, BigDecimal.ROUND_HALF_UP);
		precision = precision.divide(new BigDecimal(total), 2, BigDecimal.ROUND_HALF_UP);
		recall = recall.divide(new BigDecimal(total), 2, BigDecimal.ROUND_HALF_UP);
		
		double f1 = (2.0 * precision.doubleValue() * recall.doubleValue()) / (precision.doubleValue() + recall.doubleValue());
		double f2 = (5.0 * precision.doubleValue() * recall.doubleValue()) / (4.0 * precision.doubleValue() + recall.doubleValue());
		
		return Dev.asCSV(total, similarity, precision, recall, f1, f2);
	}
	
	//===========================================================//		
	// Constructors
	//
	private boolean dynamic;
	private boolean online;
	private Collection<File> sourceFiles;	
	private Collection<File> suspectFiles;
	BatchTest (String corpus, SearchEngine engine, int n) throws Exception {
		this(corpus, engine, n, null, new ArrayList<String>());
	}
	BatchTest (String corpus, SearchEngine engine, int n, Processor processor) throws Exception {
		this(corpus, engine, n, processor, new ArrayList<String>());
	}
	BatchTest (String corpus, SearchEngine engine, int n, Collection<String> skips) throws Exception {
		this(corpus, engine, n, null, skips);
	}	
	BatchTest (String corpus, SearchEngine engine, int n, Processor processor, Collection<String> skips) throws Exception {
		this(corpus); // set paths

		online = engine instanceof OfflineSearch ? false : true;		
		dynamic = processor == null && online ? true : false;
		
		sourceFiles = getFiles(sourcesPath);
		suspectFiles = getFiles(suspectsPath, skips);
		
		Map<String, String> metadata = loadInfo(metaFile);
		createSources(suspectFiles, metadata, createSources(sourceFiles, metadata));
	
		generateResults(suspectFiles, engine, n);
	
		// If we're online, we always have to generate results
		// and if there was a processor passed, obviously the intent was to generate results
		//
		if(online || processor != null)
			generateResults(sourceFiles, engine, n);
	
		buildSources(processor);
	}

	//===========================================================//		
	// Set paths - this is a "helper" constructor
	//
	private final String corpus;
	private final String corpusPath;
	private final String sourcesPath;
	private final String suspectsPath;
	private final String metaFile;	
	private BatchTest (String corpus) {
		String cachePath = 		"Documents"		+File.separator;
		String rootPath = 		"corpora"		+File.separator;
		String sourcesFolder = 	"Sources"		+File.separator;
		String suspectsFolder = "Suspects"		+File.separator;
		String metaName =		"metadata.csv";
		
		this.corpus = corpus;
		corpusPath = rootPath+corpus+File.separator;
		sourcesPath = corpusPath+sourcesFolder;
		suspectsPath = corpusPath+suspectsFolder;
		metaFile = corpusPath+metaName;
	}
	
	//===========================================================//		
	// Generate search results
	//
	private Map<File, SourceText> texts = new HashMap<>();
	private Map<File, Map<String, SearchResult>> results = new HashMap<>();
	private void generateResults(Collection<File> files, SearchEngine engine, int n) throws Exception {	
		for (File file : files) {
			if (online) {
				Dev.copyToCache(file);		
				engine.setCache(Cache.USE, Dev.datPath(file, n, engine));
			}
			SourceText text = new SourceText(file, n);
			texts.put(file, text);
			results.put(file, engine.search(text));
		}
	}	
	
	//===========================================================//		
	// Create Source objects for every file
	//
	private Map<File, Source> sources = new HashMap<>();
	private Map<String, Source> createSources (Collection<File> files, Map<String, String> metadata) {
		return createSources(files, metadata, new HashMap<String, Source>());
	}
	private Map<String, Source> createSources (Collection<File> files, Map<String, String> metadata, Map<String, Source> sourcesById) {
	Dev.out.println("Creating sources...");
		for (File file : files) {
			String id = metadata.get(file.getName());
			Source source = sourcesById.get(id);
			if (source == null) {
				source = new Source(id);
				sourcesById.put(id, source);
			}
			sources.put(file, source);
		}
		return sourcesById;
	}
	
	//===========================================================//		
	// Fill our Source objects with data
	//
	private void buildSources () {
		buildSources(null);
	}
	private void buildSources (Processor processor) {
	Dev.out.println("Building sources...");
		for (File file : sourceFiles) {
			Source source = sources.get(file);
			source.reset();				
			
			String link = file.getPath();
			if (processor != null) {		
				Set<SourceFragment> hits = processor.process(results.get(file), texts.get(file));				
				Set<SourceFragment> visibles = texts.get(file).getVisible(hits);
				if (!processor.disabled()) {
					hits = SourceFragment.switchToMatches(hits);
					visibles = SourceFragment.switchToMatches(visibles);
				}
				for (SourceFragment hit : hits) {
					link = hit.toString();
					if (visibles.contains(hit))
							source.add(link, 1.0);
					else	source.add(link, 0.5);
				}			
			} else source.add(link, 1.0);
		}
	}

	//===========================================================//		
	// For loading our file info from csv
	//
	private Map<String, String> loadInfo (String data) {
		Map<String, String> info = new HashMap<>();	
		String line = "";
		
		try (BufferedReader reader = new BufferedReader(new FileReader(data))) {		
			while ((line = reader.readLine()) != null) {
				String[] fields = line.split(",");
				String name = StringUtils.strip(fields[0], "\"");
				String id = StringUtils.strip(fields[1], "\"");
				if (fields.length > 2) {
					String category = StringUtils.strip(fields[2], "\"");
					if (category.equals("No") || category.equals("non"))
						id = null;
				}
				info.put(name, id);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return info;
	}
	
	//===========================================================//		
	// Get a list of files, without any files we should skip
	//
	private Collection<File> getFiles (String path) {
		return getFiles(path, new ArrayList<String>());
	}
	private Collection<File> getFiles (String path, Collection<String> skips) {
		File[] fileArray = (new File(path)).listFiles();	
		List<File> files = new ArrayList<>(fileArray.length);
		for (File file : fileArray) {
			if (!skips.contains(file.getName()))
				files.add(file);
		}
		return files;
	}
}
 
