package com.demod.fbsr.gui.layout;

import java.util.ArrayList;
import java.util.List;

import com.demod.fbsr.bs.BSBlueprint;

final class BlueprintBookPreviewSelector {
	static final int MAX_BLUEPRINTS = 48;
	static final long MAX_COMPONENTS = 25_000;
	static final long MAX_COMPONENTS_PER_BLUEPRINT = 10_000;

	record Selection(List<BSBlueprint> blueprints, int totalBlueprintCount, long selectedComponents) {
		Selection {
			blueprints = List.copyOf(blueprints);
		}

		boolean isSampled() {
			return blueprints.size() < totalBlueprintCount;
		}
	}

	record IndexSelection(List<Integer> indices, int totalBlueprintCount, long selectedComponents) {
		IndexSelection {
			indices = List.copyOf(indices);
		}
	}

	private BlueprintBookPreviewSelector() {
		throw new AssertionError("Suppress default constructor for noninstantiability");
	}

	static Selection select(List<BSBlueprint> blueprints) {
		List<Long> complexities = blueprints.stream()
			.map(blueprint -> (long) blueprint.entities.size() + blueprint.tiles.size())
			.toList();
		IndexSelection indexSelection = selectIndices(
			complexities,
			MAX_BLUEPRINTS,
			MAX_COMPONENTS,
			MAX_COMPONENTS_PER_BLUEPRINT
		);
		List<BSBlueprint> selectedBlueprints = indexSelection.indices().stream().map(blueprints::get).toList();
		return new Selection(
			selectedBlueprints,
			indexSelection.totalBlueprintCount(),
			indexSelection.selectedComponents()
		);
	}

	static IndexSelection selectIndices(
		List<Long> complexities,
		int maximumBlueprints,
		long maximumComponents,
		long maximumComponentsPerBlueprint
	) {
		if (complexities.isEmpty()) {
			throw new IllegalArgumentException("Blueprint book does not contain any blueprints.");
		}
		if (maximumBlueprints <= 0 || maximumComponents <= 0 || maximumComponentsPerBlueprint <= 0) {
			throw new IllegalArgumentException("Blueprint preview limits must be positive.");
		}

		int totalBlueprintCount = complexities.size();
		int candidateCount = Math.min(maximumBlueprints, totalBlueprintCount);
		List<Integer> selectedIndices = new ArrayList<>();
		long selectedComponents = 0;

		for (int candidate = 0; candidate < candidateCount; candidate++) {
			int index = candidateCount == 1
				? 0
				: (int) Math.round(candidate * (totalBlueprintCount - 1.0) / (candidateCount - 1.0));
			long complexity = complexities.get(index);
			if (complexity > maximumComponentsPerBlueprint || selectedComponents + complexity > maximumComponents) {
				continue;
			}
			selectedIndices.add(index);
			selectedComponents += complexity;
		}

		if (selectedIndices.isEmpty()) {
			throw new IllegalArgumentException("Blueprint book is too complex to preview safely.");
		}

		return new IndexSelection(selectedIndices, totalBlueprintCount, selectedComponents);
	}
}
