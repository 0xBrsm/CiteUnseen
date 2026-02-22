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

import java.util.ArrayList;
import java.util.List;

public class BingAccountInfo {
	private static final String[] info = loadKeys();

	// cycle through Bing account keys for searches
	//
	public static String getKey (int id) {	
		int i = Math.floorMod(id, info.length);	// cycle through keys
		return ":" + info[i];
	}
	
	// Get the API name for this id
	//
	public static String getAPI (int id) {
		int i = Math.floorMod(id, info.length);
		int j = Math.max(1, info.length / 10);
		int k = (i / j) + 1;
		int l = i % j;
		
		return k+"-"+l;
	}

	private static String[] loadKeys () {
		String raw = Credentials.required("CITEUNSEEN_BING_ACCOUNT_KEYS");
		String[] split = raw.split(",");
		List<String> keys = new ArrayList<>();

		for (String value : split) {
			String key = value.trim();
			if (!key.isEmpty()) {
				keys.add(key);
			}
		}

		if (keys.isEmpty()) {
			throw new IllegalStateException(
				"CITEUNSEEN_BING_ACCOUNT_KEYS is set but contains no usable keys."
			);
		}

		return keys.toArray(new String[0]);
	}
}
