/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 *	Note: this class has a natural ordering that is inconsistent with equals.
 */ 

package citeunseen;

import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.List;

public class SourceFragment implements TokenizedText, Comparable<SourceFragment> {
	private final String label;
	private final SourceText sourceText;
	private Set<SourceFragment> matches;
	private Set<Integer> positions = new HashSet<>();
	
	// Static methods
	public static Set<SourceFragment> switchToMatches (Set<? extends SourceFragment> fragments) {
		Set<SourceFragment> matches = new HashSet<>();
		for (SourceFragment fragment : fragments)
			matches.addAll(fragment.matches());
		return matches;
	}	

	// Constructors
	public SourceFragment (SourceText sourceText) {
		this("", sourceText);
	}
	
	public SourceFragment (String label, SourceText sourceText) {
		this.label = label;
		this.sourceText = sourceText;
	}

	// Setters
	public boolean add (int position) {
		return this.positions.add(position);
	}

	public boolean add (Set<Integer> positions) {
		if (positions == null) return false;
		return this.positions.addAll(positions);	
	}
	
	public boolean add (SourceFragment fragment) {
		if (fragment == null) return false;
		if (matches == null) matches = new HashSet<>();
		return this.matches.add(fragment);
	}
	
	public boolean addAll (Set<? extends SourceFragment> fragments) {
		if (fragments == null) return false;	
		if (matches == null) matches = new HashSet<>();
		return this.matches.addAll(fragments);		
	}
	
	// Getters
	public Set<Integer> positions () {
		return positions;
	}
	
	public Set<Integer> positionsInOrder () {
		return new TreeSet<>(positions);
	}
	
	public Set<SourceFragment> matches () {
		return matches == null ? matches = new HashSet<>() : matches;
	}
	
	public SourceFragment getBestMatch () {
		return Collections.max(matches);
	}
	
	public int size () {
		return positions.size();
	}
	
	public String toString () {
		return label;
	}
	
	public SourceText getSourceText () {
		return sourceText;
	}
	
	// Get the similarity of this fragment with its source
	//
	public double similarity () {
		return sourceText.getSimilarity(this);
	}
	
	// Get a text representation of this source fragment
	//
	public String getAsText () {
		return sourceText.getOverlapAsText(this);
	}
	
	@Override
	public Set<String> getNGrams () {
		Set<String> ngrams = new HashSet<>();	
		for (Integer position : positions)
			ngrams.add(sourceText.get(position));		
		return ngrams;
	}	
	
	// Compare the passed fragment with this source text and return a fragment
	// representing the overlap
	//
	@Override
	public SourceFragment getOverlap (TokenizedText tokenizedText) {
		Set<String> ngrams = tokenizedText.getNGrams();
		SourceFragment overlap = new SourceFragment(sourceText);
		for (String ngram : ngrams)
			overlap.add(sourceText.locate(ngram));
		return overlap;
	}

	// Compare to find the larger fragment.
	//
	@Override
	public int compareTo (SourceFragment other) {
		if (other == null) return 1;
		return this.size() - other.size();
	}
	
	// Comparators - based on label only
	@Override
	public int hashCode () {
		return this.toString().hashCode();
	}
	
	@Override	
	public boolean equals (Object obj) {
		if (obj == null) return false;
		if (!(obj instanceof SourceFragment)) return false;
		if (!((SourceFragment)obj).toString().equals(this.toString())) return false;
		
		return true;
	}
}