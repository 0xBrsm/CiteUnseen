/*
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 */

package citeunseen;

import java.io.*;
import java.util.*;
import java.math.*;

import org.apache.lucene.analysis.tokenattributes.*;

public class Processor {
	private long indexSize;					// index size for determining commonality of particular search results
	private int urlThreshold = 0;			// minimum number of occurrences for a URL to be considered worth checking
	private int wordThreshold = 0;			// minimum number of words in a row to count toward the overall score of a URL
	private int minScore = 0;				// minimum score for URL match sequences to be considered significant/displayed to user
	private boolean scoreByRarity = true;	// whether to weight the worth of matches by the number of results each returned in the search
	
	// Constructor
	//
	public Processor (long indexSize) {
		this.indexSize = indexSize;
	}
	
	// Setters for config parameters once defaults are established
	//
	public void setURLThreshold (int urlThreshold) {
		this.urlThreshold = urlThreshold;
	}
	
	public void setWordThreshold (int wordThreshold) {
		this.wordThreshold = wordThreshold;
	}
	
	public void setMinimumScore (int minScore) {
		this.minScore = minScore;
	}
	
	public void setScoreByRarity (boolean scoreByRarity) {
		this.scoreByRarity = scoreByRarity;
	}
	
	// full constructor, legacy
	//
	public Processor (int urlThreshold, int wordThreshold, int minScore, boolean scoreByRarity) {
		this.urlThreshold = urlThreshold;
		this.wordThreshold = wordThreshold;
		this.minScore = minScore;
		this.scoreByRarity = scoreByRarity;
	}

	// Other getters
	//
	private double getPercentMatch (SourceText sourceText, URLResult urlResult) {
		BigDecimal percent = new BigDecimal(100.0 * urlResult.getPositions().size() / sourceText.getNGrams().size()); // there is a bug here
		percent = percent.setScale(1, RoundingMode.HALF_UP);
		
		return percent.doubleValue();
	}
	
	// internal methods for all the heavy lifting
	//
	private double scoreNGram (NGram nGram) {				
		double v = 1.0;						// default score per match
		
		if (scoreByRarity) {	
			long total = nGram.getTotalResults();
			if (total == 0) {
				v = 5.0;
			} else {
				// weighted tf-idf option
				// let's just go with idf, m'kay?
//				int f = nGram.getPositions().size();
//				double tf = f / max;
//				double idf = Math.log10(indexSize / total);
//				v = tf * idf;
//				v = idf;

				// flat option
				v = Math.log(total);		//
				if (v == 0) v = 5.0;		// avoid divide by 0 if only one match
				else v = 1 / v;				// set our value for this match to the multiplicative inverse log

			}
		}	
		return v;
	}
	
	private List<Sequence> buildSequence (SourceText sourceText, URLResult urlResult) {
		Set<Integer> positions = urlResult.getPositions();
		List<Sequence> seq = new LinkedList<>();
		
		// skip URLs that have too few matches
		if (positions.size() < urlThreshold) return seq;	
		
		// Build word sequence array
		// Positives are number of matches in a row, negatives are number of mismatches in a row
		//
		List<NGram> nGrams = sourceText.getNGramsByOrder();	

		Sequence sub = new Sequence();
		int n = sourceText.getN();
		int p = 0;
		int last = 0;
		for (Integer i : positions) {
			// use the total number of results for this ngram to scale it
			//  the more results, the less likely we care about this match, so it should count for less
			//  the fewer results, the more interesting this match is, so it should count for more
			//
			NGram nGram = nGrams.get(i);
			
			if (nGram.getTotalResults() == 0)
				System.out.println(nGram+" "+urlResult);
			
			double v = scoreNGram(nGram);
			
			int d = i - last;						// distance between this position and the last position
			p = seq.size() - 1;						// set to previous position		
			if (seq.size() == 0) {					// if we're just starting, 
				sub.add(v, i);						//  add our value for our first match
				seq.add(sub);
			} else if (d < n)	{					// if the distance is less than n, this is an overlapping match
				sub = seq.get(p);					//  first, get the last value
				sub.add(v, i);						//	add this value to the sequence
				d--;								//	decrement d to start at the previous location
				while (d > 0) {							// now, fill in the gaps for all the ngrams between last and this one
					v = scoreNGram(nGrams.get(last+d));	// debug as this doesn't make sense for combined URLs
					sub.add(v, last+d);
					d--;
				}
			} else {								// not overlapping
				sub = seq.get(p);
				sub.addToScore(n-1);				//  so we add n-1 to the previous location in seq to approximate number of words in that subseq
				
				sub = new Sequence();
				sub.addToScore(-1.0*d + n);			//  then add a negative representing the number of words between this position and the last
				seq.add(sub);
				
				sub = new Sequence();				//  finally add a positive to start a new matching sequence at this position
				sub.add(v, i);
				seq.add(sub);
			}
			last = i;
		}					
		if (seq.size() > 0) {					// if we have a sequence of any size
			p = seq.size() - 1;					//  set our position to the last position of the sequence
			seq.get(p).addToScore(n-1);			//  add n-1 to our last subseq of matches to approximate number of words
		}
	
		return seq;
	}

