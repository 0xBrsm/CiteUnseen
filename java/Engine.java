/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 *	ALM in IT Thesis
 *	Overview
 *	<date>	  
 *
 *	Enum definition for various Search Engines
 *	and their approximate index sizes.
 *
 */

package citeunseen;

enum Engine {
	// approximate max of indexed documents
	// is found by searching for the letter 
	// "a" in each search engine
	//
	Bing	(4300000000L),
	FAROO	(2000000000L),
	Google	(3700000000L), 
	Yahoo	(4300000000L);

	private final long indexSize;
	
	private Engine (long indexSize) {
		this.indexSize = indexSize;
	}
	
	public long getIndexSize() {
		return indexSize;
	}
};