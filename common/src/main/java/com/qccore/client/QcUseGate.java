package com.qccore.client;

/**
 * Decides which use-key presses reach a shape, so a multi-point shape takes exactly one point per
 * fresh press while a single-cell shape still drag-paints when the button is held.
 *
 * <p>Vanilla's {@code handleKeybinds} can call {@code startUseItem} twice in one tick for one fresh
 * press: once for the queued click ({@code keyUse.consumeClick()}) and once more for the held key
 * ({@code keyUse.isDown() && rightClickDelay == 0}). On Fabric the input mixin cancels
 * {@code startUseItem} at its head, before vanilla sets {@code rightClickDelay}, so both calls reach
 * {@link QcInteraction#click}. Knowing only whether the key was down at the end of the previous tick,
 * a line, floor or ring took both points from one press and finished as a single cell, and a box
 * took two of its three points at once (T13). The gate also closes after a taken point, so the
 * second call of the same tick is refused.
 *
 * <p>Registry-free so JUnit can drive it.
 */
final class QcUseGate {
	/** Whether the use key was held at the end of the previous tick, or a point was taken this tick. */
	private boolean closed;

	/**
	 * Whether this use-key call may place: always for a single-cell shape (held painting), and for a
	 * multi-point shape only on the first call of a fresh press.
	 */
	boolean accept(boolean multiPoint) {
		if (!multiPoint) {
			return true;
		}
		if (closed) {
			return false;
		}
		closed = true;
		return true;
	}

	/** Called at the end of every client tick with the use key's state: a release reopens the gate. */
	void endTick(boolean useDown) {
		closed = useDown;
	}
}
