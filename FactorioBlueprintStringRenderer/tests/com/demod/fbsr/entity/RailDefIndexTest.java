package com.demod.fbsr.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.demod.fbsr.Direction;

/**
 * Straight and half-diagonal rails define four rail pieces but accept all eight directions.
 *
 * <p>Factorio ships artwork for north, northeast, east and southeast only, because the shape is
 * unchanged by a half turn, and leaves the opposite four empty. A blueprint using one of those four
 * used to index past the end of the rail def list, and the resulting exception discarded the whole
 * render. Folding the direction is what makes both the rail piece and its artwork resolve.
 */
public class RailDefIndexTest {

	@Test
	public void everyDirectionFoldsOntoOneOfTheFourDefinedPieces() {
		for (Direction direction : Direction.values()) {
			int index = RailRendering.foldHalfTurn(direction).ordinal();
			assertTrue("Direction " + direction + " folded to index " + index
					+ ", which is outside the four pieces Factorio defines artwork for",
					index >= 0 && index < 4);
		}
	}

	@Test
	public void oppositeDirectionsAreTheSamePieceOfRail() {
		// A straight rail is unchanged by a half turn, so south is the same piece as north.
		assertEquals(RailRendering.foldHalfTurn(Direction.NORTH),
				RailRendering.foldHalfTurn(Direction.SOUTH));
		assertEquals(RailRendering.foldHalfTurn(Direction.NORTHEAST),
				RailRendering.foldHalfTurn(Direction.SOUTHWEST));
		assertEquals(RailRendering.foldHalfTurn(Direction.EAST),
				RailRendering.foldHalfTurn(Direction.WEST));
		assertEquals(RailRendering.foldHalfTurn(Direction.SOUTHEAST),
				RailRendering.foldHalfTurn(Direction.NORTHWEST));
	}

	@Test
	public void theFirstFourDirectionsKeepTheirExistingIndexes() {
		// These already worked; the fix must not renumber them.
		assertEquals(Direction.NORTH, RailRendering.foldHalfTurn(Direction.NORTH));
		assertEquals(Direction.NORTHEAST, RailRendering.foldHalfTurn(Direction.NORTHEAST));
		assertEquals(Direction.EAST, RailRendering.foldHalfTurn(Direction.EAST));
		assertEquals(Direction.SOUTHEAST, RailRendering.foldHalfTurn(Direction.SOUTHEAST));
	}
}
