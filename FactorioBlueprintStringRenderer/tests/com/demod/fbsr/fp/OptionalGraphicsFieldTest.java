package com.demod.fbsr.fp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.demod.factorio.fakelua.LuaValue;
import com.demod.fbsr.def.ImageDef;
import com.demod.fbsr.def.SpriteDef;

/**
 * Prototype graphics fields that vanilla always defines are still optional, and mods omit them.
 *
 * <p>Every entity below is one a Factorio mod actually ships: a locomotive with no pictures, a gate
 * that draws only its rail animations, a train stop with no rail overlay. FBSR read each field as
 * though it were mandatory, so one absent field threw and took the whole profile's asset build down
 * with it. Absent artwork should draw nothing.
 */
public class OptionalGraphicsFieldTest {

	private static final int FRAME = 0;

	@Test
	public void animationWithoutADefinitionDrawsNothing() {
		// A gate that defines only horizontal_rail_animation_left and friends: vertical_animation
		// is absent, and Factorio treats that as "nothing to draw" rather than an error.
		FPAnimation animation = new FPAnimation(null, LuaValue.NIL);

		assertTrue("an absent animation should contribute no sprites", sprites(animation).isEmpty());
	}

	@Test
	public void fourWayAnimationWithoutADefinitionDrawsNothing() {
		// A train stop with animations but no rail_overlay_animations.
		FPAnimation4Way animation = new FPAnimation4Way(null, LuaValue.NIL);

		List<SpriteDef> defs = new ArrayList<>();
		animation.getDefs((ImageDef image) -> defs.add((SpriteDef) image), FRAME);

		assertTrue("an absent 4-way animation should contribute no sprites", defs.isEmpty());
	}

	@Test
	public void rotatedSpriteWithoutADefinitionDrawsNothing() {
		// A locomotive with no pictures at all, because its hull is a separate entity.
		FPRotatedSprite sprite = new FPRotatedSprite(null, LuaValue.NIL, 32);

		List<SpriteDef> defs = new ArrayList<>();
		sprite.getDefs((ImageDef image) -> defs.add((SpriteDef) image));

		assertTrue("an absent rotated sprite should contribute no sprites", defs.isEmpty());
	}

	@Test
	public void railSignalWithoutARailPieceDrawsNothing() {
		// A buoy floats on water, so it has a structure but no rail_piece under it. Asking that
		// absent layer which frame to draw at an alignment has no answer, and that is not an error.
		FPRailSignalPictureSet pictureSet = new FPRailSignalPictureSet(null, LuaValue.NIL);

		assertFalse("an absent rail piece should name no frame",
				pictureSet.railPiece.frameIndexFor(0).isPresent());
	}

	private static List<SpriteDef> sprites(FPAnimation animation) {
		List<SpriteDef> defs = new ArrayList<>();
		animation.defineSprites(defs::add, FRAME);
		return defs;
	}
}
