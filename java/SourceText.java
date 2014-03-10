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
import java.math.*;
import java.net.URL;
import java.text.BreakIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;
import org.apache.lucene.analysis.tokenattributes.*;
import org.apache.tika.*;


public class SourceText implements TokenizedText {
	private final String sourceText;
	private final Map<String, NGram> nGrams = new HashMap<>();
	private final ArrayList<NGram> nGramsList = new ArrayList<>();
	private final ArrayList<OffsetAttribute> offsetsList = new ArrayList<>();
	private final Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_46, CharArraySet.EMPTY_SET);
	private final int n;

	// Create our Tika parser for parsing non-strings
	//
	private final static Tika tika = buildParser();
	private static Tika buildParser () {
		Tika parser = new Tika();
		parser.setMaxStringLength(-1);
		
		return parser;
	}		

	// SourceText constructors, get/set methods //
	
	// Create SourceText from File
	//
	public SourceText (File file, int n) throws Exception {
		this(tika.parseToString(file), n);
	}
	public SourceText (File file, int n, boolean ignoreCitations) throws Exception {
		this(tika.parseToString(file), n, ignoreCitations);
	}	
	
	// Create SourceText from InputStream
	//
	public SourceText (InputStream fileStream, int n) throws Exception {
		this(tika.parseToString(fileStream), n);
	}
	public SourceText (InputStream fileStream, int n, boolean ignoreCitations) throws Exception {
		this(tika.parseToString(fileStream), n, ignoreCitations);
	}
	
	// Create SourceText from URLResult
	//	
	public SourceText (URLResult url, int n) throws Exception {
		this(tika.parseToString(url.toURL()), n);
	}
	public SourceText (URLResult url, int n, boolean ignoreCitations) throws Exception {
		this(tika.parseToString(url.toURL()), n, ignoreCitations);
	}	

	// Create SourceText from URL
	//	
	public SourceText (URL url, int n) throws Exception {
		this(tika.parseToString(url), n);
	}
	public SourceText (URL url, int n, boolean ignoreCitations) throws Exception {
		this(tika.parseToString(url), n, ignoreCitations);
	}	

	// Create SourceText from String
	//
	public SourceText (String sourceText, int n) {
		this(sourceText, n, false);
	}
	public SourceText (String sourceText, int n, boolean ignoreCitations) {
		this.sourceText = sourceText;
		this.n = n;
	
		String text = sourceText;
		if (ignoreCitations)
			text = removeCitations(sourceText);
		generateNGrams(text);
	}

	// Calculate the percent match between this source text and one or
	// more fragments of that sourceText
	//
	public double getPercentMatch (SourceFragment fragment) {
		return getPercentMatch(fragment.positions());
	}
	
	public double getPercentMatch (Set<? extends SourceFragment> fragments) {
		Set<Integer> positions = new HashSet<>();
		for (SourceFragment fragment : fragments)
			positions.addAll(fragment.positions());
		return getPercentMatch(positions);
	}
	
	public double getPercentMatch (Collection<Integer> positions) {
		BigDecimal percent = new BigDecimal(100.0 * positions.size() / this.length());
		percent = percent.setScale(1, RoundingMode.HALF_UP);
		
		return percent.doubleValue();
	}
	
	// Compare the passed fragment with this source text and return a fragment
	// representing the overlap
	//
	public SourceFragment getOverlap (String text) {
		Set<String> ngrams = generateNGrams(text, this);
		SourceFragment overlap = new SourceFragment(this);
		for (String ngram : ngrams)
			overlap.add(locate(ngram));		
		return overlap;
	}	
	
	@Override
	public SourceFragment getOverlap (TokenizedText tokenizedText) {
		Set<String> ngrams = tokenizedText.getNGrams();
		SourceFragment overlap = new SourceFragment(this);
		for (String ngram : ngrams)
			overlap.add(locate(ngram));		
		return overlap;
	}
	
	// General getter methods
	//
	public Analyzer getAnalyzer () {
		return analyzer;
	}
	
	public int getN () {
		return n;
	}

	@Override	
	public Set<String> getNGrams () {
		return nGrams.keySet();
	}
	
	public String toString () {
		return sourceText;
	}
	
	public int startOffset (int position) {
		return offsetsList.get(position).startOffset();
	}
	
	public int endOffset (int position) {
		return offsetsList.get(position).endOffset();
	}
	
	public String get (Integer position) {
		return nGramsList.get(position).toString();
	}
	
	public boolean contains (String ngram) {
		if (nGrams.get(ngram) != null)
			return true;
		return false;
	}
	
	public Set<Integer> locate (String ngram) {
		Set<Integer> positions = new HashSet<>();
		
		NGram nGram = nGrams.get(ngram);		
		if (nGram != null)
			positions = nGram.positions();
		
		return positions;
	}
	
	// Length of the document in ngrams
	public int length () {
		return nGramsList.size();
	}
	
	// Total number of unique ngrams
	public int size () {
		return nGrams.size();
	}
	
	//===========================================================//
	// Match each fragment to a particular character in the source
	// document. This is the best way to ensure no fragment
	// overlap.
	//
	public List<SourceFragment> getOverlapByChar (SourceFragment fragment) {
		return getOverlapByChar(Collections.singleton(fragment));
	}
	
	public List<SourceFragment> getOverlapByChar (Set<? extends SourceFragment> fragments) {
		int size = sourceText.length();
		List<SourceFragment> fragmentsByChar = Arrays.asList(new SourceFragment[size]);

		for (SourceFragment fragment : fragments) {
			for (Integer position : fragment.positions()) {
				int start = startOffset(position);
				int end = endOffset(position);
				while (start < end) {
					SourceFragment current = fragmentsByChar.get(start);
					if (fragment.compareTo(current) > 0)
						fragmentsByChar.set(start, fragment);
					start++;
				}
			}
		}
		return fragmentsByChar;
	}
	
	// Get a text representation of this source fragment
	//
	public String getOverlapAsText (String text) {
		return getOverlapAsText(getOverlap(text));
	}
	public String getOverlapAsText (SourceText sourceText) {
		return getOverlapAsText(getOverlap(sourceText));
	}
	public String getOverlapAsText (SourceFragment fragment) {
		return getOverlapAsText(Collections.singleton(fragment));
	}
	public String getOverlapAsText (Set<? extends SourceFragment> fragments) {
		StringBuilder builder = new StringBuilder();
		List<SourceFragment> sourceTextByChar = getOverlapByChar(fragments);		
		
		int position = 0;
		boolean gap = false;
		for (SourceFragment location : sourceTextByChar) {
			if (location != null) {
				builder.append(sourceText.charAt(position));
				gap = false;
			} else if (!gap) {
				builder.append("...");
				gap = true;
			}
			position++;
		}
		return builder.toString();
	}

	public String getOverlapInContext (SourceFragment fragment) {
		return getOverlapInContext(Collections.singleton(fragment));
	}
	public String getOverlapInContext (Set<? extends SourceFragment> fragments) {
		List<SourceFragment> sourceTextByChar = getOverlapByChar(fragments);
		int last = sourceTextByChar.size() - 1;
		String sourceText = null;
		
		int contextStart = last;
		int contextEnd = 0;
		for (SourceFragment fragment : fragments) {
			if (sourceText == null)
				sourceText = fragment.getSourceText().toString();
				
			int start = sourceTextByChar.indexOf(fragment);
			int end = sourceTextByChar.lastIndexOf(fragment);
			
			if (start < contextStart) contextStart = start;
			if (end > contextEnd) contextEnd = end;		
		}
		BreakIterator it = BreakIterator.getSentenceInstance();
		it.setText(sourceText);	
		
		contextStart = it.preceding(contextStart);
		contextEnd = it.following(contextEnd+4);
		
		return sourceText.substring(contextStart, contextEnd);
	}

	//===========================================================//	
	// Generate ngrams from the given text and remember original text location for later matching
	//
	private void generateNGrams (String text) {
		int position = 0;					
		try {
			TokenStream nGramStream = getNGramStream(text, this);
		
			CharTermAttribute charAttrib = nGramStream.getAttribute(CharTermAttribute.class);
			OffsetAttribute offsetAttrib = nGramStream.getAttribute(OffsetAttribute.class);
			
			nGramStream.reset();
			while (nGramStream.incrementToken()) {
				String ngram = charAttrib.toString();		// get our formatted ngram string
				NGram nGram = nGrams.get(ngram);			// is this ngram already in the collection?
				if (nGram == null) {
					nGram = new NGram(ngram, this);			// if not, create a new nGram for it
					nGrams.put(ngram, nGram);				// and add it
				}
				nGram.add(position);						// add this position to it

				// Add a deep copy of this offset to our list of offsets in order
				//
				OffsetAttribute offset = new OffsetAttributeImpl();
				offset.setOffset(offsetAttrib.startOffset(), offsetAttrib.endOffset());
				offsetsList.add(offset);
				
				// add to list for ngrams in order				
				nGramsList.add(nGram);
				
				position++;				
			}
			nGramStream.close();
		} catch (IOException e) { e.printStackTrace(); }	// this will never happen since it's a StringReader...
	}
	
	// Quick static method to generate only text string ngrams as a utility method
	// but based on the parameters of an existing SourceText object
	//
	// This ensures we returned ngrams in an identical format, without any other fluff
	//
	public static TokenStream getNGramStream (String text, SourceText source) throws IOException {
		TokenStream nGramStream;
		
		Analyzer analyzer = source.getAnalyzer(); 	// analyze the text the same way as source
		int n = source.getN();						// match the token count of source
		
		TokenStream words = analyzer.tokenStream(null, new StringReader(text));
		if (n > 1) {
			nGramStream = new ShingleFilter(words, n, n);
			((ShingleFilter)nGramStream).setOutputUnigrams(false);
		} else
			nGramStream = words;
			
		return nGramStream;
	}
	
	public static Set<String> generateNGrams (String text, SourceText source) {
		Set<String> ngrams = new HashSet<>();
		try {
			TokenStream nGramStream = getNGramStream(text, source);
			CharTermAttribute charAttrib = nGramStream.getAttribute(CharTermAttribute.class);
			
			nGramStream.reset();
			while (nGramStream.incrementToken())		
				ngrams.add(charAttrib.toString());
			nGramStream.close();
		} catch (IOException e) { e.printStackTrace(); }	// this will never happen since it's a StringReader...
		
		return ngrams;
	}	
	
