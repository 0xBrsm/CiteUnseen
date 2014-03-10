/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;

import java.util.Set;
 
 public interface TokenizedText {
 
	Set<String> getNGrams ();
	
	SourceFragment getOverlap (TokenizedText tokenizedText);
 }