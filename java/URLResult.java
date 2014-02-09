package citeunseen;

import java.util.*;

public class URLResult extends SourceFragment {
	private Set<NGram> matches = new HashSet<>();
	private final String host;
			
	// Constructor and get/set methods
	public URLResult () {
		this("");
	}
	
	public URLResult (String url) {
		super(url);
		this.host = parseHost(url);
	}
	
	public String getHost () {
		return host;
	}
	
	public Set<NGram> getMatches () {
		return matches;
	}
	
	// Add the passed nGrams as well as their positions
	public boolean add (Set<NGram> nGrams) {
		boolean success = matches.addAll(nGrams);
		if (success)
			for (NGram nGram : nGrams) addPositions(nGram.getPositions());
		return success;
	}	
	
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
