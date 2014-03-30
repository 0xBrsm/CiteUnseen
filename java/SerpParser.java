/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;

public abstract class SerpParser {

	abstract SearchResult parse (String serp);
	abstract String parseError (String serp);

}