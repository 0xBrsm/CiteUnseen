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
	private int minScore = 8;				// minimum score for URL match sequences to be considered significant/displayed to user
	private boolean scoreByRarity = true;	// whether to weight the worth of matches by the number of results each returned in the search
	private boolean weightGaps = false;		// flat score for gaps, rather than log probability...this helps performance A LOT	
	private String scoringMethod = "idf";	// default scoring method
	private double weightFactor = 1.0;		// weighting factor
	private boolean disabled = false;		// if advanced processing is enabled or disabled

	// Remember the search results once processing has begun
	// This is only for later scoring
	private Map<String, SearchResult> searchResults;
	
	// Factory method?
	//
	public static Processor build () {
		return new Processor();
	}	

	//
	// Constructors
	//
	public Processor () { }
	public Processor (Processor oldp) {
		this.setMinimumScore(oldp.minimumScore());
		this.setScoreByRarity(oldp.scoreByRarity());
		this.setScoreGapsByRarity(oldp.scoreGapsByRarity());
		this.setScoringMethod(oldp.scoringMethod());
		this.setWeightFactor(oldp.scoringWeightFactor());
		if (oldp.disabled()) this.disable();
	}

	//
	// Copy method
	//
	public Processor copy () {
		return new Processor(this);
	}
	
	//
	// Setters/getters
	//
	
	// Set/get the base score
	//
	public Processor setMinimumScore (int minScore) {
		this.minScore = minScore;
		return this;
	}
	public int minimumScore () {
		return minScore;
	}

	// Set score by rarity options, all or individually
	//	
	public Processor setScoreByRarity (boolean scoreByRarity) {
		this.scoreByRarity = scoreByRarity;
		return this;
	}
	public Processor setScoreByRarity (boolean scoreByRarity, boolean weightGaps) {
		this.scoreByRarity = scoreByRarity;
		this.weightGaps = weightGaps;
		return this;
	}
	public Processor setScoreByRarity (boolean scoreByRarity, boolean weightGaps, double weightFactor) {
		this.scoreByRarity = scoreByRarity;
		this.weightGaps = weightGaps;
		this.weightFactor = weightFactor;		
		return this;
	}
	public boolean scoreByRarity () {
		return scoreByRarity;
	}
	
	public Processor setScoreGapsByRarity (boolean weightGaps) {
		this.weightGaps = weightGaps;
		return this;
	}
	public boolean scoreGapsByRarity () {
		return weightGaps;
	}
	
	public Processor setScoringMethod (String method) {
		this.scoringMethod = method;
		return this;
	}
	public String scoringMethod () {
		return scoringMethod;
	}
	
	public Processor setWeightFactor (double weightFactor) {
		this.weightFactor = weightFactor;
		return this;
	}
	public double scoringWeightFactor () {
		return weightFactor;
	}

	// Settings for enabling/disabling advanced processing
	//
	public Processor enable () {
		disabled = false;
		return this;
	}
	public Processor disable () {
		disabled = true;
		return this;
	}
	public boolean disabled () {
		return disabled;
	}

	//===========================================================//	
	// Take our source text and search results as input.
	// Return the a map between document positions and sequences.
	//===========================================================//
	public Set<SourceFragment> process (Map<String, SearchResult> searchResults, SourceText sourceText) {
		// Remember results for scoreNGram access
		if (scoreByRarity && !disabled)
			this.searchResults = searchResults;
		
		Set<SourceFragment> fragments = new HashSet<>();	
		Set<URLResult> urlResults = getByURL(searchResults, sourceText);
		if (disabled) {
			fragments.addAll(urlResults);
			return fragments;
		}
		Set<Sequence> sequences = findSequences(urlResults);
		this.searchResults = null; // release memory
		fragments.addAll(sequences);
		
		return fragments;
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
		List<Sequence> seq = new ArrayList<>();
		
		// Build word sequence array
		// Positives are number of matches in a row, negatives are number of mismatches in a row
		//
		Sequence sub = new Sequence(sourceText);
		int n = sourceText.getN();
		int p = 0;
		int last = 0;
		for (Integer i : positions) {
			String ngram = sourceText.get(i);
			double v = scoreNGram(ngram) * weightFactor;
			
			int gap = i - last - 1;					// gap size between this position and the last position
			p = seq.size() - 1;						// set to previous position		
			if (seq.size() == 0) {					// if we're just starting, 
				sub.add(v, i);						//  add our value for our first match
				seq.add(sub);
			} else if (gap == 0) {					// if no gap, this is a consecutive match
				sub = seq.get(p);					//  first, get the last value
				sub.add(v, i);						//	add this value to the sequence
			} else {								// not consecutive
				sub = seq.get(p);
				sub.addToScore(n-1);				//  so we add n-1 to the previous location in seq to approximate number of words in that subseq				
				sub = new Sequence(sourceText);		//  and start a new sequence for the gap

				double penalty = 0;
				if (weightGaps) {									// if we want to weight gaps
					while (gap > 0) {								// now, fill in the gaps for all the missing ngrams between last and this one
						String missed = sourceText.get(last+gap);	// retrieve the ngram at this position
						penalty -= scoreNGram(missed);				// get the penalty for missing this ngram
						gap--;
					}
				} else penalty = -1 * gap;							// logs are VERY expensive, so maybe we skip that
				penalty = penalty > -1*n ? 0 : penalty + n-1;		// ensure a penalty never ends up positive
				sub.addToScore(penalty);				
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
		// default score per match	
		double v = 1.0;
		
		if (scoreByRarity) {
			SearchResult searchResult = searchResults.get(ngram);
			if (searchResult != null)
				v = searchResult.getRelativeValue(scoringMethod);		
		}	
		return v;
	}	

	// Find all the subsequences in a sequence
	//
	private Set<Sequence> findSubsequences (List<Sequence> values) {
		double current = 0;
		double score = 0;
		int end = 0;
		
		SourceText source = values.get(0).getSourceText();
		Sequence sequence = new Sequence(source);
		Set<Integer> positions = new HashSet<>();
		Set<Sequence> sequences = new HashSet<>();
		for (int i = 0; i < values.size(); i++) {
			Sequence value = values.get(i);
			current += value.score();
			
			if (value.score() > 0)							// If this is a positive value
				positions.addAll(value.positions());		//  remember all the positions
				
			if (current > sequence.score()) {				// If we have a new max
				sequence.add(positions);					//  add all the positions
				sequence.setScore(current);					//  and set the current score
				positions = new HashSet<>();				//  then start the positions anew
				end = i;									//  and start the next sequence at the end of this one
			}
			
			if (current <= 0 || i == values.size()-1) {		// If current drops below zero or the array is done
				if (sequence.score() > minScore) {			//	if this sequenc'es score is greater than our minimum
					sequences.add(sequence);				//   add it to the list of sequences
					i = end;								//   and go back to where this sequence ended
				}
				current = 0;
				sequence = new Sequence(source);
				positions = new HashSet<>();
			}
		}
		return sequences;
	}

	/*
	private Set<Sequence> findSubsequences (List<Sequence> seq) {
		double next = 0;
		double score = 0;		
		double current = 0;
		
		int start = 0;
		int end = 0;
		
		SourceText source = seq.get(0).getSourceText();
		Sequence sub = new Sequence(source);
		Sequence val = null;
		Set<Sequence> subSeq = new HashSet<>();
		for (int i = 0; i < seq.size(); i++) {			
			val = seq.get(i);
			next = val.score();
			
			if (current == 0 && next > 0)
				start = i;
				
			current += next;
	
			if (current > score) {
				score = current;
				end = i;
			}
			if (current <= 0 || i == seq.size()-1) {
				if (score >= minScore) {
					for (int s = start; s <= end; s++) {
						val = seq.get(s);
						if (val.score() > 0)
							sub.add(val.positions());
					}
					i = end;
					subSeq.add(sub);
					sub.setScore(score);					
					sub = new Sequence(source);
				}
				score = current = 0;
			}
		}
		return subSeq;
	}
	//===========================================================//	
/* Original busted
	
	private Set<Sequence> findSubsequences2 (List<Sequence> seq) {		
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
/*
	// OLD	
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
			
			int gap = i - last;						// distance between this position and the last position
			p = seq.size() - 1;						// set to previous position		
			if (seq.size() == 0) {					// if we're just starting, 
				sub.add(v, i);						//  add our value for our first match
				seq.add(sub);
			} else if (gap < n)	{					// if the gap is less than n, this is an overlapping match
				sub = seq.get(p);					//  first, get the last value
				sub.add(v, i);						//	add this value to the sequence
				gap--;								//	decrement d to start at the previous location
				if (fillGaps) while (gap > 0) {		// now, fill in the gaps for all the ngrams between last and this one
					ngram = sourceText.get(last+gap);	// I'm not really sure what effect this has, though
					v = scoreNGram(ngram);			// I think I'm better off not doing it because of accidental overlaps
					sub.add(v, last+gap);
					gap--;
				}
			} else {								// not overlapping
				sub = seq.get(p);
				sub.addToScore(n-1);				//  so we add n-1 to the previous location in seq to approximate number of words in that subseq
				
				sub = new Sequence(sourceText);
				sub.addToScore(-1.0*gap + n);		//  then add a negative representing the gap in words
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


