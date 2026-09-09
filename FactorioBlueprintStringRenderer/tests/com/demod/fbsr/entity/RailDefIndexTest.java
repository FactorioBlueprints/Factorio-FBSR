package com.demod.fbsr.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.demod.fbsr.Direction;

/**
 * Straight and half-diagonal rails define four rail pieces but accept all eight directions.
 *
 * <p>A rail of either shape is unchanged by a half turn, so Factorio ships artwork for north,
 * northeast, east and southeast only and leaves the opposite four empty. A blueprint holding one of
 * those four used to index past the end of the rail def list, and the resulting exception discarded
 * the whole render.
 */
public class RailDefIndexTest {

	@Test
	public void straightRailResolvesEveryDirection() {
		StraightRailRendering rendering = new StraightRailRendering();
		for (Direction direction : Direction.values()) {
			assertNotNull("No rail piece for straight rail facing " + direction,
					rendering.getRailDef(direction));
		}
	}

	@Test
	public void halfDiagonalRailResolvesEveryDirection() {
		HalfDiagonalRailRendering rendering = new HalfDiagonalRailRendering();
		for (Direction direction : Direction.values()) {
			assertNotNull("No rail piece for half-diagonal rail facing " + direction,
					rendering.getRailDef(direction));
		}
	}

	@Test
	public void oppositeDirectionsAreTheSamePieceOfRail() {
		StraightRailRendering rendering = new StraightRailRendering();
		assertSame(rendering.getRailDef(Direction.NORTH), rendering.getRailDef(Direction.SOUTH));
		assertSame(rendering.getRailDef(Direction.NORTHEAST), rendering.getRailDef(Direction.SOUTHWEST));
		assertSame(rendering.getRailDef(Direction.EAST), rendering.getRailDef(Direction.WEST));
		assertSame(rendering.getRailDef(Direction.SOUTHEAST), rendering.getRailDef(Direction.NORTHWEST));
	}

	@Test
	public void artworkForOppositeDirectionsIsLookedUpUnderTheDefinedHalf() {
		// Factorio leaves the opposite four artwork entries empty, so the lookup has to fold too.
		StraightRailRendering rendering = new StraightRailRendering();
		assertEquals(Direction.NORTH, rendering.railDirection(Direction.SOUTH));
		assertEquals(Direction.NORTHEAST, rendering.railDirection(Direction.SOUTHWEST));
		assertEquals(Direction.EAST, rendering.railDirection(Direction.WEST));
		assertEquals(Direction.SOUTHEAST, rendering.railDirection(Direction.NORTHWEST));
	}

	@Test
	public void theFirstFourDirectionsKeepTheirExistingPieces() {
		// These already worked; the fix must not renumber them.
		StraightRailRendering rendering = new StraightRailRendering();
		for (Direction direction : new Direction[] { Direction.NORTH, Direction.NORTHEAST,
				Direction.EAST, Direction.SOUTHEAST }) {
			assertEquals(direction, rendering.railDirection(direction));
		}
	}
}
