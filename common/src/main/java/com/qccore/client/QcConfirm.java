package com.qccore.client;

/**
 * Two presses for an action that loses a file: the first press of a delete or an overwrite only
 * arms it (the screen says what a second press will do), the second press of the same action on the
 * same target carries it out. Arming another action or target, or {@link #reset()} (a new selection),
 * disarms, so a press never confirms something the player did not just see armed.
 *
 * <p>Registry-free so JUnit can drive it.
 */
final class QcConfirm {
	private String armed;

	/** Whether {@code action} was armed by the previous press (and disarms it); otherwise arms it. */
	boolean confirm(String action) {
		if (action.equals(this.armed)) {
			this.armed = null;
			return true;
		}
		this.armed = action;
		return false;
	}

	void reset() {
		this.armed = null;
	}
}
