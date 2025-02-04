package com.demod.fbsr.fp;

import org.luaj.vm2.LuaValue;

public class FPWireConnectionPoint {
	public final FPWirePosition wire;
	public final FPWirePosition shadow;

	public FPWireConnectionPoint(LuaValue lua) {
		wire = new FPWirePosition(lua.get("wire"));
		shadow = new FPWirePosition(lua.get("shadow"));
	}
}
