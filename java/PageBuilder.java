/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 *	ALM in IT Thesis
 *	<date>	  
 *
 *	PageBuilder utility class to build HTML pages
 *	for presentation of results
 *
 */
package citeunseen;
 
import java.io.*;
import java.util.*;
import java.net.URL;
import java.text.BreakIterator;

import org.apache.commons.lang3.StringEscapeUtils;

public class PageBuilder {
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
		Set<SourceFragment> hits = SourceFragment.switchToMatches(fragments);
		double percent = (int)(sourceText.getSimilarity(fragments) * 1000) / 10.0;
		
		String summary = percent+"% similarity, with "+hits.size()+" notable URLs (duplicates and overlaps hidden).";
		
		// Build a mapping of url to HTML tag
		Map<SourceFragment, String> startTags = tagAsSpans(fragments);
		Map<SourceFragment, String> endTags = tagWithMarker(fragments, "</a></span>");		

		// Mark all our matches, highlighted as URLs
//		String markedText = markByChar(fragments, sourceText, startTags, endTags);
		String markedText = markByWord(fragments, sourceText, startTags, endTags);

		// Results only have two values, don't waste space
		Map<String, String> results = new HashMap<>(2, 1.5f);
		
		results.put("summary", summary);
		results.put("markedText", markedText);
		
		return results;
	}

	//===========================================================//
	// Create HTML tags and colors for pass fragments
	//===========================================================//		
	
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
			int n = fragment.getSourceText().getN();
			double percent = (int)(1000 * fragment.similarity()) / 10.0;
			int score = 0;

			String urls = "";
			for (SourceFragment match : fragment.matches())
				urls += match+"\n";		

			String url = fragment.toString();
			String alt = " with "+percent+"% similarity.\n"+urls+"\n";
				
			// hack
			if (fragment instanceof Sequence) {
				Sequence sequence = (Sequence)fragment;
				score = (int)sequence.score();
				url = sequence.getBestMatch().toString();
				alt = "Score of "+score+alt;
			} else alt = url+"\nBest match"+alt;
			
			String color = colorMap.get(url);								// get this URL's color, if it has one
			if (color == null) {
				do color = randomColor();									// else, generate a random HTML color
				while (colorMap.containsValue(color) 						// avoid duplicate colors
					&& colorMap.size() < 64);								// currently only supports 64 colors
				colorMap.put(url, color);									// and save it for later
			}
			
			String ngramString = fragment.getNGrams().toString();
			String tag = 
				"<span class='sequence'"									// specify this as a sequence
				+" url='"+url+"'"											// associate it with a specific url
				+" name='seq-"+id+"'"										// give the sequence a unique identify
				+" n='"+n+"'"												// value of n for this sequence
				+" ngrams=\""+ngramString+"\""								// add our ngrams for later matching
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
	//===========================================================//
	
	//===========================================================//
	// Mark the best fragment for each character in the source text
	//===========================================================//	
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
	// Mark by word to avoid crappy overlaps
	//===========================================================//
	public static String markByWord (SourceFragment fragment, String tag) {
		Map<SourceFragment, String> tags = tagWithMarker(Collections.singleton(fragment), tag);
		return markByWord(fragment, tags, tags);
	}
	public static String markByWord (SourceFragment fragment, String startTag, String endTag) {
		Map<SourceFragment, String> startTags = tagWithMarker(Collections.singleton(fragment), startTag);
		Map<SourceFragment, String> endTags = tagWithMarker(Collections.singleton(fragment), endTag);		
		return markByWord(fragment, startTags, endTags);
	}	
	public static String markByWord (SourceFragment fragment, Map<SourceFragment, String> startTags, Map<SourceFragment, String> endTags) {
		return markByWord(Collections.singleton(fragment), fragment.getSourceText(), startTags, endTags);
	}	
	public static String markByWord (Set<? extends SourceFragment> fragments, SourceText sourceText, Map<SourceFragment, String> startTags, Map<SourceFragment, String> endTags) {
		List<SourceFragment> sourceTextChar = sourceText.getOverlapByChar(fragments);		
		BreakIterator wordbreak = BreakIterator.getWordInstance();
		String text = sourceText.toString();		
		wordbreak.setText(text);
		
		SourceFragment previous = null;
		int last = 0;			
		int start = 0;
		int end = 0;

		StringBuilder builder = new StringBuilder();		
		while (end != BreakIterator.DONE) {			
			SourceFragment current = sourceTextChar.get(start);		
			if (current != previous) {
				if (previous != null)
					builder.append(endTags.get(previous));
				if (current != null) {
					builder.append(text.substring(last, start));
					builder.append(startTags.get(current));
					last = start;
				}
			}
			builder.append(text.substring(last, end));
			
			previous = current;
			last = end;
			start = wordbreak.next();
			end = wordbreak.next();
			
			nextWordStart: while (end != BreakIterator.DONE) {
				for (int i = start; i < end; i++) {
					if (Character.isLetter(text.codePointAt(i)))
						break nextWordStart;
				}
				start = end;
				end = wordbreak.next();
			}
		}
		return builder.toString();
	}
	//===========================================================//	
}