/**
 *  @author Brian St. Marie
 *  stmarie@fas.harvard.edu
 *
 */
 
package citeunseen;
 
import java.util.concurrent.TimeUnit;
 
public class Timer {
	private static int id;
	private String name = "Timer"+id;
	
 	private long startTime;
	private long runTime;
	private boolean running;
	private boolean restart;

	// Static constructors
	//
	public static Timer startNew () {
		Timer timer = new Timer();
		timer.start();
		return timer;
	}
	public static Timer startNew (String name) {
		Timer timer = new Timer(name);
		timer.start();
		return timer;
	}	
	
	// Constructors
	//
	public Timer (String name) {
		this.name = name;
	}
	public Timer () {}
	
	// Output methods
	//
	public Timer print () {
		if (running && runTime == 0)
			Dev.out.println(name+" started.");
		else print("");
		
		return this;
	}
	
	public Timer print (String type) {
		String time = "";
		switch(type) {
			case "s"	:	time = s()+type; break;		
			case "ms"	:	time = ms()+type; break;
			default		:	time = ns()+type;
		}
		Dev.out.println(name+" run time: "+time);
		
		return this;
	}
	
 	// Methods for retrieving runtime
	//
	public double s () {
		return ms() / 1000.0;
	}
	
	public long ms () {
		return TimeUnit.NANOSECONDS.toMillis(ns());
	}	
	
	public long ns () {
		if (!running) return runTime;
		return System.nanoTime() - startTime;
	}
	
	// Methods for running a timer
	//	
	public Timer start () {
		if (restart) runTime = 0;
		restart = true;
		running = true;

		startTime = System.nanoTime();
		
		return this;
	}
	
	public Timer stop () {	
		if (running)
			runTime += System.nanoTime() - startTime;
		running = false;
		
		return this;
	}
			
	public Timer pause () {
		stop();	
		restart = false;
		
		return this;
	}
	
	public Timer lap () {
		pause();
		start();
	
		return this;
	}
}