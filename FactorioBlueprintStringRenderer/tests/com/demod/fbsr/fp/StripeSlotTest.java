package com.demod.fbsr.fp;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import com.demod.factorio.fakelua.LuaValue;
import com.google.common.collect.ImmutableList;

/**
 * Stripe sheets must fill every direction and frame the prototype declares, leaving no holes.
 *
 * <p>A hole is not a crash where it happens. It becomes one later, when a renderer that samples a
 * subset of directions lands on the missing slot, and the message names a direction far from the
 * sheet that failed to fill it.
 */
public class StripeSlotTest {

	@Test
	public void aRowOfFramesPerDirectionFillsEveryFrame() {
		// The vanilla character: 26 frames per direction, delivered as two 13-wide sheets.
		int[] slots = FPRotatedAnimation.stripeSlots(8, 26, ImmutableList.of(stripe(13, 8), stripe(13, 8)));

		assertEquals("cells", 208, slots.length);
		assertEquals("every direction and frame filled exactly once", 208, distinctUsedSlots(slots));
	}

	@Test
	public void aGridOfDirectionsFillsEveryDirection() {
		// The cargo-ships boat: one frame per direction, 256 rotations in a 16x16 sheet.
		int[] slots = FPRotatedAnimation.stripeSlots(256, 1, ImmutableList.of(stripe(16, 16)));

		assertEquals("cells", 256, slots.length);
		assertEquals("every direction filled exactly once", 256, distinctUsedSlots(slots));
	}

	@Test
	public void artworkBeyondWhatThePrototypeDeclaresIsUnused() {
		// 20 cells of artwork against 8 declared directions: the extra cells go nowhere.
		int[] slots = FPRotatedAnimation.stripeSlots(8, 1, ImmutableList.of(stripe(5, 4)));

		assertEquals("cells", 20, slots.length);
		assertEquals("only the declared directions are filled", 8, distinctUsedSlots(slots));
	}

	private static int distinctUsedSlots(int[] slots) {
		return (int) java.util.Arrays.stream(slots).filter(slot -> slot >= 0).distinct().count();
	}

	private static FPStripe stripe(int widthInFrames, int heightInFrames) {
		JSONObject json = new JSONObject();
		json.put("width_in_frames", widthInFrames);
		json.put("height_in_frames", heightInFrames);
		json.put("filename", "sheet.png");
		return new FPStripe(new LuaValue(json), java.util.OptionalInt.empty());
	}
}
