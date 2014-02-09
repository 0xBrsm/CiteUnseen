/**
 *	@author Brian St. Marie
 *	stmarie@fas.harvard.edu
 *
 *	ALM in IT Thesis
 *	<date>	  
 *
 *	Convenience class to hold Bing account info
 *
 */

package citeunseen;

public class BingAccountInfo {
	// cycle through Bing account keys for searches
	//
	public static String getKey (int id) {	
		int i = id % info.length;	// cycle through keys
		return ":" + info[i];
	}
	
	// Get the API name for this id
	//
	public static String getAPI (int id) {
		int i = id % info.length;
		int j = info.length / 10;
		int k = (i / j) + 1;
		int l = i % j;
		
		return k+"-"+l;
	}
	
	private static final String[] info = {
		//1 - bonesface
		"REDACTED_BING_KEY_1",

		//2 - rowstrange
		"REDACTED_BING_KEY_2",

		//3 - surferbit
		"REDACTED_BING_KEY_3",

		//4 - hauljoints
		"REDACTED_BING_KEY_4",

		//5 - raftleep
		"REDACTED_BING_KEY_5",

		//6 - fairbash
		"REDACTED_BING_KEY_6",

		//7 - brothgrid
		"REDACTED_BING_KEY_7",

		//8 - whackgames
		"REDACTED_BING_KEY_8",

		//9 - sinepulse
		"REDACTED_BING_KEY_9",
		
		//10 - hipbox
		"REDACTED_BING_KEY_10"
		
/*		spares and testing
		//11 - stmarie
		"REDACTED_BING_KEY_11",
		
		//12 - brsm
		"REDACTED_BING_KEY_12"
*/
	};
}