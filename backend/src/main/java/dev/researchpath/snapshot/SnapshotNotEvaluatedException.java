package dev.researchpath.snapshot;

import java.util.UUID;

/** The snapshot predates frozen evaluation inputs (V5), so features cannot be computed from it. */
public class SnapshotNotEvaluatedException extends RuntimeException {

	public SnapshotNotEvaluatedException(UUID snapshotId) {
		super("Snapshot " + snapshotId + " was created before frozen evaluation inputs existed; create a new snapshot.");
	}

}
