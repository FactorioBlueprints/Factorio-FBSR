package com.demod.fbsr;

import static org.junit.Assert.assertEquals;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.junit.Test;

/**
 * Trimming must survive a sprite rectangle that reaches past its sheet.
 *
 * <p>Factorio rejects such a sprite when the game loads its graphics, but {@code --dump-data} never
 * loads them, so the prototype reaches the asset build anyway. factorioplus-assets-turrets 1.0.0
 * declares its pistol-turret shadow as 64 directions in one column of a sheet that holds only 8
 * rows, and a single bad rectangle must not abort every profile built after it.
 */
public class TrimEmptyRectTest {

	@Test
	public void aRectStartingBelowTheSheetTrimsToOneEmptyPixel() {
		// The pistol-turret shadow: direction 8 of 64 in a 128px column on a 1024x1024 sheet.
		BufferedImage sheet = sheetWithOpaquePixel(1024, 1024, 0, 0);

		Rectangle trimmed = AtlasPackage.trimEmptyRect(sheet, new Rectangle(0, 1024, 128, 128));

		assertEquals(new Rectangle(0, 1024, 1, 1), trimmed);
	}

	@Test
	public void aRectOverhangingTheSheetTrimsWithinTheVisiblePart() {
		BufferedImage sheet = sheetWithOpaquePixel(100, 100, 95, 95);

		Rectangle trimmed = AtlasPackage.trimEmptyRect(sheet, new Rectangle(90, 90, 20, 20));

		assertEquals(new Rectangle(95, 95, 1, 1), trimmed);
	}

	@Test
	public void aRectInsideTheSheetTrimsToItsOpaquePixels() {
		BufferedImage sheet = sheetWithOpaquePixel(64, 64, 40, 20);

		Rectangle trimmed = AtlasPackage.trimEmptyRect(sheet, new Rectangle(32, 0, 32, 32));

		assertEquals(new Rectangle(40, 20, 1, 1), trimmed);
	}

	private static BufferedImage sheetWithOpaquePixel(int width, int height, int x, int y) {
		BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		sheet.setRGB(x, y, 0xFFFFFFFF);
		return sheet;
	}
}