	private List<Sequence> findSubsequences (List<Sequence> seq) {		
		double next = 0;
		double current = 0;
		double score = 0;

		Sequence sub = new Sequence();
		Sequence val = new Sequence();
		List<Sequence> subSeq = new LinkedList<>();
		Iterator<Sequence> it = seq.listIterator();
		while (it.hasNext()) {
			val = it.next();
			next = val.getScore();
			current += next;
			
			if (next == 0) {	// possible bug here if 0 is the last element
				it.remove();
				continue;
			}
			if (current > score) {
				score = current;							// new max
				sub.merge(val);
				it.remove();
			}
			if (current <= 0 || !it.hasNext()) {			// end of seq
				if (next < 0) it.remove();					// remove negatives at edge of sequence				
				if (score > 0) {
					sub.setScore(score);
					subSeq.add(sub);
				}
				sub = new Sequence();
				score = current = 0;	
				if (!it.hasNext()) it = seq.listIterator();	// start over
			}
		}	

		return subSeq;
	}

	private int scoreSequence (List<Sequence> seq) {	
		// now score our document...
		//
		// f is our power factor; we raise continuous segments to this power
		// 	then add them all together for a given url
		//	then root by this power, this gives more weight to continuous segments
		double f = 4.0;
		
		double next = 0;
		double current = 0;
		double score = 0;
		double totalScore = 0;
		int finalScore = 0;
			
		Iterator<Sequence> it = seq.listIterator();
		while (it.hasNext()) {					
			next = it.next().getScore();
			current += next;
			
			if (next == 0) {
				it.remove();
				continue;
			}
			if (current > score) {
				score = current;							// new max
				it.remove();
			}
			if (current <= 0 || !it.hasNext()) {			// end of seq
				if (next < 0) it.remove();					// remove negatives at edge of sequence				
				if (score >= wordThreshold)
					totalScore += Math.pow(score, f);
				score = current = 0;			
				if (!it.hasNext()) it = seq.listIterator();	// start over
			}
		}
		finalScore = (int)Math.pow(totalScore, 1/f);

		return finalScore;
	}

	// Find all subsequences above our minscore
	//
	public Map<Sequence, Sequence> findBestSubsequences (SourceText sourceText, Collection<URLResult> urlResults) {
		Map<Sequence, Sequence> subSequences = new HashMap<>();
		
		for (URLResult urlResult: urlResults) {
			List<Sequence> urlSequence = buildSequence(sourceText, urlResult);
			List<Sequence> urlSubsequences = findSubsequences(urlSequence);
		
			for (Sequence urlSubsequence : urlSubsequences) {
				if (urlSubsequence.getScore() < minScore) {
					continue;
				}
				Sequence subSequence = subSequences.get(urlSubsequence);
				if (subSequence == null)
					subSequence = urlSubsequence;
				subSequence.addResult(urlResult);
				subSequences.put(subSequence, subSequence);
			}
		}
		return subSequences;
	}

