package com.demod.fbsr.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import com.demod.factorio.fakelua.LuaTable;

/**
 * Steam engines and steam turbines draw their artwork whichever prototype layout the profile uses.
 *
 * <p>Factorio 2.0 put a generator's artwork in {@code vertical_animation} and
 * {@code horizontal_animation}. Factorio 2.1 moved it to {@code pictures.north.animation} and
 * {@code pictures.east.animation}. Reading only the old keys finds nothing on 2.1 and silently draws
 * no generator at all.
 */
public class GeneratorAnimationTest {

	@Test
	public void factorio21PicturesProvideTheVerticalAnimation() {
		LuaTable generator = new LuaTable(new JSONObject().put("pictures", new JSONObject()
				.put("north", new JSONObject().put("animation", animation("generator-V.png")))
				.put("east", new JSONObject().put("animation", animation("generator-H.png")))));

		assertEquals(animation("generator-V.png").toString(),
				GeneratorRendering.animation(generator, true).getJson().toString());
	}

	@Test
	public void factorio21PicturesProvideTheHorizontalAnimation() {
		LuaTable generator = new LuaTable(new JSONObject().put("pictures", new JSONObject()
				.put("north", new JSONObject().put("animation", animation("generator-V.png")))
				.put("east", new JSONObject().put("animation", animation("generator-H.png")))));

		assertEquals(animation("generator-H.png").toString(),
				GeneratorRendering.animation(generator, false).getJson().toString());
	}

	@Test
	public void factorio20AnimationsStillDraw() {
		LuaTable generator = new LuaTable(new JSONObject()
				.put("vertical_animation", animation("generator-V.png"))
				.put("horizontal_animation", animation("generator-H.png")));

		assertEquals(animation("generator-V.png").toString(),
				GeneratorRendering.animation(generator, true).getJson().toString());
		assertEquals(animation("generator-H.png").toString(),
				GeneratorRendering.animation(generator, false).getJson().toString());
	}

	@Test
	public void aGeneratorWithNoArtworkDrawsNothing() {
		LuaTable generator = new LuaTable(new JSONObject());

		assertTrue(GeneratorRendering.animation(generator, true).isnil());
		assertTrue(GeneratorRendering.animation(generator, false).isnil());
	}

	private static JSONObject animation(String filename) {
		return new JSONObject().put("filename", filename).put("width", 1).put("height", 1);
	}
}
