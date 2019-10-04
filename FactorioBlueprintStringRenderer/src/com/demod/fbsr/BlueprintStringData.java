package com.demod.fbsr;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.codec.binary.Base64;
import com.google.common.base.Charsets;

public class BlueprintStringData {
	private static String cleanupBlueprintString(String blueprintString) {
		// Remove new lines
		blueprintString = blueprintString.replaceAll("\\r|\\n", "");

		return blueprintString;
	}

	public static JsonNode decode(String blueprintString) throws IOException {
		blueprintString = cleanupBlueprintString(blueprintString);
		byte[] decoded = Base64.decodeBase64(blueprintString.substring(1));
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new InflaterInputStream(new ByteArrayInputStream(decoded)), Charsets.UTF_8))) {
			StringBuilder jsonBuilder = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				jsonBuilder.append(line);
			}
			ObjectMapper objectMapper = new ObjectMapper();
			return objectMapper.readTree(jsonBuilder.toString());
		}
	}

	public static String encode(ObjectNode json) throws IOException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
			dos.write(json.toString().getBytes());
			dos.close();
			return "0" + Base64.encodeBase64String(baos.toByteArray());
		}
	}

	private final List<Blueprint> blueprints = new ArrayList<>();

	private final JsonNode json;

	private final Optional<String> label;
	private final Optional<Long> version;

	public BlueprintStringData(String blueprintString) throws IllegalArgumentException, IOException {
		String versionChar = blueprintString.substring(0, 1);
		try {
			if (Integer.parseInt(versionChar) != 0) {
				throw new IllegalArgumentException("Only Version 0 is supported! (" + versionChar + ")");
			}
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Version is not valid! (" + versionChar + ")");
		}

		json = decode(blueprintString);

		if (json.has("blueprint")) {
			Blueprint blueprint = new Blueprint(json);
			blueprints.add(blueprint);

			label = blueprint.getLabel();
			version = blueprint.getVersion();
		} else {
			JsonNode bookJson = json.path("blueprint_book");
			ArrayNode blueprintsJson = (ArrayNode) bookJson.path("blueprints");
			for (int i = 0; i < blueprintsJson.size(); i++) {
				Blueprint blueprint = new Blueprint(blueprintsJson.path(i));
				blueprints.add(blueprint);
			}

			if (bookJson.has("label")) {
				label = Optional.of(bookJson.path("label").textValue());
			} else {
				label = Optional.empty();
			}
			if (bookJson.has("version")) {
				version = Optional.of(bookJson.path("version").longValue());
			} else {
				version = Optional.empty();
			}
		}

		if (blueprints.isEmpty()) {
			throw new IllegalArgumentException("No blueprints found in blueprint string!");
		}
	}

	public List<Blueprint> getBlueprints() {
		return blueprints;
	}

	public Optional<String> getLabel() {
		return label;
	}

	public Optional<Long> getVersion() {
		return version;
	}

	public boolean isBook() {
		return blueprints.size() > 1;
	}

	public JsonNode json() {
		return json;
	}
}