	public Map<OffsetAttribute, Sequence> convertToOffsets (SourceText sourceText, Map<Sequence, Sequence> subSequences) {
		Map<OffsetAttribute, Sequence> bestMatches = new HashMap<>();
		List<OffsetAttribute> offsets = sourceText.getOffsets();

		for (Sequence subSequence : subSequences.keySet()) {
			Set<URLResult> urlMatches = subSequence.getResults();

			for (Integer i : subSequence.getPositions()) {
				OffsetAttribute offset = offsets.get(i);
				Sequence currentMatch = bestMatches.get(offset);
				if (currentMatch != null) {
					if (currentMatch.getScore() > subSequence.getScore())
						subSequence = currentMatch;
				}
				bestMatches.put(offset, subSequence);
			}
		}
		return bestMatches;
	}
	
	public Map<OffsetAttribute, URLResult> convertToOffsetsAndURLs (SourceText sourceText, Map<Sequence, Sequence> subSequences) {
		Map<OffsetAttribute, URLResult> bestMatches = new HashMap<>();
		List<OffsetAttribute> offsets = sourceText.getOffsets();

		for (Sequence subSequence : subSequences.keySet()) {
			Set<URLResult> urlMatches = subSequence.getResults();

			for (Integer i : subSequence.getPositions()) {
				OffsetAttribute offset = offsets.get(i);
				URLResult bestMatch = subSequence.getBestResult();				
				URLResult currentMatch = bestMatches.get(offset);
				if (currentMatch != null) {
					if (currentMatch.size() > bestMatch.size())
						bestMatch = currentMatch;
				}
				bestMatches.put(offset, bestMatch);
			}
		}
		return bestMatches;
	}
	
	// Mark all the best Sequences in the set of NGrams
	//
	public String markBestMatches (SourceText sourceText, Map<OffsetAttribute, Sequence> bestMatches) {
		String text = sourceText.toString();
		List<OffsetAttribute> offsets = sourceText.getOffsets();		
		
		int i = 0;
		int last = 0;
		String lastURL = "";
		StringBuilder builder = new StringBuilder();
		Map<String, String> colorMap = new HashMap<>();
		for (OffsetAttribute offset : offsets) {		
			Sequence subSequence = bestMatches.get(offset);			
			if (subSequence == null) continue;
			
			int start = offset.startOffset();
			int end = offset.endOffset();			
			
			URLResult urlResult = subSequence.getBestResult();
			String url = urlResult.toString();
			int score = (int)subSequence.getScore();
			int total = subSequence.size();
					
			if (start == end) continue; //debug - why?
			
			if ((start >= last) || (start < last && url != lastURL)) {						// add all our text since the last match
				builder.append("</a></span>");
				
				if (start > last) builder.append(text.substring(last, start));
				else start = last;
				
				String color = colorMap.get(url);											// get this URLs color, if it has one
				if (color == null) {
					color = randomColor();													// else, generate a random HTML color
					colorMap.put(url, color);												// and save it for later
				}
				String urls = "";
				for (URLResult result : subSequence.getResults())
					urls += result.toString()+"\n";
				String href = 
					"<span style='background-color:"+color+"'>"
					+"<a href="+url															// build an HTML link
					+" style='font-weight: bold; text-decoration: none'"					// configure style
					+" target=_blank"														// open in separate window
					+" title='Score of "+score+" with "+total+" matches\n"+urls+"'>";		// add info in alt text
				builder.append(href);														// append it to our builder
			} else start = last;
			builder.append(text.substring(start, end));
			last = end;
			lastURL = url;
			i++;
		}
		builder.append("</a></span>"+text.substring(last));

		return builder.toString();
	}
	
