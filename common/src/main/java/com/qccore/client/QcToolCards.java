package com.qccore.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The tools screen's settings card (T33): which card a tool shows and the card's plain-word helpers.
 * Free of Minecraft classes so it is JUnit-tested ({@code QcToolCardsTest}).
 */
final class QcToolCards {
	private QcToolCards() {
	}

	/** What the top-right card of {@link QcToolsScreen} shows: the settings of the current tool. */
	enum Card {
		BUILDING("Building"), ROAD("Road"), CLEAR("Clear terrain"), TWEAK("Tweak"), TEMPLATE("Template");

		final String title;

		Card(String title) {
			this.title = title;
		}

		/** Building for the building, assign and erase tools; every other tool has its own card. */
		static Card of(QcTool tool) {
			return switch (tool) {
				case UNITS, ASSIGN, ERASE -> BUILDING;
				case ROAD -> ROAD;
				case CLEAR -> CLEAR;
				case TWEAK -> TWEAK;
				case TEMPLATE -> TEMPLATE;
			};
		}
	}

	/** "blocks complete", or what a road style lacks: "lanes 2, 3 without a block → Blocks…" (lane floors by lane). */
	static String roadStatus(Set<String> missing) {
		if (missing.isEmpty()) {
			return "blocks complete";
		}
		List<String> lanes = new ArrayList<>();
		List<String> other = new ArrayList<>();
		for (String role : missing) {
			if (role.matches("floor\\d")) {
				lanes.add(role.substring(5));
			} else {
				other.add(role);
			}
		}
		List<String> parts = new ArrayList<>();
		if (!lanes.isEmpty()) {
			parts.add((lanes.size() == 1 ? "lane " : "lanes ") + String.join(", ", lanes));
		}
		parts.addAll(other);
		return String.join(", ", parts) + " without a block → Blocks…";
	}

	/** The next free name after {@code current}: "street" → "street 2", "street 2" → "street 3" (skipping taken ones). */
	static String nextRoadName(String current, Set<String> taken) {
		String base = current.replaceFirst(" \\d+$", "").trim();
		if (base.isEmpty()) {
			base = "street";
		}
		int n = 2;
		while (taken.contains(base + " " + n)) {
			n++;
		}
		return base + " " + n;
	}
}
