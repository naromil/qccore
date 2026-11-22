package com.qccore.client;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The requests this client sent to the server that still wait for their result. The server answers
 * every apply, align, undo and redo request with exactly one result packet, in the order it got
 * them, so the oldest pending request is the one a result answers. The result packet itself does
 * not say what it answers.
 */
public final class QcRequests {
	/** More pending requests than this means results went missing; the oldest are dropped. */
	static final int LIMIT = 32;

	/** What a request asked for. */
	public enum Kind { APPLY, ALIGN, UNDO, REDO }

	/** A request, with the plan history's revision when it was sent. */
	public record Pending(Kind kind, int revision) {
	}

	private final Deque<Pending> pending = new ArrayDeque<>();

	/** Records a request just sent. */
	public void expect(Kind kind, int revision) {
		this.pending.addLast(new Pending(kind, revision));
		while (this.pending.size() > LIMIT) {
			this.pending.removeFirst();
		}
	}

	/** The request a result that just arrived answers, or {@code null} if none was waiting. */
	@Nullable
	public Pending answered() {
		return this.pending.pollFirst();
	}

	/**
	 * Whether a request of {@code kind} sent at plan revision {@code revision} still waits for its
	 * result. K pressed again before the apply's result arrives would send the same plan twice: the
	 * server builds it twice, the first world undo then only reverts the copy, and the late second
	 * result reports the plan as changed.
	 */
	public boolean waiting(Kind kind, int revision) {
		for (Pending request : this.pending) {
			if (request.kind() == kind && request.revision() == revision) {
				return true;
			}
		}
		return false;
	}

	public int size() {
		return this.pending.size();
	}

	public void clear() {
		this.pending.clear();
	}
}
