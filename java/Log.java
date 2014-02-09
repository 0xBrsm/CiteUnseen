/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.io.*;
import java.util.concurrent.TimeUnit;
 
public class Log {
	private String name;
 	private long startTime;
	private long endTime;
	private boolean print;

	// Constructors
	public Log (String name) {
		this.name = name;
		start();
	}
	
	public Log () {
	
	}
	
 	// Non-static methods for running a timer
	//
	public void start () {
		startTime = System.nanoTime();
		if (name != null) println(name+" started...");		
	}
	
	public double stop () {
		endTime = System.nanoTime();		
		double runTime = convert(endTime - startTime);	
	
		if (name != null) println(name+" run time: "+runTime+"s ");
		
		return runTime;
	}
	
	private double convert (long time) {
		return TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS)/1000.0;
	}
 
	// Static methods for standardizing output
	//
 	public static void print (String str) {
		System.out.print(str);
	}
	
	public static void println (String str) {
		print(str+"\n");
	}
	
	public static void println () {
		print("\n");
	}	
	
	public static boolean output (StringBuilder builder, String path) {
		return output(builder.toString(), path);
	}
	
	public static boolean output (String str, String path) {
		try (
			PrintWriter writer = new PrintWriter(path, "UTF-8");
		) {
			writer.println(str);
			Log.println("Data output to: "+path);
			
			return true;
		} catch (Exception e) {
			Log.println("Failed output to: "+path);
			Log.println("Error was: "+e.getMessage());
			
			return false;
		}
	}
}