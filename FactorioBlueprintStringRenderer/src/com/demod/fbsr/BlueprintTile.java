package com.demod.fbsr;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.demod.factorio.Utils;

public class BlueprintTile {
	private final JsonNode json;

	private final String name;
	private final Double position;

	public BlueprintTile(JsonNode entityJson) {
		json = entityJson;

		JsonNode name = entityJson.path("name");
		assert name.isTextual();
		this.name = name.textValue();

		JsonNode positionJson = entityJson.path("position");
		JsonNode x = positionJson.path("x");
		JsonNode y = positionJson.path("y");
		assert x.isFloatingPointNumber();
		assert y.isFloatingPointNumber();
		position = new Point2D.Double(x.doubleValue(), y.doubleValue());
	}

	public void debugPrint() {
		System.out.println();
		System.out.println(getName());
		Utils.debugPrintJson((ObjectNode) json);
	}

	public String getName() {
		return name;
	}

	public Double getPosition() {
		return position;
	}

	public ObjectNode json() {
		return (ObjectNode) json;
	}
}
