package com.demod.fbsr.entity;

import org.luaj.vm2.LuaValue;

import com.demod.fbsr.bs.BSEntity;

public class AsteroidCollectorRendering extends SimpleEntityRendering<BSEntity> {

	@Override
	public void defineEntity(Bindings bind, LuaValue lua) {
		bind.animation4Way(lua.get("graphics_set").get("animation"));
		bind.circuitConnector4Way(lua.get("circuit_connector"));
	}

}
