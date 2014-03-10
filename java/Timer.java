/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.util.concurrent.TimeUnit;
 
public class Timer {
	private String name;
 	private long startTime;
	private long endTime;
	private long totalTime;

	// Constructors
	public Timer () {}
	
	public Timer (String name) {
		this.name = name;
		start();
	}
	
 	// Non-static methods for running a timer
	//
	public void start () {
		startTime = System.nanoTime();
		if (name != null) Dev.out.println(name+" started...");		
	}
	
	public double stop () {
		endTime = System.nanoTime();		
		long runTime = endTime - startTime;
		totalTime += runTime;
	
		if (name != null) Dev.out.println(name+" run time: "+ms(runTime)+"s ");
		
		return runTime;
	}
	
	public double totalTime () {
		return ms(totalTime);
	}
	
	private double ms (long time) {
		return TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS)/1000.0;
	}
}