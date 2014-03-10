/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */ 
 
package citeunseen;
 
import java.util.Set;
import java.util.HashSet; 
 
public class Sequence extends SourceFragment {
	private double score;
	private Set<String> urls;
	
	public Sequence (SourceText sourceText) {
		super(sourceText);
	}

	public void setScore (double score) {
		this.score = score;
	}
	
	public void addToScore (double v) {
		score += v;
	}

	public double score () {
		return score;
	}	
	
	public boolean add (double v, int p) {
		if (add(p)) {
			addToScore(v);
			return true;
		}
		return false;
	}
	
	@Override
	public String toString () {
		return positionsInOrder().toString();
	}
	
	// Compare to find the more significant sequence.
	//
	@Override
	public int compareTo (SourceFragment other) {
		if (other == null) return 1;
		if (other instanceof Sequence) {
			double thisScore = this.score();
			double otherScore = ((Sequence)other).score();
			if (thisScore != otherScore)
				return (int)(thisScore - otherScore);
		}
		return super.compareTo(other);
	}

	// Comparators - based on positions
	@Override
	public int hashCode () {
		return this.positions().hashCode();
	}
	
	@Override	
	public boolean equals (Object obj) {
		if (obj == null) return false;
		if (!(obj instanceof Sequence)) return false;
		if (!((Sequence)obj).positions().equals(this.positions())) return false;
		
		return true;
	}	
 }