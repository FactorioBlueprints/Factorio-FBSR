package com.demod.fbsr.entity;

import org.luaj.vm2.LuaValue;

import com.demod.fbsr.bs.BSEntity;

public class LampRendering extends SimpleEntityRendering<BSEntity> {

	// TODO check if I can get the color and show the lit colored state

	@Override
	public void defineEntity(Bindings bind, LuaValue lua) {
		bind.sprite(lua.get("picture_off"));
		bind.circuitConnector(lua.get("circuit_connector"));
	}
}