	// Find the best URL for each ngram's precise document location
	// This will prefer the URL matched by the previous ngram, if this
	// ngram shares that same URL
	//
	public Map<OffsetAttribute, URLResult> findBestURLMatches (SourceText sourceText) {
		Map<OffsetAttribute, URLResult> bestMatches = new HashMap<>();
		List<NGram> nGrams = sourceText.getNGramsByOrder();
		List<OffsetAttribute> offsets = sourceText.getOffsets();

		URLResult lastMatch = new URLResult("");
		for (int i = 0; i < nGrams.size(); i++) {
			NGram nGram = nGrams.get(i);
			Set<URLResult> urlResults = nGram.getResults();
			
			// check if this ngram has any results
			if (urlResults.size() == 0)
				continue;

			// if it does, find the most promising result
			URLResult bestMatch = null;
			if (urlResults.contains(lastMatch))
					bestMatch = lastMatch;
			else	bestMatch = nGram.getBestResult();

			if (bestMatch.getScore() >= minScore) {				// there may be no urls which exceed the minimum score
				lastMatch = bestMatch;							// remember this match for the next ngram
				bestMatches.put(offsets.get(i), bestMatch);		// and add the offsets to the map with this url
			}			
		}
		return bestMatches;
	}	
	
	// Find the highest ranked URL for each NGram's precise document location
	// There should be a way to prefer URLs around this ngram to make things less "lumpy"
	//
	public Map<OffsetAttribute, URLResult> findBestURLMatchesOld (SourceText sourceText) {
		Map<OffsetAttribute, URLResult> bestMatches = new HashMap<>();
		Collection<NGram> nGrams = sourceText.getNGrams().values();
		
		for (NGram nGram : nGrams) {
			Set<URLResult> urlResults = nGram.getResults();
			URLResult bestMatch = null;
			
			int topScore = minScore;								// skip urls with scores below the minimum score
			for (URLResult urlResult : nGram.getResults()) {
				int score = (int)urlResult.getScore();
				if (score > topScore) {
					bestMatch = urlResult;
					topScore = score;
				}
			}
			if (bestMatch != null) {								// there may be no urls which exceed the minimum score
				nGram.setBestResult(bestMatch);						// if there are, set the best match for this ngram
				for (OffsetAttribute offset : nGram.getOffsets()) 	// and add the offsets to the map with this url
					bestMatches.put(offset, bestMatch);		
			}
		}
		return bestMatches;
	}
	
	// Randomly generates HTML color codes for darker colors.
	private static String randomColor(){
		String code = "#"+Integer.toHexString((int)(Math.random()*4+12))+Integer.toHexString((int)(Math.random()*4+12))+Integer.toHexString((int)(Math.random()*4+12));

		return code;
	}
	
	// Mark all the best URLs in the set of NGrams
	//
	public String markBestURLMatches (SourceText sourceText, Map<OffsetAttribute, URLResult> bestMatches) {
		String text = sourceText.toString();
		List<OffsetAttribute> offsets = sourceText.getOffsets();		
/*	Old way		
		List<OffsetAttribute> offsets = new ArrayList<>(bestMatches.keySet());
		
		// Sort our list of offsets by start position
		//	 using an anonymous comparator
		Collections.sort(offsets, 
			new Comparator<OffsetAttribute>() {
				public int compare (OffsetAttribute offset1, OffsetAttribute offset2) {
					return offset1.startOffset() - offset2.startOffset();
				}
			}
		);
*/		
		int i = 0;
		int last = 0;
		String lastURL = "";
		StringBuilder builder = new StringBuilder();
		Map<String, String> colorMap = new HashMap<>();
		for (OffsetAttribute offset : offsets) {
			int start = offset.startOffset();
			int end = offset.endOffset();
			
			URLResult urlResult = bestMatches.get(offset);			
			if (urlResult == null) continue;
			
			String url = urlResult.toString();
			int score = (int)urlResult.getScore();
			int total = urlResult.getMatches().size();
					
			if (start == end) continue; //debug - why?
			
			if ((start >= last) || (start < last && url != lastURL)) {						// add all our text since the last match
				builder.append("</a></span>");
				
				if (start > last) builder.append(text.substring(last, start));
				else start = last;
				
				String color = colorMap.get(url);											// get this URLs color, if it has one
				if (color == null) {
					color = randomColor();													// else, generate a random HTML color
					colorMap.put(url, color);												// and save it for later
				}
				String href = 
					"<span style='background-color:"+color+"'>"
					+"<a href="+url															// build an HTML link
					+" style='font-weight: bold; text-decoration: none'"					// configure style
					+" target=_blank title='Score of "+score+" with "+total+" matches'>";	// open in separate window
				builder.append(href);														// append it to our builder
			} else start = last;
			builder.append(text.substring(start, end));
			last = end;
			lastURL = url;
			i++;
		}
		builder.append("</a></span>"+text.substring(last));

		return builder.toString();
	}

