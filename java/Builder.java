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
 
 public class Builder {
 
  	public static String buildHTML (URLResult urlResult, String output) {
//		String urls = urlResult.getGroup.toString();
		int total = urlResult.getResults().size();
		double percent = urlResult.getPercent();
		StringBuilder builder = new StringBuilder();
		
		builder.append("<html><body>");
//		builder.append("<a href="+url+" target=_blank>"+url+"</a><br>");
		builder.append(percent+"% match with "+total+" URLs.<p>");
		builder.append("<table bgcolor=#eeeeeeee width=80% align=center><tr><td><pre style=\"white-space: pre-wrap; font-family: Arial; font-size: 10pt;\">");
		builder.append(output);
		builder.append("</pre></td></tr></table>");
		builder.append("</body></html>");

		return builder.toString();
	}
 
 	public static void outputHTMLPage (URLResult urlResult, String output, String root) throws Exception {
		String url = urlResult.toString();
		int total = urlResult.getPositions().size();
		int score  = (int)urlResult.getScore();
		double percent = urlResult.getPercent();
		String path = root+".html";
		PrintWriter writer = new PrintWriter(path, "UTF-8");
		
		writer.println("<html><body>");
		writer.println("<a href="+url+" target=_blank>"+url+"</a><br>");
		writer.println(percent+"% match with "+total+" matches and a score of "+score+".<p>");
		writer.println("<table bgcolor=#eeeeeeee width=80% align=center><tr><td><pre style=\"white-space: pre-wrap; font-family: Arial; font-size: 10pt;\">");
		writer.write(output);
		writer.println("</pre></td></tr></table>");
		
		writer.println("</body></html>");
		writer.close();
		
		System.out.println("Results saved to "+path+".");
	}
}