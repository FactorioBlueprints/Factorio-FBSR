package com.demod.fbsr.fp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.demod.factorio.fakelua.LuaValue;
import com.demod.fbsr.FPUtils;
import com.demod.fbsr.Profile;
import com.demod.fbsr.def.ImageDef;
import com.demod.fbsr.def.SpriteDef;
import com.google.common.collect.ImmutableList;

public class FPRotatedAnimation extends FPAnimationParameters {

	public final Optional<List<FPRotatedAnimation>> layers;
	public final int directionCount;
	public final Optional<List<FPStripe>> stripes;
	public final Optional<List<String>> filenames;
	public final int slice;
	public final int linesPerFile;
	public final boolean applyProjection;
	public final boolean counterclockwise;

	private final List<List<SpriteDef>> defs;
	private final int limitedDirectionCount;

	public FPRotatedAnimation(Profile profile, LuaValue lua) {
		this(profile, lua, Integer.MAX_VALUE);
	}

	public FPRotatedAnimation(Profile profile, LuaValue lua, int limitDirectionCount) {
		super(profile, lua);

		layers = FPUtils.optList(lua.get("layers"), l -> new FPRotatedAnimation(profile, l, limitDirectionCount));
		directionCount = lua.get("direction_count").optint(1);
		stripes = FPUtils.optList(lua.get("stripes"), l -> new FPStripe(l, OptionalInt.of(directionCount)));
		filenames = FPUtils.optList(lua.get("filenames"), LuaValue::toString);
		slice = lua.get("slice").optint(frameCount);
		linesPerFile = lua.get("lines_per_file").optint(0);
		applyProjection = lua.get("apply_projection").optboolean(true);
		counterclockwise = lua.get("counterclockwise").optboolean(false);

		this.limitedDirectionCount = Math.min(limitDirectionCount, directionCount);
		List<List<SpriteDef>> allDefs = createDefs(profile);
		defs = limitedDirectionDefs(allDefs);
		FPUtils.verifyNotNull(lua.getDebugPath() + " defs", defs);
	}

	/**
	 * Where each cell of each stripe sheet lands in the direction-major sprite array.
	 *
	 * <p>Cells are listed in reading order: every stripe in turn, each row left to right. A slot of
	 * -1 means the sheets supply more artwork than the prototype declares, so the cell is unused.
	 *
	 * <p>Vanilla gives a direction one row of animation frames, so a stripe's row selects the
	 * direction and its column selects the frame; the character's 26 frames arrive as two 13-wide
	 * stripes that way. A sheet with no animation frames at all cannot mean that, because there is
	 * only ever frame 0 to select. Such a sheet is a grid of directions read row by row, which is
	 * how cargo-ships packs a boat's 256 rotations into one 16x16 sheet.
	 */
	static int[] stripeSlots(int directionCount, int frameCount, List<FPStripe> stripes) {
		int cells = 0;
		for (FPStripe stripe : stripes) {
			cells += stripe.widthInFrames * stripe.heightInFrames;
		}

		int[] slots = new int[cells];
		int capacity = directionCount * frameCount;
		int cell = 0;
		int stripeRow = 0, stripeCol = 0;
		int direction = 0;
		for (FPStripe stripe : stripes) {
			for (int row = 0; row < stripe.heightInFrames; row++) {
				for (int col = 0; col < stripe.widthInFrames; col++) {
					int slot = frameCount == 1 ? direction++ : (stripeRow + row) * frameCount + (stripeCol + col);
					slots[cell++] = slot < capacity ? slot : -1;
				}
			}

			stripeCol += stripe.widthInFrames;
			if (stripeCol == frameCount) {
				stripeCol = 0;
				stripeRow += stripe.heightInFrames;
			}
		}
		return slots;
	}

