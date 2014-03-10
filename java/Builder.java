/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 *	ALM in IT Thesis
 *	<date>	  
 *
 *	Builder utility class to build HTML pages
 *	for presentation of results
 *
 */
package citeunseen;
 
import java.io.*;
import java.util.*;
import java.net.URL;
import org.apache.tika.*;
import org.apache.lucene.analysis.tokenattributes.*;

public class Builder {
	//===========================================================//	
	// Format a page on the fly and save it
	//
 	public static void outputHTMLPage (String result, String markedText, String root) {
		String path = root+".html";
		StringBuilder builder = new StringBuilder();
		
		builder.append("<html><head>");
		builder.append("<meta http-equiv=\"Content-type\" content=\"text/html;charset=UTF-8\">");
		builder.append("</head><body>");
		builder.append(result);
		builder.append("<p><table bgcolor=#eeeeeeee width=80% align=center><tr><td><pre style=\"white-space: pre-wrap; font-family: Arial; font-size: 10pt;\">");
		builder.append(markedText);
		builder.append("</pre></td></tr></table>");
		builder.append("</body></html>");
		
		Dev.output(builder, path);
	}

	//===========================================================//	
	// Mark all the matching sections via HTML and return
	//
	public static Map<String, String> getHTMLResults (SourceFragment fragment) {
		return getHTMLResults(Collections.singleton(fragment), fragment.getSourceText());
	}
	public static Map<String, String> getHTMLResults (Set<? extends SourceFragment> fragments, SourceText sourceText) {
		// Get the overall stats on the matches
		Set<SourceFragment> urls = new HashSet<>();		
		for (SourceFragment fragment : fragments)
			urls.addAll(fragment.matches());
		double percent = sourceText.getPercentMatch(fragments);
		
		String summary = percent+"% similarity, with "+urls.size()+" notable URLs (duplicates hidden).";

		// Build a mapping of url to HTML tag
		Map<SourceFragment, String> startTags = tagAsSpans(fragments);
		Map<SourceFragment, String> endTags = tagWithMarker(fragments, "</a></span>");		

		// Mark all our matches, highlighted as URLs
		String markedText = markByChar(fragments, sourceText, startTags, endTags);

		// Results only have two values, don't waste space
		Map<String, String> results = new HashMap<>(2, 1.5f);
		
		results.put("summary", summary);
		results.put("markedText", markedText);
		
		return results;
	}

	// Randomly generates HTML color codes for darker colors.
	//
	private static String randomColor(){
		String code = "#"
						+Integer.toHexString((int)(Math.random()*4+12))
						+Integer.toHexString((int)(Math.random()*4+12))
						+Integer.toHexString((int)(Math.random()*4+12));
		return code;
	}
	
	// Build HTML href links for each URL
	//
	private static Map<SourceFragment, String> tagWithMarker (Set<? extends SourceFragment> fragments, String marker) {
		Map<SourceFragment, String> tags = new HashMap<>();
		for (SourceFragment fragment : fragments) {
			tags.put(fragment, marker);
		}
		return tags;
	}
	private static Map<SourceFragment, String> tagAsSpans (Set<? extends SourceFragment> fragments) {
		Map<SourceFragment, String> tags = new HashMap<>();
		Map<String, String> colorMap = new HashMap<>();	

		int id = 0;		
		for (SourceFragment fragment : fragments) {
			int total = fragment.size();
			int score = 0;
			String url = fragment.toString();
			String alt = url;

			String urls = "";
			for (SourceFragment match : fragment.matches())
				urls += match+"\n";
		
			// hack
			if (fragment instanceof Sequence) {
				Sequence sequence = (Sequence)fragment;
				score = (int)sequence.score();
				url = sequence.getBestMatch().toString();
				alt = "Score of "+score+" with "+total+" matches\n"+urls;
			}
			
			String color = colorMap.get(url);								// get this URL's color, if it has one
			if (color == null) {
				do color = randomColor();									// else, generate a random HTML color
				while (colorMap.containsValue(color) 						// avoid duplicate colors
					&& colorMap.size() < 64);								// currently only supports 64 colors
				colorMap.put(url, color);									// and save it for later
			}
				
			String tag = 
				"<span class='sequence'"									// specify this as a sequence
				+" url='"+url+"'"											// associate it with a specific url
				+" name='seq-"+id+"'"										// give the sequence a unique identify
				+" style='background-color:"+color+"'>"						// assign unique color to this tag
				+"<a href="+url												// build an HTML link
				+" style='font-weight: bold; text-decoration: none'"		// configure style
				+" target=_blank"											// open in separate window
				+" title='"+alt+"'>";										// add info in alt text	
				
			tags.put(fragment, tag);
			id++;
		}
		return tags;
	}
	
