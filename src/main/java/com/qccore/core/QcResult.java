package com.qccore.core;

/** The outcome of a build or align request: whether it was applied and the message for the player. */
public record QcResult(boolean ok, String message) {
}
