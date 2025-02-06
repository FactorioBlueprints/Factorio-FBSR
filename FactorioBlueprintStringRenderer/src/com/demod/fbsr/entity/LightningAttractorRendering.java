package com.demod.fbsr.entity;

import com.demod.factorio.fakelua.LuaTable;
import com.demod.fbsr.bs.BSEntity;

public class LightningAttractorRendering extends SimpleEntityRendering<BSEntity> {
	@Override
	public void defineEntity(Bindings bind, LuaTable lua) {
		bind.sprite(lua.get("chargable_graphics").get("picture"));
	}
}
