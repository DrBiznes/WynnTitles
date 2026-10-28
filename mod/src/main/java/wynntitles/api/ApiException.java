package wynntitles.api;

/** A non-2xx response from the server, or a failure to reach it (status 0). */
public class ApiException extends RuntimeException {
	private final int status;

	public ApiException(int status, String message) {
		super(message);
		this.status = status;
	}

	public ApiException(String message, Throwable cause) {
		super(message, cause);
		this.status = 0;
	}

	public int status() {
		return status;
	}

	public boolean unauthorized() {
		return status == 401;
	}

	/** Unwraps CompletableFuture's CompletionException to find the API error. */
	public static String describe(Throwable t) {
		while (t.getCause() != null && !(t instanceof ApiException)) {
			t = t.getCause();
		}
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}
}
