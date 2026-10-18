package dev.researchpath.identity;

import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.researchpath.identity.IdentityPolicy.Resolution;

/**
 * Applies the conservative identity policy to one snapshot and freezes the result with it.
 * Call inside the transaction that creates the snapshot, after all members are stored.
 */
@Service
public class IdentityResolutionService {

	private final IdentityRepository repository;

	public IdentityResolutionService(IdentityRepository repository) {
		this.repository = repository;
	}

	public Resolution resolveSnapshot(UUID snapshotId) {
		Resolution resolution = IdentityPolicy.resolve(repository.loadMemberEvidence(snapshotId),
				repository.loadPairsAmongMembers(snapshotId));
		repository.saveResolution(snapshotId, resolution, IdentityPolicy.VERSION);
		return resolution;
	}

}
