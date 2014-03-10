/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.io.*;
import java.util.*;
import java.util.concurrent.*; 
 
public class Dev {
	//
	// Standard input and output for easily changing the entire system
	//
	public static InputStream in = in();
	private static InputStream in () {
		InputStream stream = null;
		try {
		
			stream = System.in;

		} catch (Exception e) { e.getMessage(); }
		
		return stream;
	}
	
	public static PrintStream out = out();	
	private static PrintStream out () {
		PrintStream stream = null;
		try {
//			stream = new PrintStream("out.csv");
			stream = System.out;

		} catch (Exception e) { e.getMessage(); }
		
		return stream;
	}

	//
	// Static methods for dumping information to a file
	//
	public static String asCSV (Object ... values) {
		StringBuilder builder = new StringBuilder();
		
		for (Object value : values) {
			builder.append("\""+value+"\",");		// append the object as it's string representation
		}
		builder.setLength(builder.length() - 1);	// remove last comma
		builder.append(System.lineSeparator());		// add newline

		return builder.toString();
	}

	// Output any object to a file via it's toString method
	//
	public static boolean output (Object obj, String path) {
		try (
			PrintWriter writer = new PrintWriter(path, "UTF-8");
		) {
			writer.println(obj.toString());
			out.println("Data output to: "+path);
			
			return true;
		} catch (Exception e) {
			out.println("Failed output to: "+e.getMessage());
			
			return false;
		}
	}
	
	//
	// Static methods for confirming a question via the console
	//
	public static boolean confirm (String msg) {
		return confirm(true, msg);
	}
	
	public static boolean confirm (boolean console, String msg) {
		out.println(msg);
		if (console) {
			out.print("Continue? (y/n) ");
			Scanner scanner = new Scanner(in);
			String key = scanner.nextLine();
			if (!key.equals("y")) return false;
		}
		return true;
	}
	
	//===========================================================//
	// Convenience methods for importing/exporting search results
	//===========================================================//
	
	public static Object importCache(String path) {
		out.print("Loading cache...");
		long startTime = System.nanoTime();
		Object obj = null;
		
		try (
			FileInputStream dat = new FileInputStream(path);
			ObjectInputStream input = new ObjectInputStream(dat);
		) {
			obj = input.readObject();
			
			long endTime = System.nanoTime();
			long duration = endTime - startTime;
			out.println("complete! ("+TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS)/1000.0+"s)");
			
		} catch (Exception e) {
			out.println("failed! "+e.getMessage());
		}
		
		return obj;
	}
	
	public static boolean exportCache(Object obj, String path) {
		out.print("Saving cache...");
		long startTime = System.nanoTime();		
		
		try (
			FileOutputStream dat = new FileOutputStream(path);
			ObjectOutputStream output = new ObjectOutputStream(dat);		
		) {
			output.writeObject(obj);
			
			long endTime = System.nanoTime();
			long duration = endTime - startTime;
			out.println("complete! ("+TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS)/1000.0+"s)");
			
			return true;
		} catch (IOException e) {
			out.println("failed! "+e.getMessage());
			return false;
		}
	}
	//===========================================================//
		
}