/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 */

package citeunseen;

import java.io.*;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.net.URL;
import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

public class ClickHandler extends HttpServlet {
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// Disable all the log4j noise
		org.apache.log4j.Logger.getLogger("org.apache.http").setLevel(org.apache.log4j.Level.OFF);
		
		// Set to UTF-8 encoding
		request.setCharacterEncoding("UTF-8");

		String link = request.getParameter("url");		
		int n = Integer.parseInt(request.getParameter("n"));
		String ngramString = request.getParameter("ngrams");
		String[] ngrams = StringUtils.substringBetween(ngramString, "[", "]").split(", ");
		String output = "";

		try {
			// In order to support offline copora
			// we must support links to local files
			//
			SourceText urlText = null;
			URI uri = new URI(link);
			if (uri.isAbsolute()) {
				URL url = uri.toURL();
				urlText = new SourceText(url, n);				
			} else	{
				File file = new File(link);
				urlText = new SourceText(file, n);
			}
			SourceFragment overlap = urlText.getOverlap(ngrams);

			if (overlap.size() == 0)
				throw new NoSuchElementException();
			
			Processor processor = Processor.build().setMinimumScore(1).setScoreByRarity(false, false, 4.0);
			
			Set<Sequence> sequences = processor.findSequences(overlap);
			sequences = new TreeSet<>(sequences).descendingSet();
			Sequence highlights = new Sequence(urlText);
			for (Sequence sequence : sequences) {
				Set<String> current = highlights.getNGrams();
				Set<String> these = sequence.getNGrams();			
				if (current.addAll(these))
					highlights.add(sequence.positions());
				if (highlights.getNGrams().size() >= ngrams.length)
					break;
			}

			String context = urlText.getOverlapInContext(highlights);
			SourceText urlContext = new SourceText(context, n);		
			overlap = urlContext.getOverlap(ngrams);
		
			String href = 
				"<a href="+link
				+" style='font-weight: bold; text-decoration: none'"		// configure style
				+" target=_blank"											// open in separate window
				+" title='click to open source in a new window'>";			// add info in alt text	
				
			output = PageBuilder.markByChar(overlap, href, "</a>");
		}
		catch (NoSuchElementException e) {
			output = "Page failed to load.";
		}
		catch (Exception e) { 
			output = e.getMessage();
			e.printStackTrace();
		}
		response.setCharacterEncoding("UTF-8");
		
		PrintWriter out = response.getWriter();
		out.println(output);
	}
}












