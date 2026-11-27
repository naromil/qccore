package com.qccore.client;

/**
 * What a right click does over the cells of the current {@link com.qccore.core.plan.BuildShape}.
 * Every tool edits the plan only; nothing reaches the world before the plan is applied.
 */
public enum QcTool {
	/** Adds QC units to the active building (a new one when none is active). */
	UNITS("building", 0.35F, 0.65F, 0.95F),
	/** Removes planned units, roads and cleared cells. */
	ERASE("erase", 0.95F, 0.35F, 0.35F),
	/** Moves planned units into the active building. */
	ASSIGN("assign to building", 0.75F, 0.45F, 0.95F),
	/**
	 * Tweaks one planned unit (a click opens {@link QcTweakScreen}: each side's partition and door); with a
	 * shape of two or more units it sets the bulk partition ({@link QcClientState#tweakPartition}) on every
	 * shared side inside it, as the old walls / gates / open tools did.
	 */
	TWEAK("tweak", 0.29F, 0.66F, 1.0F),
	/** Clears the terrain of the cells (and levels the floor plane if a ground block is set). */
	CLEAR("clear terrain", 1.0F, 0.55F, 0.15F),
	/** Lays a QC road (qckit's 7-wide negative space) in the active road style. */
	ROAD("road", 0.65F, 0.65F, 0.65F),
	/** Stamps the selected template at the clicked cell; the array and mirror modifiers repeat it. */
	TEMPLATE("template", 0.95F, 0.85F, 0.3F);

	private final String label;
	public final float red;
	public final float green;
	public final float blue;

	QcTool(String label, float red, float green, float blue) {
		this.label = label;
		this.red = red;
		this.green = green;
		this.blue = blue;
	}

	public String label() {
		return this.label;
	}

	public QcTool next() {
		QcTool[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}
