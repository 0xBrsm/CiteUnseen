/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 *	ALM in IT Thesis
 *	Overview
 *	<date>	  
 *
 *	Main SourceText class to handle submitted text
 *
 */

package citeunseen;

import java.io.*;
import java.util.*;

import org.apache.commons.io.IOUtils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;
import org.apache.lucene.analysis.tokenattributes.*;
import org.apache.tika.*;


public class SourceText {
	private final String sourceText;
	private final Map<String, NGram> nGrams;
	private final ArrayList<NGram> nGramsList = new ArrayList<>();
	private final ArrayList<OffsetAttribute> offsetsList = new ArrayList<>();
	private final Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_46, CharArraySet.EMPTY_SET);
	private final int n;

	// SourceText constructors, get/set methods //
	
	// Create SourceText from inputstream or file
	//
	public SourceText (File file, int n) throws Exception {
		Tika parser = new Tika();		
		sourceText = parser.parseToString(file);
		this.n = n;
		nGrams = generateNGrams();
	}
	
	public SourceText (InputStream fileStream, int n) throws Exception {
		Tika parser = new Tika();		
		sourceText = parser.parseToString(fileStream);
		this.n = n;
		nGrams = generateNGrams();
	}
	

	// get/set methods
	//
	public Analyzer getAnalyzer () {
		return analyzer;
	}
	
	public int getN () {
		return n;
	}
	
	public Map<String, NGram> getNGrams () {
		return nGrams;
	}
	
	public List<NGram> getNGramsByOrder () {
		return nGramsList;
	}
	
	public String toString () {
		return sourceText;
	}
	
	public List<OffsetAttribute> getOffsets () {
		return offsetsList;
	}

	// Generate ngrams from the given text and remember original text location for later matching
	//
	private Map<String, NGram> generateNGrams () {
		Map<NGram, HashSet<Integer>> nGramsMap = new HashMap<>();
		Map<String, NGram> nGrams = new HashMap<>();
		int position = 0;
					
		try {
			TokenStream words = analyzer.tokenStream(null, new StringReader(sourceText));
			ShingleFilter nGramStream = new ShingleFilter(words, n, n);
			nGramStream.setOutputUnigrams(false);
		
			CharTermAttribute charAttrib = nGramStream.getAttribute(CharTermAttribute.class);
			OffsetAttribute offsetAttrib = nGramStream.getAttribute(OffsetAttribute.class);
			
			nGramStream.reset();
			while (nGramStream.incrementToken()) {
				String ngram = charAttrib.toString();					// get our formatted ngram string
				NGram nGram = nGrams.get(ngram);						// is this ngram already in the collection?
				if (nGram != null) nGram.add(position, offsetAttrib);	// if it is, add this position to it
				else {
					nGram = new NGram(ngram, position, offsetAttrib);	// if not, create a new nGram for it
					nGrams.put(ngram, nGram);							// and add it
				}
			
				HashSet<Integer> positions = nGramsMap.get(nGram);				// is this ngram already in the collection?
				if (positions == null) positions = new HashSet<Integer>();		// if not, create a new list of locations
				if (positions.add(position)) nGramsMap.put(nGram, positions);	// if this ngram isn't already in the collection, add it

				position++;

				// Add a deep copy of this offset to our list of offsets in order
				//
				OffsetAttribute offset = new OffsetAttributeImpl();
				offset.setOffset(offsetAttrib.startOffset(), offsetAttrib.endOffset());
				offsetsList.add(offset);
				
				nGramsList.add(nGram);	// add to list for ngrams in order
			}
			nGramStream.close();
		} catch (IOException e) { e.printStackTrace(); }	// this will never happen since it's a StringReader...

		return nGrams;
	}
	
	// Quick static method to generate only text string ngrams as a utility method
	// but based on the parameters of an existing SourceText object
	//
	// This ensures we're returned ngrams in an identical format, without any other fluff
	//
	public static Set<String> generateNGrams (String text, SourceText source) {
		Set<String> ngrams = new HashSet<>();
		Analyzer analyzer = source.getAnalyzer(); 	// analyze the text the same way as source
		int n = source.getN();						// match the token count of source
		
		try {
			TokenStream words = analyzer.tokenStream(null, new StringReader(text));
			ShingleFilter nGramStream = new ShingleFilter(words, n, n);
			nGramStream.setOutputUnigrams(false);
			CharTermAttribute charAttrib = nGramStream.getAttribute(CharTermAttribute.class);
			
			nGramStream.reset();
			while (nGramStream.incrementToken())		
				ngrams.add(charAttrib.toString());
			nGramStream.close();
		} catch (IOException e) { e.printStackTrace(); }	// this will never happen since it's a StringReader...
		
		return ngrams;
	}	
	
//	Debug code	//
	public void dumpNGrams (String root) throws Exception {
		PrintWriter writer = new PrintWriter(root+".ngrams.csv", "UTF-8");
		for (NGram nGram : nGrams.values())
			writer.println(nGram+","+nGram.getTotalResults()+",\""+nGram.getPositions()+"\",\""+nGram.getBestScore()+"\",\""+nGram.getResults()+"\"");
//			writer.println(nGram+","+nGram.getTotalResults()+",\""+Processor.getValue(nGram, nGrams.size())+"\"");

		writer.close();
	}

	public void dumpText (String root) throws Exception {
		dumpText(sourceText, root);
	}
	
	public static void dumpText (String text, String root) throws Exception {
		PrintWriter writer = new PrintWriter(root+".output.txt", "UTF-8");
		writer.println(text);
		writer.close();
	}
//	
}
