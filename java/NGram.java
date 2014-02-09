/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */ 

package citeunseen;

import java.util.*;

import org.apache.lucene.analysis.tokenattributes.*;

public class NGram extends SourceFragment {
	private Set<OffsetAttribute> offsets;	// absolute char positions in the document
	private long totalResults;

	// Constructors
	//
	public NGram (String ngram) {
		super(ngram);
	}
	
	public NGram (String ngram, int position, OffsetAttribute offsetAttrib) {
		super(ngram);
		addPosition(position);
		
		offsets = new HashSet<OffsetAttribute>();
		OffsetAttribute offset = new OffsetAttributeImpl();
		offset.setOffset(offsetAttrib.startOffset(), offsetAttrib.endOffset());
		offsets.add(offset);
	}

	// Get and Set methods
	//
	public void add (int position, OffsetAttribute offset) {
		addPosition(position);
		offsets.add(offset);
	}
	
	public Set<OffsetAttribute> getOffsets () {
		return offsets;
	}
	
	public void setBestResult (URLResult urlResult) {
		super.setBestResult(urlResult);
		setScore(urlResult.getScore());
	}
	
	public int getBestScore () {
		return (int)getScore();
	}
	
	public long getTotalResults () {
		return totalResults;
	}

	public void setTotalResults (long r) {
		totalResults = r;
	}
}