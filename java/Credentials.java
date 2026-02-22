package citeunseen;

public final class Credentials {
	private Credentials () {}

	// Look up a required credential from environment variables first,
	// then Java system properties.
	public static String required (String envName) {
		String value = System.getenv(envName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getProperty(envName);
		}
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalStateException(
				"Missing required credential: " + envName
			);
		}
		return value.trim();
	}
}
