package wynntitles.mc.screen.ui;

/** A short animation from 0 to 1 over a fixed time, eased out so it settles gently. */
public final class Tween {
	private final long durationMs;
	private long start;

	/** A finished tween; restart it to play it. */
	public Tween(long durationMs) {
		this.durationMs = durationMs;
		this.start = Long.MIN_VALUE / 2;
	}

	public Tween restart() {
		start = System.currentTimeMillis();
		return this;
	}

	public boolean running() {
		return System.currentTimeMillis() - start < durationMs;
	}

	/** Eased progress: fast at first, slowing to a stop at 1. */
	public float value() {
		float t = Math.clamp((System.currentTimeMillis() - start) / (float) durationMs, 0f, 1f);
		float inv = 1 - t;
		return 1 - inv * inv * inv;
	}

	public static float lerp(float from, float to, float t) {
		return from + (to - from) * t;
	}
}
