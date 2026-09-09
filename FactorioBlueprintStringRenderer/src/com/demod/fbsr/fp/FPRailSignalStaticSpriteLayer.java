package com.demod.fbsr.fp;

import java.util.List;
import java.util.OptionalInt;

import com.demod.factorio.fakelua.LuaValue;
import com.demod.fbsr.FPUtils;
import com.demod.fbsr.Profile;

public class FPRailSignalStaticSpriteLayer {
	public final FPAnimation sprites;
	public final List<Integer> alignToFrameIndex;

	public FPRailSignalStaticSpriteLayer(Profile profile, LuaValue lua) {
		sprites = new FPAnimation(profile, lua.get("sprites"));
		alignToFrameIndex = FPUtils.list(lua.get("align_to_frame_index"), LuaValue::toint);
	}

	/**
	 * The frame this layer draws at the given alignment, or empty when the layer is not defined.
	 *
	 * <p>A signal that sits on water rather than on track has no rail_piece, which leaves this
	 * layer with no alignments at all. Callers skip it instead of indexing an empty list.
	 */
	public OptionalInt frameIndexFor(int align) {
		if (align < 0 || align >= alignToFrameIndex.size()) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(alignToFrameIndex.get(align));
	}
}