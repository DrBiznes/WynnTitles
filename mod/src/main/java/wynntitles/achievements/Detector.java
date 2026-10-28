package wynntitles.achievements;

import com.google.gson.JsonObject;
import wynntitles.catalog.Catalog;

/** Checks one trigger type against the player's current state. */
@FunctionalInterface
public interface Detector {
	/**
	 * Called on the client thread about once a second for each locked
	 * achievement of this type. Returns evidence for the claim if the trigger is
	 * met right now, otherwise null.
	 */
	JsonObject check(Catalog.Trigger trigger);
}