//	Debug code	//
	public void dumpNGrams (String root) {
		String path = root+".ngrams.csv";
		StringBuilder builder = new StringBuilder();
		
		for (NGram nGram : nGrams.values()) {
			builder.append(Dev.asCSV(nGram, nGram.positionsInOrder()));
		}
			
		Dev.output(builder, path);
	}
	
	public String removeCitations (String text) {
		Pattern pattern;
		Matcher matcher;
	
		// Remove the references section
		//
		String sources =
			"[^\\S ]\\s*("
			+ "Bibliography|"
			+ "Citations|"
			+ "References|"
			+ "Works Cited"
			+ ")\\s*[^\\S ].*";
		
		pattern = Pattern.compile(sources, Pattern.CASE_INSENSITIVE + Pattern.DOTALL);
		text = pattern.matcher(text).replaceFirst("");
		
		// Remove all quotes and citations
		//
		
		// Define the plethora of Unicode quotes we want to look for
		String sq = "\""	// "
			+ "\\u201C"		// “
			+ "\\u201F"		// ‟
			+ "\\u301D"		//
			+ "\\u301F"		// 〟			
			+ "\\uFF02";	// ＂
			
		String eq = "\"" 	// "
			+ "\\u201D"		//
			+ "\\u201E"		// „
			+ "\\u301E"		// 〞
			+ "\\u301F"		// 〟
			+ "\\uFF02";	// ＂
		
		String cites = 
			""
			+ "["+sq+"]"			// Starts with ASCII or Unicode quotation mark
			+ "[^"+eq+"]+"			//  followed by one or more non-quotation characters
			+ "["+eq+"]"			//  ending with a quotation mark
			+ "\\s*"				//  followed by any number of whitespace characters
			+ ""					// Match one or more of the above
			+ ""
			+ "\\p{Ps}"				//  followed by an opening bracket
			+ "[^\\p{Pe}]+"			//	followed by one or more non-bracket characters
			+ "\\p{Pe}"				//  ending with a closing bracket
			+ "";					// Match one of the above
			
		pattern = Pattern.compile(cites, Pattern.UNICODE_CHARACTER_CLASS);
		matcher = pattern.matcher(text);
		StringBuffer buffer = new StringBuffer();
		
		while (matcher.find()) {
			int length = matcher.end() - matcher.start();
			String replace = StringUtils.repeat(".", length);
			matcher.appendReplacement(buffer, replace);
		}
		matcher.appendTail(buffer);
		
//		Dev.output(buffer, "output.txt");
		
		return buffer.toString();
	}
}