	private List<List<SpriteDef>> createDefs(Profile profile) {
		if (layers.isPresent()) {
			return ImmutableList.of();
		}

		if (stripes.isPresent()) {

			SpriteDef[] defArray = new SpriteDef[directionCount * frameCount];
			int[] slots = stripeSlots(directionCount, frameCount, stripes.get());
			int cell = 0;
			for (FPStripe stripe : stripes.get()) {

				for (int row = 0; row < stripe.heightInFrames; row++) {
					for (int col = 0; col < stripe.widthInFrames; col++) {
						int x = stripe.x + width * col;
						int y = stripe.y + height * row;

						int slot = slots[cell++];
						if (slot < 0) {
							continue;
						}
						defArray[slot] = SpriteDef.fromFP(profile, stripe.filename, drawAsShadow,
								blendMode, tint, tintAsOverlay, applyRuntimeTint, x, y, width, height, shift.x, shift.y,
								scale);
					}
				}
			}

			List<List<SpriteDef>> defs = new ArrayList<>();
			for (int index = 0; index < directionCount; index++) {
				List<SpriteDef> dirDefs = new ArrayList<>();
				defs.add(dirDefs);
				for (int frame = 0; frame < frameCount; frame++) {
					dirDefs.add(defArray[index * frameCount + frame]);
				}
			}
			return defs;

		} else if (filenames.isPresent()) {

			List<List<SpriteDef>> defs = new ArrayList<>();
			for (int index = 0; index < directionCount; index++) {
				List<SpriteDef> dirDefs = new ArrayList<>();
				defs.add(dirDefs);
				for (int frame = 0; frame < frameCount; frame++) {

					int spriteIndex = index * frameCount + frame;
					int fileFrameCount = (linesPerFile * lineLength);
					int fileFrame = spriteIndex % fileFrameCount;
					int fileIndex = spriteIndex / fileFrameCount;
					int x = this.x + width * (fileFrame % lineLength);
					int y = this.y + height * (fileFrame / lineLength);

					dirDefs.add(SpriteDef.fromFP(profile, filenames.get().get(fileIndex), drawAsShadow, blendMode, tint,
							tintAsOverlay, applyRuntimeTint, x, y, width, height, shift.x, shift.y, scale));

				}
			}
			return defs;

		} else if (!filename.isPresent()) {
			// No stripes, no filenames and no filename: the prototype named no artwork at all.
			// A rail signal that sits on water has no rail_piece to draw, and that is not an error.
			return ImmutableList.of();

		} else {
			List<List<SpriteDef>> defs = new ArrayList<>();
			for (int index = 0; index < directionCount; index++) {
				List<SpriteDef> dirDefs = new ArrayList<>();
				defs.add(dirDefs);
				for (int frame = 0; frame < frameCount; frame++) {

					int spriteIndex = index * frameCount + frame;
					int x = this.x + width * (spriteIndex % lineLength);
					int y = this.y + height * (spriteIndex / lineLength);

					dirDefs.add(SpriteDef.fromFP(profile, filename.get(), drawAsShadow, blendMode, tint, tintAsOverlay,
							applyRuntimeTint, x, y, width, height, shift.x, shift.y, scale));
				}
			}
			return defs;
		}
	}

	private List<List<SpriteDef>> limitedDirectionDefs(List<List<SpriteDef>> allDefs) {
		if (limitedDirectionCount == directionCount || allDefs.isEmpty()) {
			return allDefs;
		}

		return IntStream.range(0, limitedDirectionCount).map(i -> (i * directionCount) / limitedDirectionCount)
				.mapToObj(allDefs::get).collect(Collectors.toList());
	}

	public void defineSprites(Consumer<? super SpriteDef> consumer, double orientation, int frame) {
		int index = getIndex(orientation);
		defineSprites(consumer, index, frame);
	}

	public void defineSprites(Consumer<? super SpriteDef> consumer, int index, int frame) {
		if (layers.isPresent()) {
			for (FPRotatedAnimation animation : layers.get()) {
				animation.defineSprites(consumer, index, frame);
			}
			return;
		}

		consumer.accept(defs.get(index).get(frame));
	}


	public List<SpriteDef> defineSprites(double orientation, int frame) {
		int index = getIndex(orientation);
		return defineSprites(index, frame);
	}

	public List<SpriteDef> defineSprites(int index, int frame) {
		List<SpriteDef> ret = new ArrayList<>();
		defineSprites(ret::add, index, frame);
		return ret;
	}

	public void getDefs(Consumer<ImageDef> consumer, int frame) {
		if (layers.isPresent()) {
			layers.get().forEach(fp -> fp.getDefs(consumer, frame));

		} else {
			for (int index = 0; index < limitedDirectionCount; index++) {
				defineSprites(consumer, index, frame);
			}
		}
	}

	public int getLimitedDirectionCount() {
		return limitedDirectionCount;
	}

	private int getIndex(double orientation) {
		if (counterclockwise) {
			orientation = 1 - orientation;
		}
		int directionCount = this.limitedDirectionCount;
		int index;
		if (applyProjection) {
			index = (int) (FPUtils.projectedOrientation(orientation) * directionCount);
		} else {
			index = (int) (orientation * directionCount);
		}
		return index;
	}
}
