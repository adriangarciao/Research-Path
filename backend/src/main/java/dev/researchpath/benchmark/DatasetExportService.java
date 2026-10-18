package dev.researchpath.benchmark;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.researchpath.publication.NormalizedWork;
import dev.researchpath.snapshot.CandidateSnapshotService;
import dev.researchpath.snapshot.FrozenCandidate;
import dev.researchpath.snapshot.SnapshotNotEvaluatedException;
import dev.researchpath.snapshot.SnapshotSummary;

/** Exports a snapshot's frozen optimization candidates as a benchmark dataset (no provider calls). */
@Service
public class DatasetExportService {

	private final CandidateSnapshotService snapshots;

	public DatasetExportService(CandidateSnapshotService snapshots) {
		this.snapshots = snapshots;
	}

	/** Empty if the snapshot does not exist; throws if it predates frozen inputs. */
	public Optional<BenchmarkDataset> export(UUID snapshotId) {
		return snapshots.findSummary(snapshotId).map(summary -> {
			if (summary.relevance() == null) {
				throw new SnapshotNotEvaluatedException(snapshotId);
			}
			return new BenchmarkDataset(BenchmarkDataset.FORMAT_VERSION, snapshotId, summary.topic(),
					summary.createdAt(), policies(summary),
					snapshots.optimizationCandidates(snapshotId).stream().map(DatasetExportService::candidate).toList());
		});
	}

	private static BenchmarkDataset.Policies policies(SnapshotSummary s) {
		return new BenchmarkDataset.Policies(s.normalizerVersion(),
				s.identity() == null ? null : s.identity().policyVersion(), s.relevance().policyVersion());
	}

	static BenchmarkDataset.Candidate candidate(FrozenCandidate c) {
		NormalizedWork w = c.work();
		Optional<NormalizedWork.SourceLink> link = w.preferredSourceLink();
		return new BenchmarkDataset.Candidate(w.openalexId(), c.providerRank(), w.title(), w.publicationYear(),
				w.workType(), w.doi(), w.citedByCount(), w.fwci(), w.citationPercentile(),
				link.map(NormalizedWork.SourceLink::url).orElse(null),
				link.map(l -> l.kind().name()).orElse(null),
				c.relevanceEvidence().path("titleOrConceptCoverage").asDouble(0),
				titleTermShare(c), w.concepts(), w.referencedWorkIds());
	}

	private static double titleTermShare(FrozenCandidate c) {
		int terms = c.relevanceEvidence().path("queryTerms").size();
		return terms == 0 ? 0 : Math.round(c.relevanceEvidence().path("inTitle").size() * 100.0 / terms) / 100.0;
	}

}
