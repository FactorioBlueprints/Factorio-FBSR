package com.demod.fbsr;

import java.awt.geom.Point2D;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.demod.factorio.Utils;

public class BlueprintEntity {
	private final JsonNode json;

	private final int id;
	private final String name;
	private final Point2D.Double position;
	private final Direction direction;

	public BlueprintEntity(JsonNode entityJson) {
		json = entityJson;

		JsonNode entityNumber = entityJson.path("entity_number");
		assert entityNumber.isIntegralNumber();
		id = entityNumber.intValue();
		JsonNode name = entityJson.path("name");
		assert name.isTextual();
		this.name = name.textValue();

		position = Utils.parsePoint2D((ObjectNode) entityJson.path("position"));

		int direction = entityJson.path("direction").asInt(0);
		this.direction = Direction.values()[direction];
	}

	public void debugPrint() {
		System.out.println();
		System.out.println(getName());
		Utils.debugPrintJson((ObjectNode) json);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BlueprintEntity other = (BlueprintEntity) obj;
		if (id != other.id)
			return false;
		return true;
	}

	public Direction getDirection() {
		return direction;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Point2D.Double getPosition() {
		return position;
	}

	@Override
	public int hashCode() {
		return id;
	}

	public ObjectNode json() {
		return (ObjectNode) json;
	}
}
