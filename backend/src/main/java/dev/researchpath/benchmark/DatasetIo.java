package dev.researchpath.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and writes benchmark datasets as stable, pretty-printed JSON (LF line endings) and computes the
 * SHA-256 recorded with every benchmark run. Spring-free so the offline runner can use it.
 */
public final class DatasetIo {

	public static final JsonMapper JSON = JsonMapper.builder()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.build();

	private DatasetIo() {
	}

	public static String toJson(BenchmarkDataset dataset) {
		return JSON.writeValueAsString(dataset).replace("\r\n", "\n") + "\n";
	}

	public static BenchmarkDataset fromJson(String json) {
		BenchmarkDataset dataset = JSON.readValue(json, BenchmarkDataset.class);
		if (dataset.formatVersion() != BenchmarkDataset.FORMAT_VERSION) {
			throw new IllegalArgumentException("Unsupported dataset format version " + dataset.formatVersion());
		}
		return dataset;
	}

	public static void write(BenchmarkDataset dataset, Path file) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.writeString(file, toJson(dataset), StandardCharsets.UTF_8);
	}

	public static BenchmarkDataset read(Path file) throws IOException {
		return fromJson(Files.readString(file, StandardCharsets.UTF_8));
	}

	/** SHA-256 of the file bytes with line endings normalized to LF (stable across Git checkouts). */
	public static String sha256(Path file) throws IOException {
		byte[] bytes = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n")
				.getBytes(StandardCharsets.UTF_8);
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
