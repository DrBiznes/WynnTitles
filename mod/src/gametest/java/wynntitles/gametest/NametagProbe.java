package wynntitles.gametest;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;

/** Collects the plain text of every nametag line Wynntils draws. */
public final class NametagProbe {
	private static final Set<String> DRAWN = ConcurrentHashMap.newKeySet();
	private static final MethodHandle COMPONENT = accessor();

	private NametagProbe() {}

	public static boolean drawn(String text) {
		return DRAWN.contains(text);
	}

	public static void clear() {
		DRAWN.clear();
	}

	public static Set<String> all() {
		return Set.copyOf(DRAWN);
	}

	@SuppressWarnings("rawtypes")
	public static void record(List nametags) {
		for (Object nametag : nametags) {
			try {
				DRAWN.add(((Component) COMPONENT.invoke(nametag)).getString());
			} catch (Throwable t) {
				throw new AssertionError("Could not read a Wynntils nametag", t);
			}
		}
	}

	private static MethodHandle accessor() {
		try {
			Class<?> type = Class.forName("com.wynntils.features.players.CustomNametagRendererFeature$CustomNametag");
			return MethodHandles.privateLookupIn(type, MethodHandles.lookup())
					.findVirtual(type, "nametagComponent", MethodType.methodType(Component.class));
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
