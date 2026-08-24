package com.demod.fbsr;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The prototype renames that Factorio applies when it imports an old blueprint.
 *
 * <p>
 * A blueprint string records the prototype names that existed when it was exported, so a
 * blueprint saved before a rename still says {@code filter-inserter} where the current data has
 * {@code fast-inserter}. The game fixes those names up on import using the migration files in
 * {@code data/<mod>/migrations}, and mods ship migrations of their own the same way. Without the
 * same fix-up, every renamed prototype in an old blueprint renders as unknown.
 *
 * <p>
 * The renames are read from the Factorio installation and from the profile's mods while a profile
 * is built, and stored in the profile's asset package, so rendering itself still needs neither an
 * installation nor the mods.
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

	// The sections that name something a blueprint can contain. Migration files also rename
	// recipes, technologies and the like, which are of no use when rendering one.
	private static final String SECTION_ENTITY = "entity";
	private static final String SECTION_TILE = "tile";
	private static final String SECTION_ITEM = "item";
	private static final List<String> SECTIONS = List.of(SECTION_ENTITY, SECTION_TILE, SECTION_ITEM);

	private static final String JSON_STEPS = "steps";
	private static final String JSON_SOURCE = "source";

	private static final String MIGRATION_SUFFIX = ".json";

	/** Migrations of a mod zip live one folder down, in a folder named for the mod. */
	private static final String MOD_ZIP_MIGRATION_PATH = "[^/]+/migrations/[^/]+";

	/** The renames of a single migration file, applied as one simultaneous substitution. */
	private static class Step {
		private final String source;
		private final Map<String, Map<String, String>> renamesBySection;

		private Step(String source, Map<String, Map<String, String>> renamesBySection) {
			this.source = source;
			this.renamesBySection = renamesBySection;
		}

		private static Step read(String source, JSONObject json) {
			Map<String, Map<String, String>> renamesBySection = new LinkedHashMap<>();
			for (String section : SECTIONS) {
				Map<String, String> renames = readSection(json.opt(section));
				if (!renames.isEmpty()) {
					renamesBySection.put(section, renames);
				}
			}
			return new Step(source, renamesBySection);
		}

		private boolean isEmpty() {
			return renamesBySection.isEmpty();
		}

		private String rename(String section, String name) {
			return renamesBySection.getOrDefault(section, Map.of()).get(name);
		}

		private JSONObject toJson() {
			JSONObject json = new JSONObject();
			json.put(JSON_SOURCE, source);
			renamesBySection.forEach((section, renames) -> json.put(section, new JSONObject(renames)));
			return json;
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
		for (File modFolder : listSorted(new File(factorioInstall, "data"), File::isDirectory)) {
			readModFolder(modFolder.getName(), new File(modFolder, "migrations"), steps);
		}
		return new FactorioMigrations(steps);
	}

	/**
	 * Reads the migrations of every mod in a mods folder, whether the mod is a zip or unpacked.
	 * Mods carry far more renames than the game itself does.
	 */
	public static FactorioMigrations fromMods(File folderMods) {
		List<Step> steps = new ArrayList<>();
		for (File mod : listSorted(folderMods, file -> true)) {
			if (mod.isDirectory()) {
				readModFolder(mod.getName(), new File(mod, "migrations"), steps);
			} else if (mod.getName().endsWith(".zip")) {
				readModZip(mod, steps);
			}
		}
		return new FactorioMigrations(steps);
	}

	private static void readModFolder(String modName, File folderMigrations, List<Step> steps) {
		for (File migrationFile : listSorted(folderMigrations, file -> file.getName().endsWith(MIGRATION_SUFFIX))) {
			String source = modName + "/" + migrationFile.getName();
			try {
				addStep(steps, source, Files.readAllBytes(migrationFile.toPath()));
			} catch (IOException | RuntimeException e) {
				reportSkipped(source, e);
			}
		}
	}

	/**
	 * A mod zip holds a single top level folder named for the mod, which some mods version and
	 * others do not, so the migrations are found by shape rather than by a known path.
	 */
	private static void readModZip(File fileMod, List<Step> steps) {
		try (ZipFile zipFile = new ZipFile(fileMod)) {
			List<? extends ZipEntry> entries = zipFile.stream()
					.filter(entry -> entry.getName().endsWith(MIGRATION_SUFFIX))
					.filter(entry -> entry.getName().matches(MOD_ZIP_MIGRATION_PATH))
					.sorted(Comparator.comparing(ZipEntry::getName))
					.toList();
			for (ZipEntry entry : entries) {
				String source = fileMod.getName() + ":" + entry.getName();
				try (InputStream is = zipFile.getInputStream(entry)) {
					addStep(steps, source, is.readAllBytes());
				} catch (IOException | RuntimeException e) {
					reportSkipped(source, e);
				}
			}
		} catch (IOException | RuntimeException e) {
			reportSkipped(fileMod.getName(), e);
		}
	}

	private static void addStep(List<Step> steps, String source, byte[] contents) {
		Step step = Step.read(source, new JSONObject(new String(contents, StandardCharsets.UTF_8)));
		if (!step.isEmpty()) {
			steps.add(step);
		}
	}

	private static void reportSkipped(String source, Exception e) {
		System.out.println("Skipping unreadable migrations in " + source + ": " + e.getMessage());
	}

	private static File[] listSorted(File folder, FileFilter filter) {
		File[] files = folder.listFiles(filter);
		if (files == null) {
			return new File[0];
		}
		Arrays.sort(files);
		return files;
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

	/** The renames of this followed by those of {@code later}. Neither side is modified. */
	public FactorioMigrations andThen(FactorioMigrations later) {
		List<Step> combined = new ArrayList<>(steps);
		combined.addAll(later.steps);
		return new FactorioMigrations(combined);
	}

	public static FactorioMigrations fromJson(JSONObject json) {
		List<Step> steps = new ArrayList<>();
		JSONArray jsonSteps = json.optJSONArray(JSON_STEPS);
		if (jsonSteps != null) {
			for (int i = 0; i < jsonSteps.length(); i++) {
				JSONObject jsonStep = jsonSteps.getJSONObject(i);
				steps.add(Step.read(jsonStep.optString(JSON_SOURCE, ""), jsonStep));
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
		return migrate(SECTION_ENTITY, entityName);
	}

	public Optional<String> migrateTileName(String tileName) {
		return migrate(SECTION_TILE, tileName);
	}

	public Optional<String> migrateItemName(String itemName) {
		return migrate(SECTION_ITEM, itemName);
	}

	private Optional<String> migrate(String section, String name) {
		String current = name;
		for (Step step : steps) {
			String renamed = step.rename(section, current);
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
