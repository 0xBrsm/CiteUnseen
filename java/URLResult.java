/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */

package citeunseen;
 
import java.net.URL;

public class URLResult extends SourceFragment {
	private final String host;
			
	// Constructor
	public URLResult (String url, SourceText sourceText) {
		super(url, sourceText);
		this.host = parseHost(url);
	}
	
	// Get the root URL of this result
	public String getHost () {
		return host;
	}
	
	// Get this result as a URL object
	public URL toURL () throws Exception {	
		return (new URL(toString()));
	}
	
	// Find the root URL of the given URL
	public static String parseHost (String url) {
		if (url == null || url == "") return "";

		int start = url.indexOf("//");
		if (start == -1) 	start = 0;
		else				start += 2;

		int end = url.indexOf('/', start);
		end = end >= 0 ? end : url.length();

		return url.substring(start, end);
	}
}
