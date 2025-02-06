package com.demod.fbsr.entity;

import com.demod.factorio.fakelua.LuaTable;
import com.demod.fbsr.Layer;
import com.demod.fbsr.bs.BSEntity;

public class RocketSiloRendering extends SimpleEntityRendering<BSEntity> {
	@Override
	public void defineEntity(Bindings bind, LuaTable lua) {
		bind.sprite(lua.get("shadow_sprite"));
		bind.sprite(lua.get("door_front_sprite"));
		bind.sprite(lua.get("door_back_sprite"));
		bind.sprite(lua.get("base_day_sprite")).layer(Layer.HIGHER_OBJECT_UNDER);
	}
}
