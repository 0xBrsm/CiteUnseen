/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */ 

package citeunseen;

import java.util.*;

public abstract class SourceFragment {
	private final String label;
	private double score;
	private double percent;
	private URLResult bestResult;
	private Set<URLResult> urlResults = new HashSet<>();
	private Set<Integer> positions = new HashSet<>();

	// Constructors
	public SourceFragment () {
		this("");
	}
	
	public SourceFragment (String label) {
		this.label = label;
	}
	
	// Setters	
	public void setScore (double score) {
		this.score = score;
	}
	
	public void addToScore (double v) {
		setScore(getScore()+v);
	}
	
	public void setPercent (double percent) {
		this.percent = percent;
	}
	
	public void setBestResult (URLResult urlResult) {
		bestResult = urlResult;
	}

	public boolean addResult (URLResult urlResult) {
		if (bestResult == null)
			bestResult = urlResult;
		else if (urlResult.getPositions().size() > bestResult.getPositions().size())
			bestResult = urlResult;
			
		return urlResults.add(urlResult);
	}
	
	public boolean addResults (Collection<URLResult> urlResults) {
		boolean success = false;
		for (URLResult urlResult : urlResults)
			if (addResult(urlResult)) success = true;
		return success;
	}	
	
	public boolean addPosition (int position) {
		return positions.add(position);
	}

	public boolean addPositions (Set<Integer> positions) {
		return this.positions.addAll(positions);
	}
	
	// Getters
	public String toString () {
		return label;
	}
	
	public double getScore () {
		return score;
	}
	
	public double getPercent () {
		return percent;
	}
	
	public URLResult getBestResult () {
		return bestResult;
	}
	
	public Set<URLResult> getResults () {
		return urlResults;
	}
	
	public Set<Integer> getPositions () {
		return new TreeSet<>(positions);
	}
	
	public int size () {
		return positions.size();
	}

	// Comparators
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