package dev.researchpath.snapshot;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.researchpath.identity.IdentityDiagnostics;
import dev.researchpath.identity.IdentityRepository;
import dev.researchpath.relevance.RelevanceTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Development-only endpoints for creating and inspecting candidate snapshots.
 * Enabled by {@code researchpath.dev-api.enabled=true} (the {@code local} profile).
 * Not the product API: path generation will create snapshots internally later.
 */
@RestController
@RequestMapping("/api/dev/snapshots")
@ConditionalOnProperty(name = "researchpath.dev-api.enabled", havingValue = "true")
@Validated
class DevSnapshotController {

	private final CandidateSnapshotService service;

	private final IdentityRepository identity;

	DevSnapshotController(CandidateSnapshotService service, IdentityRepository identity) {
		this.service = service;
		this.identity = identity;
	}

	record CreateSnapshotRequest(
			@NotBlank @Size(max = 300) String topic,
			@Min(1) @Max(200) Integer maxCandidates) {
	}

	@PostMapping
	ResponseEntity<SnapshotSummary> create(@Valid @RequestBody CreateSnapshotRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(service.createSnapshot(request.topic(), request.maxCandidates()));
	}

	@GetMapping("/{id}")
	ResponseEntity<SnapshotSummary> get(@PathVariable UUID id) {
		return ResponseEntity.of(service.findSummary(id));
	}

	/** Frozen relevance decisions with evidence, optionally filtered by tier (e.g. ?tier=BORDERLINE). */
	@GetMapping("/{id}/relevance")
	ResponseEntity<List<RelevanceRow>> relevance(@PathVariable UUID id,
			@RequestParam(required = false) RelevanceTier tier) {
		return ResponseEntity.of(service.relevance(id, tier));
	}

	/** Frozen identity decisions (with evidence) and suppressed duplicates for one snapshot. */
	@GetMapping("/{id}/identity")
	ResponseEntity<IdentityDiagnostics> identity(@PathVariable UUID id) {
		return ResponseEntity.of(identity.diagnostics(id));
	}

}