	// scoring crap
	
	// This is for per-URL scoring
	private int scoreResult (SourceText sourceText, URLResult urlResult) {
		List<Sequence> sequence = buildSequence(sourceText, urlResult);
		int score = scoreSequence(sequence);
		
		return score;
	}

	// public methods for getting results and manipulating URLs
	//
	public void scoreResults (SourceText sourceText, Collection<URLResult> urlResults) {
		System.out.println("Scoring "+urlResults.size()+" distinct URLs...");	
		for (URLResult urlResult : urlResults) {
			urlResult.setPercent(getPercentMatch(sourceText, urlResult));		
			urlResult.setScore(scoreResult(sourceText, urlResult));
		}
	}	
	
	public URLResult getSingleResult (SourceText sourceText, Collection<URLResult> urlResults) {
		URLResult urlCombo = combine(urlResults);
		urlCombo.setPercent(getPercentMatch(sourceText, urlCombo));		
		
		return urlCombo;
	}
	
	// static method to combine a set of results into a single result
	// can be a blank URL or a provided URL
	//
	public static URLResult combine (Collection<URLResult> urlResults) {
		return combine("", urlResults);		// no particular site
	}
	
	public static URLResult combine (String url, Collection<URLResult> urlResults) {
		URLResult combinedResult = new URLResult(url);
		
		for (URLResult urlResult : urlResults) {
			combinedResult.add(urlResult.getMatches());
			combinedResult.addResult(urlResult);
		}
		
		return combinedResult;
	}	
	
	//	
	
	
	// not really used
	
	// Output all our unique and interesting URLs, along with the score and total number of matches
	//
	public void output (Collection<URLResult> urlResults, String root) throws IOException {
		String path = root+".csv";
		
		String topURL = "";
		Set<Integer> topURLMatches = new TreeSet<>();
	
		PrintWriter writer = new PrintWriter(path, "UTF-8");
		
		int total = 0;	// total number of URLs worth processing
		for (URLResult urlResult : urlResults) {
			String url = urlResult.toString();
			Set<Integer> positions = urlResult.getPositions();
			
			if (positions.size() < urlThreshold)
				continue;

			total++;
			if (positions.size() > topURLMatches.size()) {
				topURLMatches = positions;
				topURL = url;
			}
			writer.println("\""+urlResult.getScore()+"\",\""+positions.size()+"\",\""+positions+"\",\""+url+"\"");
		}
		writer.close();

		System.out.println("Processing "+total+" significant URLs...");
		System.out.println("Results output to "+path+".");
		System.out.println("Most likely URL with "+topURLMatches.size()+" matches:");
		System.out.println(topURL);
	}	
	
	// Mark all the word matches between the original text and the passed list of ngrams
	//
	public String markMatches (SourceText sourceText, Set<NGram> matches) {
		String text = sourceText.toString();	
		List<Integer> starts = new ArrayList<>();
		List<Integer> ends = new ArrayList<>();
		
		for (NGram nGram : matches) {
			Set<OffsetAttribute> offsets = nGram.getOffsets();
			for (OffsetAttribute offset : offsets) {
				starts.add(offset.startOffset());
				ends.add(offset.endOffset());
			}
		}
		Collections.sort(starts);
		Collections.sort(ends);
		
		int last = 0;
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < starts.size(); i++) {
			int start = starts.get(i);
			int end = ends.get(i);
			
			if (start == end) continue;

			if (start < last) start = last;
			else if (start >= last) builder.append("</b>"+text.substring(last, start)+"<b>");
			builder.append(text.substring(start, end));
			last = end;
		}
		builder.append("</b>"+text.substring(last));

		return builder.toString();
	}	
}


