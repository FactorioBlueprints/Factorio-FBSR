package com.demod.fbsr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONArray;
import org.json.JSONObject;

import com.demod.factorio.Utils;

public class Blueprint {

	private final JsonNode json;

	private final List<BlueprintEntity> entities = new ArrayList<>();
	private final List<BlueprintTile> tiles = new ArrayList<>();
	private Optional<String> label;
	private Optional<Long> version;
	private Optional<ArrayNode> icons;

	public Blueprint(JsonNode json) throws IllegalArgumentException, IOException {
		this.json = json;

		ObjectNode blueprintJson = (ObjectNode) json.path("blueprint");

		if (blueprintJson.has("entities")) {
			ArrayNode entities = (ArrayNode) blueprintJson.path("entities");
			for (JsonNode entity : entities) {
				this.entities.add(new BlueprintEntity(entity));
			};
		}

		if (blueprintJson.has("tiles")) {
			JsonNode tiles = blueprintJson.path("tiles");
			for (JsonNode tile : tiles) {
				this.tiles.add(new BlueprintTile(tile));
			};
		}

		if (blueprintJson.has("label")) {
			label = Optional.of(blueprintJson.path("label").textValue());
		} else {
			label = Optional.empty();
		}

		if (blueprintJson.has("icons")) {
			ArrayNode icons = (ArrayNode) blueprintJson.path("icons");
			this.icons = Optional.of(icons);
		} else {
			icons = Optional.empty();
		}

		if (blueprintJson.has("version")) {
			version = Optional.of(blueprintJson.path("version").longValue());
		} else {
			version = Optional.empty();
		}
	}

	public List<BlueprintEntity> getEntities() {
		return entities;
	}

	public Optional<ArrayNode> getIcons() {
		return icons;
	}

	public Optional<String> getLabel() {
		return label;
	}

	public List<BlueprintTile> getTiles() {
		return tiles;
	}

	public Optional<Long> getVersion() {
		return version;
	}

	public ObjectNode json() {
		return (ObjectNode) json;
	}

	public void setIcons(Optional<ArrayNode> icons) {
		this.icons = icons;
	}

	public void setLabel(Optional<String> label) {
		this.label = label;
	}

}
