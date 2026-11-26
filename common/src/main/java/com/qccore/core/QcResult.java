package com.qccore.core;

import java.util.List;

/** The outcome of a build, apply or undo request: whether it was applied and the message for the player. */
public record QcResult(boolean ok, String message) {

    /**
     * The first of {@code lines} with how many more follow ("first (+2 more)"), or {@code null} when
     * there are none. A plan's problems and notes are shown one line at a time; the count says that
     * fixing the first is not the end, so the player isn't surprised by the next one on the next try.
     */
    public static String first(List<String> lines) {
        if (lines.isEmpty()) {
            return null;
        }
        return lines.size() == 1 ? lines.get(0) : lines.get(0) + " (+" + (lines.size() - 1) + " more)";
    }
}