	// Mark the best fragment for each character in the source text
	//
	public static String markByChar (SourceFragment fragment, String tag) {
		Map<SourceFragment, String> tags = tagWithMarker(Collections.singleton(fragment), tag);
		return markByChar(fragment, tags, tags);
	}
	public static String markByChar (SourceFragment fragment, String startTag, String endTag) {
		Map<SourceFragment, String> startTags = tagWithMarker(Collections.singleton(fragment), startTag);
		Map<SourceFragment, String> endTags = tagWithMarker(Collections.singleton(fragment), endTag);		
		return markByChar(fragment, startTags, endTags);
	}	
	public static String markByChar (SourceFragment fragment, Map<SourceFragment, String> startTags, Map<SourceFragment, String> endTags) {
		return markByChar(Collections.singleton(fragment), fragment.getSourceText(), startTags, endTags);
	}
	public static String markByChar (Set<? extends SourceFragment> fragments, SourceText sourceText, Map<SourceFragment, String> startTags, Map<SourceFragment, String> endTags) {
		String text = sourceText.toString();
		List<SourceFragment> sourceTextByChar = sourceText.getOverlapByChar(fragments);
		
		int position = 0;
		SourceFragment last = null;
		StringBuilder builder = new StringBuilder();		
		for (SourceFragment current : sourceTextByChar) {
			if (current != last) {							// something has changed
				if (last != null)							// if previous character was part of a sequence
					builder.append(endTags.get(last));		//  tag sequence as over
				if (current != null)						// if a new sequence is starting
					builder.append(startTags.get(current));	//  tag sequence as starting
			}
			builder.append(text.charAt(position));
			last = current;
			position++;
		}
		builder.append(text.substring(position));

		return builder.toString();
	}
	//===========================================================//
	
	//===========================================================//
	// Create summary pages for each displayed fragment
	//===========================================================//

	public static void buildSummaryPageTika (SourceFragment fragment, URLResult urlResult) {
		try {
			SourceText sourceText = new SourceText(urlResult, 3);
			Dev.output("<pre>"+sourceText+"</pre>", "test.html");
		}
		catch (Exception e) { Dev.out.println(e.getMessage()); }		
	}
/*	
	public static void buildSummaryPage (SourceFragment fragment, URLResult urlResult) {
		try {
			String url = urlResult.toString();		
			String pageText = Jsoup.connect(url).get().html();		
			Dev.output(pageText, "test.html");	
		}
		catch (Exception e) { Dev.out.println(e.getMessage()); }
	}
/*
	private static String markOffsets (Map<OffsetAttribute, SourceFragment> offsets, SourceText sourceText) {
		String text = sourceText.toString();
		StringBuilder builder = new StringBuilder();
		Set<SourceFragment> fragments = new HashSet<>(offsets.values());
		Map<SourceFragment, String> hrefs = buildHREFs(fragments);
		
		int last = 0;
		for (Map.Entry<OffsetAttribute, SourceFragment> entry : offsets.entrySet()) {
			OffsetAttribute offset = entry.getKey();
			SourceFragment fragment = entry.getValue();
			
			int start = offset.startOffset();
			int end = offset.endOffset();
			
			builder.append(text.substring(last, start));		
			builder.append(hrefs.get(fragment));
			builder.append(text.substring(start, end));
			builder.append("</a></span>");
			
			last = end;
		}
		builder.append(text.substring(last));

		return builder.toString();
	}
/*
	// Mark the best fragment for each word in the source text
	//
	private static String markSequences (Map<Integer, SourceFragment> seqByPosition, SourceText sourceText) {
		String text = sourceText.toString();
		StringBuilder builder = new StringBuilder();
		Set<SourceFragment> fragments = new HashSet<>(seqByPosition.values());
		Map<SourceFragment, String> hrefs = buildHREFs(fragments);	
	
		// This may or may not be faster than just iterating, need to test
		seqByPosition = new TreeMap<>(seqByPosition);

		int last = 0;
		int next = 0;
		SourceFragment lastSequence = null;
		for (Map.Entry<Integer, SourceFragment> entry : seqByPosition.entrySet()) {
			int position = entry.getKey();
			SourceFragment fragment = entry.getValue();
			
			// Start and end of first word in ngram
			int start = sourceText.startOffset(position);
			int end = 0;
			
			if (position < sourceText.length() - 1)
					end = sourceText.startOffset(position+1);
			else	end = sourceText.endOffset(position);

			if (start > last) {								// this word starts
				builder.append(text.substring(next, last));
				builder.append("</a></span>");
				builder.append(text.substring(last, start));
				builder.append(hrefs.get(fragment));
			} else if (fragment != lastSequence) {			// new, better URL mid-fragment
				builder.append(text.substring(next, start));
				builder.append("</a></span>");
				builder.append(hrefs.get(fragment));
			}
			builder.append(text.substring(start, end));

			lastSequence = fragment;
			last = sourceText.endOffset(position);
			next = end;
		}
		builder.append(text.substring(next, last));
		builder.append("</a></span>");
		builder.append(text.substring(last));
		
		return builder.toString();
	} */		
}