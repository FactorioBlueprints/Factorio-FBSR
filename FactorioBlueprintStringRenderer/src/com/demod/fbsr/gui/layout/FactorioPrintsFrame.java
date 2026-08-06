package com.demod.fbsr.gui.layout;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import com.demod.fbsr.gui.GUISize;

final class FactorioPrintsFrame {

	public static final GUISize IMAGE_SIZE = new GUISize(1100, 1100);
	public static final String WATERMARK = "factorioprints.com";
	public static final String BOT_CREDIT = "Blueprint Bot by Demodude4u";

	private FactorioPrintsFrame() {}

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
