package com.qccore.client;

import com.qccore.core.plan.QcPlan;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo/redo of plan edits: whole-plan snapshots, at most {@link #LIMIT} deep. Every edit pushes the
 * plan as it was before; undo swaps the current plan with the newest snapshot.
 */
public final class QcHistory {
	private static final int LIMIT = 100;

	private final Deque<QcPlan> undo = new ArrayDeque<>();
	private final Deque<QcPlan> redo = new ArrayDeque<>();

	/** Records {@code before} as an undo step and drops the redo branch. */
	public void push(QcPlan before) {
		this.undo.push(before.copy());
		while (this.undo.size() > LIMIT) {
			this.undo.removeLast();
		}
		this.redo.clear();
	}

	/** The plan before the last edit, or {@code null}; {@code current} becomes redoable. */
	public QcPlan undo(QcPlan current) {
		if (this.undo.isEmpty()) {
			return null;
		}
		this.redo.push(current.copy());
		return this.undo.pop();
	}

	/** The plan the last undo left, or {@code null}; {@code current} becomes undoable again. */
	public QcPlan redo(QcPlan current) {
		if (this.redo.isEmpty()) {
			return null;
		}
		this.undo.push(current.copy());
		return this.redo.pop();
	}

	public int undoDepth() {
		return this.undo.size();
	}

	public int redoDepth() {
		return this.redo.size();
	}

	public void clear() {
		this.undo.clear();
		this.redo.clear();
	}
}
