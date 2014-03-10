/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 */

package citeunseen;

import java.io.*;
import java.util.Set;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ClickHandler extends HttpServlet {
	private static final int n = 3;
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// Disable all the log4j noise
		org.apache.log4j.Logger.getLogger("org.apache.http").setLevel(org.apache.log4j.Level.OFF);	
	
		String text = request.getParameter("text");
		String link = request.getParameter("url");
		String output = "";

		try {
			URL url = new URL(link);
			SourceText urlText = new SourceText(url, n);
			SourceFragment overlap = urlText.getOverlap(text);

			Processor processor = new Processor();
			Set<Sequence> sequences = processor.findSequences(overlap);

			String context = urlText.getOverlapInContext(Collections.max(sequences));
			SourceText urlContext = new SourceText(context, n);		
			overlap = urlContext.getOverlap(text);
			
			output = Builder.markByChar(overlap, "<b>", "</b>");
		}
		catch (NoSuchElementException e) {
			output = "Not found.";
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












