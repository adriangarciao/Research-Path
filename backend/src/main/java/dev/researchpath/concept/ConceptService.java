package dev.researchpath.concept;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.researchpath.concept.ConceptModel.Representation;
import dev.researchpath.snapshot.CandidateSnapshotService;
import dev.researchpath.snapshot.FrozenCandidate;
import dev.researchpath.snapshot.SnapshotNotEvaluatedException;

/**
 * Builds the concept model of a snapshot from its frozen optimization candidates only: no provider
 * calls and no reads of the refreshable cache, so the same snapshot + policy version gives the same model.
 */
@Service
public class ConceptService {

	private final CandidateSnapshotService snapshots;

	public ConceptService(CandidateSnapshotService snapshots) {
		this.snapshots = snapshots;
	}

	/** Empty if the snapshot does not exist; throws if it predates frozen inputs. */
	public Optional<ConceptModel> build(UUID snapshotId, Representation representation) {
		return snapshots.findSummary(snapshotId).map(summary -> {
			if (summary.relevance() == null) {
				throw new SnapshotNotEvaluatedException(snapshotId);
			}
			List<ConceptPolicy.Input> inputs = snapshots.optimizationCandidates(snapshotId).stream()
					.map(ConceptService::input)
					.toList();
			return ConceptPolicy.build(summary.topic(), inputs, representation);
		});
	}

	static ConceptPolicy.Input input(FrozenCandidate c) {
		return new ConceptPolicy.Input(c.publicationId(), c.work().openalexId(), c.work().concepts());
	}

}
