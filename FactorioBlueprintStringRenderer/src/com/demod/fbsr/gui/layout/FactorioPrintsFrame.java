package com.demod.fbsr.gui.layout;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import com.demod.fbsr.ModdingResolver;
import com.demod.fbsr.gui.GUIAlign;
import com.demod.fbsr.gui.GUIBox;
import com.demod.fbsr.gui.GUISize;
import com.demod.fbsr.gui.part.GUIRichText;

final class FactorioPrintsFrame {

	public static final GUISize IMAGE_SIZE = new GUISize(1100, 1100);
	public static final String WATERMARK = "factorioprints.com";
	public static final String BOT_CREDIT = "Blueprint Bot by Demodude4u";

	private static final float MIN_TITLE_FONT_SIZE = 12f;
	private static final String ELLIPSIS = "...";

	private FactorioPrintsFrame() {}

	// A website title is user-authored and can be far longer than a blueprint label, so it
	// has to be fitted to the space left of the icons: shrink the font first, then truncate.
	public static GUIRichText fitTitle(Graphics2D g, GUIBox box, String title, Font baseFont, Color color,
			ModdingResolver resolver, double availableWidth) {
		GUIRichText label = new GUIRichText(box, title, baseFont, color, GUIAlign.CENTER_LEFT, resolver);
		if (availableWidth <= 0 || label.getTextWidth(g) <= availableWidth) {
			return label;
		}

		float size = baseFont.getSize2D();
		while (size > MIN_TITLE_FONT_SIZE && label.getTextWidth(g) > availableWidth) {
			size = Math.max(MIN_TITLE_FONT_SIZE, size - 1f);
			label = new GUIRichText(box, title, baseFont.deriveFont(size), color, GUIAlign.CENTER_LEFT, resolver);
		}

		Font font = baseFont.deriveFont(size);
		String truncated = title;
		while (!truncated.isEmpty() && label.getTextWidth(g) > availableWidth) {
			truncated = dropLastCharacterOrTag(truncated);
			label = new GUIRichText(box, truncated + ELLIPSIS, font, color, GUIAlign.CENTER_LEFT, resolver);
		}
		return label;
	}

	// Cutting inside a [item=...] tag would render its raw text, so step back past the tag.
	private static String dropLastCharacterOrTag(String text) {
		int end = text.length() - 1;
		int open = text.lastIndexOf('[', end - 1);
		if (open > text.lastIndexOf(']', end - 1)) {
			end = open;
		}
		return text.substring(0, Math.max(0, end)).stripTrailing();
	}

	public static Optional<String> chooseTitle(Optional<String> websiteTitle, Optional<String> blueprintTitle) {
		return Stream.of(websiteTitle, blueprintTitle)
			.flatMap(Optional::stream)
			.map(String::strip)
			.filter(title -> !title.isEmpty())
			.filter(title -> !isPlaceholderTitle(title))
			.findFirst();
	}

	private static boolean isPlaceholderTitle(String title) {
		return switch (title.toLowerCase(Locale.ROOT)) {
			case "untitled blueprint", "untitled blueprint book" -> true;
			default -> false;
		};
	}
}
