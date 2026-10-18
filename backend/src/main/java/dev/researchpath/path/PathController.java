package dev.researchpath.path;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Product path API (blueprint §7.3): generate, get, regenerate. Path ids are unguessable links, not access
 * control; there are no accounts. Generation is synchronous (D14 is measured in M13).
 */
@RestController
@RequestMapping("/api/paths")
@Validated
class PathController {

	private final PathService paths;

	PathController(PathService paths) {
		this.paths = paths;
	}

	record CreatePathRequest(
			@NotBlank @Size(max = 300) String topic,
			@Size(max = 16) String experience,
			@Size(max = 16) String focus,
			Integer budget) {
	}

	/** Missing values keep the parent revision's settings. */
	record RegenerateRequest(
			@Size(max = 16) String experience,
			@Size(max = 16) String focus,
			Integer budget,
			@Min(1) @Max(1000) Integer parentRevision) {
	}

	@PostMapping
	ResponseEntity<JsonNode> create(@Valid @RequestBody CreatePathRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
		PathOptions options = PathOptions.parse(request.experience(), request.focus(), request.budget(),
				PathOptions.DEFAULT);
		return respond(paths.create(request.topic(), options, idempotencyKey));
	}

	@GetMapping("/{pathId}")
	ResponseEntity<JsonNode> latest(@PathVariable String pathId) {
		return respond(paths.get(pathId, null));
	}

	@GetMapping("/{pathId}/revisions/{revision}")
	ResponseEntity<JsonNode> revision(@PathVariable String pathId, @PathVariable int revision) {
		return respond(paths.get(pathId, revision));
	}

	@PostMapping("/{pathId}/revisions")
	ResponseEntity<JsonNode> regenerate(@PathVariable String pathId, @Valid @RequestBody RegenerateRequest request) {
		PathService.PathView parent = paths.get(pathId, request.parentRevision());
		JsonNode o = parent.body().path("options");
		PathOptions base = PathOptions.parse(o.path("experience").asString(), o.path("focus").asString(),
				o.path("budget").asInt(), PathOptions.DEFAULT);
		PathOptions options = PathOptions.parse(request.experience(), request.focus(), request.budget(), base);
		return respond(paths.regenerate(pathId, options, parent.revision()));
	}

	private static ResponseEntity<JsonNode> respond(PathService.PathView view) {
		ResponseEntity.BodyBuilder builder = view.created()
				? ResponseEntity.created(URI.create("/api/paths/" + view.pathId() + "/revisions/" + view.revision()))
				: ResponseEntity.ok();
		return builder.cacheControl(CacheControl.noStore()).body(view.body());
	}

}
