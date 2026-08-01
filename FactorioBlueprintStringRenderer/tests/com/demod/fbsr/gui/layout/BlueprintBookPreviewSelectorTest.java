package com.demod.fbsr.gui.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.Test;

import com.demod.fbsr.gui.layout.BlueprintBookPreviewSelector.IndexSelection;

public class BlueprintBookPreviewSelectorTest {
	@Test
	public void samplesEvenlyAcrossLargeBooks() {
		List<Long> complexities = IntStream.range(0, 100).mapToObj(ignored -> 1L).toList();

		IndexSelection actual = BlueprintBookPreviewSelector.selectIndices(complexities, 48, 100, 1);
		IndexSelection expected = new IndexSelection(
			List.of(
				0, 2, 4, 6, 8, 11, 13, 15,
				17, 19, 21, 23, 25, 27, 29, 32,
				34, 36, 38, 40, 42, 44, 46, 48,
				51, 53, 55, 57, 59, 61, 63, 65,
				67, 70, 72, 74, 76, 78, 80, 82,
				84, 86, 88, 91, 93, 95, 97, 99
			),
			100,
			48
		);

		assertEquals(expected, actual);
	}

	@Test
	public void respectsTotalAndPerBlueprintComplexityLimits() {
		IndexSelection actual = BlueprintBookPreviewSelector.selectIndices(List.of(1L, 3L, 4L, 2L, 1L), 5, 5, 3);
		IndexSelection expected = new IndexSelection(List.of(0, 1, 4), 5, 5);

		assertEquals(expected, actual);
	}

	@Test
	public void rejectsBooksWithoutASafePreviewCandidate() {
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> BlueprintBookPreviewSelector.selectIndices(List.of(4L), 1, 3, 3)
		);

		assertEquals("Blueprint book is too complex to preview safely.", exception.getMessage());
	}
}
