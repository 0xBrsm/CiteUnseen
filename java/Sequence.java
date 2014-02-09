/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */ 
 
 package citeunseen;
 
 import java.util.*;
 
 public class Sequence extends SourceFragment {

	public void add (double v, int p) {
		if (addPosition(p))
			addToScore(v);
	}

	@Override
	public String toString () {
		return getPositions().toString();
	}
	
	public boolean merge (Sequence s) {
		return addPositions(s.getPositions());
	}
 }