/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
 
public class Dev {
	//
	// Standard input and output for easily changing the entire system
	//
	
	// Input stream
	//
	public static InputStream in = in();
	private static InputStream in () {
		InputStream stream = null;
		try {
		
			stream = System.in;

		} catch (Exception e) { e.getMessage(); }
		
		return stream;
	}
	
	// Output stream
	//
	public static PrintStream out = out();	
	private static PrintStream out () {
		PrintStream stream = null;
		try {
//			stream = new PrintStream("log.txt");
//			stream = new PrintStream(new NullOutputStream());
			stream = System.out;
			

		} catch (Exception e) { e.getMessage(); }
		
		return stream;
	}
	
	// Null stream
	//
	public static PrintStream nullOut () {
		return new PrintStream(new NullOutputStream());
	}

	//
	// Static methods for dumping information to a file
	//
	
	// Convert a series of objects to CSV string using their toString
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
	// Convenience methods for standard cache location
	//===========================================================//	
	
	// Documents cache location
	//
	private static final String cachePath = "Documents"+File.separator;

	public static String cachePath () {
		return cachePath;
	}
	
	//
	// Return various cache folder formats for a passed file or file name
	//
	
	// Get the root name of a file; file.txt >> file
	//
	public static String getRoot (File file) {
		return getRoot(file.getName());
	}
	public static String getRoot (String fileName) {
		return StringUtils.substringBeforeLast(fileName, ".");
	}

	// Get the path to the cache folder for this file
	//
	public static String getCachePath (File file) {
		return getCachePath(file.getName());
	}	
	public static String getCachePath (String fileName) {
		return cachePath+getRoot(fileName)+File.separator;
	}

	// Get the cache folder for this file, plus the root of the file
	// This is used for saving files in the same name as the submitted
	// file. For instance. file.txt will create file.html, file.csv, etc.
	//
	public static String getCacheRoot (File file) {
		return getCacheRoot(file.getName());
	}	
	public static String getCacheRoot (String fileName) {
		return getCachePath(fileName)+getRoot(fileName);
	}
	
	// Create a .dat filename and path for the passed file and group of objects
	// It works as: filename.obj1...objn.dat where obj is called toString
	//
	public static String datPath (File file, Object ... objects) {
		return datPath(file.getName(), objects);
	}
	public static String datPath (String fileName, Object ... objects) {
		String dat = getCacheRoot(fileName);
		for (Object obj : objects)
			dat += "."+obj;
		return dat+".dat";
	}
	
	//
	// Save a local copy of the file
	//
	public static void copyToCache (FileItem fileItem) throws Exception {
		String fileName = FilenameUtils.getName(fileItem.getName()); 		// Extra nonsense courtesy of IE
		String filePath = getCachePath(fileName)+fileName;
		File localCopy = new File(filePath);
		
		if (localCopy.exists())
			return;		
		
		localCopy.getParentFile().mkdirs();
		fileItem.write(localCopy);
		out.println("Local copy of document saved to "+localCopy.getParentFile());
	}
	public static void copyToCache (File file) throws Exception {
		String filePath = getCachePath(file)+file.getName();
		File localCopy = new File(filePath);
		
		if (localCopy.exists())
			return;
		
		localCopy.getParentFile().mkdirs();
		Files.copy(file.toPath(), localCopy.toPath());
		out.println("Local copy of document saved to "+localCopy.getParentFile());
	}		
	
	//===========================================================//
	// Convenience methods for importing/exporting search results
	//===========================================================//
	
	public static Object importCache(String path) {
		out.print("Loading cache...");
		Timer timer = Timer.startNew();
		Object obj = null;
		
		try (
			FileInputStream dat = new FileInputStream(path);
			ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(dat));
		) {
			obj = input.readObject();
			out.println("complete! ("+timer.s()+"s)");
		} catch (Exception e) {
			out.println("failed! "+e.getMessage());
		}
		
		return obj;
	}
	
	public static boolean exportCache(Object obj, String path) {
		out.print("Saving cache...");
		Timer timer = Timer.startNew();		
		
		try (
			FileOutputStream dat = new FileOutputStream(path);
			ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(dat));		
		) {
			output.writeObject(obj);
			out.println("complete! ("+timer.s()+"s)");
			return true;
		} catch (IOException e) {
			out.println("failed! "+e.getMessage());
			return false;
		}
	}
	//===========================================================//
		
}