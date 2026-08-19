package com.demod.fbsr;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The prototype renames that Factorio applies when it imports an old blueprint.
 *
 * <p>
 * A blueprint string records the prototype names that existed when it was exported, so a
 * blueprint saved before a rename still says {@code filter-inserter} where the current data has
 * {@code fast-inserter}. The game fixes those names up on import using the migration files in
 * {@code data/<mod>/migrations}. Without the same fix-up, every renamed prototype in an old
 * blueprint renders as unknown.
 *
 * <p>
 * The renames are read from the Factorio installation while a profile is built and stored in the
 * profile's asset package, so rendering itself still needs no installation.
 *
 * <p>
 * Each migration file is one step, and a name is migrated by running it through every step in
 * order. A step applies its renames <em>simultaneously</em> rather than one at a time, because
 * files are allowed to permute names: base's 1.2.0 migration swaps {@code stack-inserter} and
 * {@code bulk-inserter} with each other, and applying those two renames in sequence would send
 * both names to the same prototype. For the same reason a step never sees the output of a later
 * step, only of earlier ones.
 *
 * <p>
 * Migrating a name that the current data already knows is not safe -- the 1.2.0 swap above would
 * happily rewrite a modern blueprint's {@code bulk-inserter} into {@code stack-inserter}. Factorio
 * avoids this by skipping migrations older than the blueprint. Callers here should instead treat
 * migration as a fallback and only ask for one after a name has failed to resolve, which is
 * cheaper than tracking versions and cannot change a blueprint that already renders.
 */
public class FactorioMigrations {

	private static final String SECTION_ENTITY = "entity";
	private static final String SECTION_TILE = "tile";
	private static final String JSON_STEPS = "steps";
	private static final String JSON_SOURCE = "source";

	/** The renames of a single migration file, applied as one simultaneous substitution. */
	private static class Step {
		private final String source;
		private final Map<String, String> entityRenames;
		private final Map<String, String> tileRenames;

		private Step(String source, Map<String, String> entityRenames, Map<String, String> tileRenames) {
			this.source = source;
			this.entityRenames = entityRenames;
			this.tileRenames = tileRenames;
		}

		private boolean isEmpty() {
			return entityRenames.isEmpty() && tileRenames.isEmpty();
		}

		private JSONObject toJson() {
			JSONObject json = new JSONObject();
			json.put(JSON_SOURCE, source);
			json.put(SECTION_ENTITY, new JSONObject(entityRenames));
			json.put(SECTION_TILE, new JSONObject(tileRenames));
			return json;
		}

		private static Step fromJson(JSONObject json) {
			return new Step(json.optString(JSON_SOURCE, ""), readSection(json.opt(SECTION_ENTITY)),
					readSection(json.opt(SECTION_TILE)));
		}
	}

	private final List<Step> steps;

	private FactorioMigrations(List<Step> steps) {
		this.steps = steps;
	}

	public static FactorioMigrations empty() {
		return new FactorioMigrations(Collections.emptyList());
	}

	/**
	 * Reads every {@code data/<mod>/migrations/*.json} in the installation. Mods and their files
	 * are read in name order, which is the order Factorio itself applies them in.
	 */
	public static FactorioMigrations fromFactorioInstall(File factorioInstall) {
		List<Step> steps = new ArrayList<>();
		File[] modFolders = new File(factorioInstall, "data").listFiles(File::isDirectory);
		if (modFolders == null) {
			return empty();
		}
		Arrays.sort(modFolders);
		for (File modFolder : modFolders) {
			File[] migrationFiles = new File(modFolder, "migrations")
					.listFiles(file -> file.getName().endsWith(".json"));
			if (migrationFiles == null) {
				continue;
			}
			Arrays.sort(migrationFiles);
			for (File migrationFile : migrationFiles) {
				String source = modFolder.getName() + "/" + migrationFile.getName();
				JSONObject json;
				try {
					json = new JSONObject(
							new String(Files.readAllBytes(migrationFile.toPath()), StandardCharsets.UTF_8));
				} catch (IOException | RuntimeException e) {
					System.out.println("Skipping unreadable migration file " + source + ": " + e.getMessage());
					continue;
				}
				Step step = new Step(source, readSection(json.opt(SECTION_ENTITY)),
						readSection(json.opt(SECTION_TILE)));
				if (!step.isEmpty()) {
					steps.add(step);
				}
			}
		}
		return new FactorioMigrations(steps);
	}

	/** Migration files write renames either as an array of old-new pairs, or as an object. */
	private static Map<String, String> readSection(Object section) {
		Map<String, String> renames = new LinkedHashMap<>();
		if (section instanceof JSONArray) {
			JSONArray jsonRenames = (JSONArray) section;
			for (int i = 0; i < jsonRenames.length(); i++) {
				JSONArray pair = jsonRenames.optJSONArray(i);
				if (pair != null && pair.length() == 2) {
					renames.put(pair.getString(0), pair.getString(1));
				}
			}
		} else if (section instanceof JSONObject) {
			JSONObject jsonRenames = (JSONObject) section;
			for (String oldName : jsonRenames.keySet()) {
				String newName = jsonRenames.optString(oldName, null);
				if (newName != null && !newName.isEmpty()) {
					renames.put(oldName, newName);
				}
			}
		}
		return renames;
	}

	public static FactorioMigrations fromJson(JSONObject json) {
		List<Step> steps = new ArrayList<>();
		JSONArray jsonSteps = json.optJSONArray(JSON_STEPS);
		if (jsonSteps != null) {
			for (int i = 0; i < jsonSteps.length(); i++) {
				steps.add(Step.fromJson(jsonSteps.getJSONObject(i)));
			}
		}
		return new FactorioMigrations(steps);
	}

	public JSONObject toJson() {
		JSONArray jsonSteps = new JSONArray();
		for (Step step : steps) {
			jsonSteps.put(step.toJson());
		}
		return new JSONObject().put(JSON_STEPS, jsonSteps);
	}

	/**
	 * The current name of an entity that a blueprint knows by an old name, or empty when no
	 * migration renames it.
	 */
	public Optional<String> migrateEntityName(String entityName) {
		return migrate(entityName, step -> step.entityRenames);
	}

	public Optional<String> migrateTileName(String tileName) {
		return migrate(tileName, step -> step.tileRenames);
	}

	private Optional<String> migrate(String name, java.util.function.Function<Step, Map<String, String>> section) {
		String current = name;
		for (Step step : steps) {
			String renamed = section.apply(step).get(current);
			if (renamed != null) {
				current = renamed;
			}
		}
		return current.equals(name) ? Optional.empty() : Optional.of(current);
	}

	public boolean isEmpty() {
		return steps.isEmpty();
	}
}
