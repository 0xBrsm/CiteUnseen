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
	private int lengthThreshold = 1;		// minimum number of occurrences for a URL to be considered worth checking
	private int minScore = 1;				// minimum score for URL match sequences to be considered significant/displayed to user
	private boolean scoreByRarity = true;	// whether to weight the worth of matches by the number of results each returned in the search
	private String scoringMethod = "idf";	// default scoring method
	private boolean fillGaps = true;		// I don't even know what this does yet

	// Remember the search results and url results once processing has begun
	private Map<String, SearchResult> searchResults = new HashMap<>();
	private Set<URLResult> urlResults = new HashSet<>();
	
	// Setters for config parameters once defaults are established
	//
	public void setURLThreshold (int urlThreshold) {
		this.lengthThreshold = urlThreshold;
	}
	
	public void setMinimumScore (int minScore) {
		this.minScore = minScore;
	}
	
	public void setScoreByRarity (boolean scoreByRarity) {
		this.scoreByRarity = scoreByRarity;
	}
	
	public void setScoringMethod (String method) {
		this.scoringMethod = method;
	}
	
	public Set<URLResult> urlResults () {
		return urlResults;
	}

	//===========================================================//	
	// Take our source text and search results as input.
	// Return the a map between document positions and sequences.
	//===========================================================//
	public Set<Sequence> process (Map<String, SearchResult> searchResults, SourceText sourceText) {
		this.searchResults = searchResults;

		Set<URLResult> urlResults = getByURL(searchResults, sourceText);	
		Set<Sequence> sequences = findSequences(urlResults);
		
//		Map<OffsetAttribute, Sequence> offsets = adjustOffsets(convertToOffsets(sequences));
//		Map<Integer, Sequence> seqByPosition = getByPosition(sequences);

		this.urlResults = urlResults;
	
		return sequences;
	}
	//===========================================================//	
	
	//===========================================================//	
	// Take our search results and create one big master list of 
	// unique URLs and the ngrams in the document which matched 
	// with that URL.
	//
	private Set<URLResult> getByURL (Map<String, SearchResult> searchResults, SourceText sourceText) {
		Map<String, URLResult> urlResults = new HashMap<>();
		for (Map.Entry<String, SearchResult> entry : searchResults.entrySet()) {
			String ngram = entry.getKey();
			SearchResult searchResult = entry.getValue();
			
			Set<Integer> positions = sourceText.locate(ngram);
			for (String url : searchResult.urls()) {
				URLResult urlResult = urlResults.get(url);
				if (urlResult == null) {
					urlResult = new URLResult(url, sourceText);
					urlResults.put(url, urlResult);
				}
				urlResult.add(positions);
			}
		}
		return new HashSet<URLResult>(urlResults.values());
	}
	//===========================================================//		
	
	//===========================================================//
	// Find all subsequences of minscore or higher and the urls
	// that match each of them.
	//
	public Set<Sequence> findSequences (SourceFragment fragment) {
		return findSequences(Collections.singleton(fragment));
	}
	public Set<Sequence> findSequences (Set<? extends SourceFragment> fragments) {
		Map<Sequence, Sequence> subSequences = new HashMap<>();
		
		for (SourceFragment fragment : fragments) {
			List<Sequence> fragmentByWords = buildNumericSequence(fragment);
			Set<Sequence> fragmentSubsequences = findSubsequences(fragmentByWords);
						
			for (Sequence fragmentSubsequence : fragmentSubsequences) {
				Sequence subSequence = subSequences.get(fragmentSubsequence);
				if (subSequence == null) {
					subSequence = fragmentSubsequence;		
					subSequences.put(subSequence, subSequence);
				}
				subSequence.add(fragment);
				fragment.add(subSequence);
			}
		}
		return subSequences.keySet();
	}	
	
	// Build a word value sequence from the matches of a particular URL
	// For this, each Sequence object is actually just a value of the 
	// number of consecutive words and the positions
	//
	private List<Sequence> buildNumericSequence (SourceFragment fragment) {
		SourceText sourceText = fragment.getSourceText();
		Set<Integer> positions = fragment.positionsInOrder();
		List<Sequence> seq = new LinkedList<>();
		
		// skip fragments that have too few positions
		if (positions.size() < lengthThreshold) return seq;
		
		// Build word sequence array
		// Positives are number of matches in a row, negatives are number of mismatches in a row
		//
		Sequence sub = new Sequence(sourceText);
		int n = sourceText.getN();
		int p = 0;
		int last = 0;
		for (Integer i : positions) {
			String ngram = sourceText.get(i);
			double v = scoreNGram(ngram);
			
			int d = i - last;						// distance between this position and the last position
			p = seq.size() - 1;						// set to previous position		
			if (seq.size() == 0) {					// if we're just starting, 
				sub.add(v, i);						//  add our value for our first match
				seq.add(sub);
			} else if (d < n)	{					// if the distance is less than n, this is an overlapping match
				sub = seq.get(p);					//  first, get the last value
				sub.add(v, i);						//	add this value to the sequence
				d--;								//	decrement d to start at the previous location
				if (fillGaps) while (d > 0) {		// now, fill in the gaps for all the ngrams between last and this one
					ngram = sourceText.get(last+d);	// I'm not really sure what effect this has, though
					v = scoreNGram(ngram);			
					sub.add(v, last+d);
					d--;
				}
			} else {								// not overlapping
				sub = seq.get(p);
				sub.addToScore(n-1);				//  so we add n-1 to the previous location in seq to approximate number of words in that subseq
				
				sub = new Sequence(sourceText);
				sub.addToScore(-0.5*d + n);			//  then add a negative representing the number of words between this position and the last
				seq.add(sub);
				
				sub = new Sequence(sourceText);		//  finally add a positive to start a new matching sequence at this position
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
	
	// use the total number of results for this ngram to scale it
	//  the more results, the less likely we care about this match, so it should count for less
	//  the fewer results, the more interesting this match is, so it should count for more
	//
	private double scoreNGram (String ngram) {		
		double v = 1.0;						// default score per match
		
		if (scoreByRarity) {
			SearchResult searchResult = searchResults.get(ngram);
			if (searchResult != null)
				v = searchResult.getRelativeValue(scoringMethod);		
		}	
		return v;
	}	

	// Find all the subsequences in a sequence
	//
	private Set<Sequence> findSubsequences (List<Sequence> seq) {		
		double next = 0;
		double current = 0;
		double score = 0;

		Sequence sub = null;
		Sequence val = null;
		Set<Sequence> subSeq = new HashSet<>();
		Iterator<Sequence> it = seq.listIterator();
		while (it.hasNext()) {
			val = it.next();
			next = val.score();
			current += next;

			if (sub == null)
				sub = new Sequence(val.getSourceText());			
			
			if (next == 0) {
				it.remove();
			}
			if (current > score) {
				score = current;			// new max
				sub.add(val.positions());	// add these positions
				it.remove();
			}
			if (current <= 0 || !it.hasNext()) {		// end of seq
				if (next < 0) it.remove();				// remove negatives at edge of sequence				
				if (score >= minScore) {				// if this subsequence score is at least minScore
					sub.setScore(score);				//  set the score for this subsequence
					subSeq.add(sub);					//  and remember it
				}
				sub = new Sequence(val.getSourceText());
				score = current = 0;	
				if (!it.hasNext()) it = seq.listIterator();	// start over
			}
		}	
		return subSeq;
	}
	//===========================================================//	

	// OLD	

	//===========================================================//
	// Match each sequence to the offsets in the original source
	// text.
	//
/*	private Map<OffsetAttribute, Sequence> convertToOffsets (Set<Sequence> sequences) {
		Map<OffsetAttribute, Sequence> offsets = new HashMap<>();
		
		for (Sequence sequence : sequences) {
			int last = -1;
			int offsetStart = -1;
			int offsetEnd = -1;	
			
			for (Integer position : sequence.positionsInOrder()) {
				if (offsetStart < 0)
					offsetStart = sourceText.startOffset(position);
				else if (position - last > sourceText.getN()) {
					offsetEnd = sourceText.endOffset(last);
					OffsetAttribute offset = new OffsetAttributeImpl();
					offset.setOffset(offsetStart, offsetEnd);
					offsets.put(offset, sequence);
					
					offsetStart = sourceText.startOffset(position);
				}
				last = position;
			}
			if (last >= 0) {
				offsetEnd = sourceText.endOffset(last);
				OffsetAttribute offset = new OffsetAttributeImpl();
				offset.setOffset(offsetStart, offsetEnd);
				offsets.put(offset, sequence);
			}
		}
		return offsets;
	}

	//===========================================================//
	
	//===========================================================//
	// Adjust the start and end of each sequence as necessary to
	// avoid overlaps.
	//
	private static Map<OffsetAttribute, Sequence> adjustOffsets (Map<OffsetAttribute, Sequence> offsetsMap) {	
		// Sort our list of offsets by start position
		//	 using an anonymous comparator
		TreeMap<OffsetAttribute, Sequence> offsets = new TreeMap<>( 
			new Comparator<OffsetAttribute>() {
				public int compare (OffsetAttribute offset1, OffsetAttribute offset2) {
					return offset1.startOffset() - offset2.startOffset();
				}
			}
		);
		
		offsets.putAll(offsetsMap);
		
		int last = 0;
		Sequence lastSequence = null;
		OffsetAttribute lastOffset = null;
		
		Map<OffsetAttribute, Sequence> adjustedOffsets = new TreeMap<>(offsets.comparator());
		for (Map.Entry<OffsetAttribute, Sequence> entry : offsets.entrySet()) {
			OffsetAttribute offset = entry.getKey();
			Sequence sequence = entry.getValue();

			int start = offset.startOffset();
			int end = offset.endOffset();
			
			if (start < last) {
				if (lastSequence.score() > sequence.score())
					start = last;
				else if (lastSequence.score() == sequence.score() && lastSequence.size() > sequence.size())
					start = last;
				else last = start;
				
				int lastStart = lastOffset.startOffset();				
				if (lastStart >= last)						// the sequence has been totally covered
					adjustedOffsets.remove(lastOffset);		// remove it from the list
				else lastOffset.setOffset(lastStart, last);
				
				if (start < end) {		
					offset.setOffset(start, end);
					adjustedOffsets.put(offset, sequence);
				}
			} else adjustedOffsets.put(offset, sequence);
			
			lastSequence = sequence;
			lastOffset = offset;
			last = end;
		}
StringBuilder builder = new StringBuilder();
for (OffsetAttribute offset : offsets.keySet()) {
	builder.append(offset.startOffset()+","+offset.endOffset());
	builder.append(System.lineSeparator());
}
Dev.output(builder, "output.csv");
		
		return adjustedOffsets;
	}
	//===========================================================//
	
/*	private Map<Integer, Sequence> getByPosition (Set<Sequence> subSequences) {
		Map<Integer, Sequence> bestMatches = new HashMap<>();
		
		for (Sequence subSequence : subSequences) {
			for (Integer position : subSequence.positions()) {
				Sequence currentMatch = bestMatches.get(position);
				
				boolean isNew = false;
				if (currentMatch == null) isNew = true;
				else {
					double currentScore = currentMatch.score();
					double subScore = subSequence.score();
					
					if (currentScore < subScore)
						isNew = true;
					else if (currentScore == subScore && currentMatch.size() < subSequence.size())
						isNew = true;
				}
				if (isNew) bestMatches.put(position, subSequence);
			}	
		}
		return bestMatches;
	}
	
	// Convert the ngram positions back to word strings
	//
*/
	//===========================================================//		
}


