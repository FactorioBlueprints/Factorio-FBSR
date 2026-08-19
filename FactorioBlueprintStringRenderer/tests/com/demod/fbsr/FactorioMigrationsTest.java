package com.demod.fbsr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FactorioMigrationsTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void readsRenamesWrittenAsPairs() throws IOException {
		FactorioMigrations migrations = install(
				migration("base", "2.0.0.json", "{\"entity\":[[\"filter-inserter\",\"fast-inserter\"]]}"));

		assertEquals(Optional.of("fast-inserter"), migrations.migrateEntityName("filter-inserter"));
	}

	@Test
	public void readsRenamesWrittenAsAnObject() throws IOException {
		FactorioMigrations migrations = install(
				migration("some-mod", "1.0.0.json", "{\"tile\":{\"old-floor\":\"new-floor\"}}"));

		assertEquals(Optional.of("new-floor"), migrations.migrateTileName("old-floor"));
	}

	/**
	 * Factorio's 1.2.0 migration swaps the stack-inserter and bulk-inserter names in a single
	 * file. Applying its renames one after another would send both names to the same place.
	 */
	@Test
	public void appliesTheRenamesWithinOneFileSimultaneously() throws IOException {
		FactorioMigrations migrations = install(migration("base", "1.2.0 stack inserter rename.json",
				"{\"entity\":[[\"stack-inserter\",\"bulk-inserter\"],[\"bulk-inserter\",\"stack-inserter\"]]}"));

		assertEquals(Optional.of("bulk-inserter"), migrations.migrateEntityName("stack-inserter"));
		assertEquals(Optional.of("stack-inserter"), migrations.migrateEntityName("bulk-inserter"));
	}

	/**
	 * A later file renames stack-filter-inserter to the name that an earlier file had already
	 * moved elsewhere. The earlier file must not get a second chance at the result.
	 */
	@Test
	public void doesNotReapplyAnEarlierFileToALaterResult() throws IOException {
		FactorioMigrations migrations = install(
				migration("base", "1.2.0.json",
						"{\"entity\":[[\"stack-inserter\",\"bulk-inserter\"],[\"bulk-inserter\",\"stack-inserter\"]]}"),
				migration("base", "2.0.0.json", "{\"entity\":[[\"stack-filter-inserter\",\"bulk-inserter\"]]}"));

		assertEquals(Optional.of("bulk-inserter"), migrations.migrateEntityName("stack-filter-inserter"));
	}

	@Test
	public void carriesAResultThroughLaterFiles() throws IOException {
		FactorioMigrations migrations = install(
				migration("base", "1.0.0.json", "{\"entity\":[[\"first-name\",\"second-name\"]]}"),
				migration("base", "2.0.0.json", "{\"entity\":[[\"second-name\",\"third-name\"]]}"));

		assertEquals(Optional.of("third-name"), migrations.migrateEntityName("first-name"));
	}

	@Test
	public void keepsEntityAndTileRenamesApart() throws IOException {
		FactorioMigrations migrations = install(
				migration("base", "2.0.0.json", "{\"entity\":[[\"shared-name\",\"renamed-entity\"]]}"));

		assertEquals(Optional.of("renamed-entity"), migrations.migrateEntityName("shared-name"));
		assertEquals(Optional.empty(), migrations.migrateTileName("shared-name"));
	}

	@Test
	public void reportsNoMigrationForNamesThatWereNeverRenamed() throws IOException {
		FactorioMigrations migrations = install(
				migration("base", "2.0.0.json", "{\"entity\":[[\"filter-inserter\",\"fast-inserter\"]]}"));

		assertEquals(Optional.empty(), migrations.migrateEntityName("assembling-machine-1"));
	}

	@Test
	public void survivesJsonRoundTrip() throws IOException {
		FactorioMigrations original = install(
				migration("base", "1.2.0.json",
						"{\"entity\":[[\"stack-inserter\",\"bulk-inserter\"],[\"bulk-inserter\",\"stack-inserter\"]]}"),
				migration("base", "2.0.0.json",
						"{\"entity\":[[\"stack-filter-inserter\",\"bulk-inserter\"]],\"tile\":[[\"a\",\"b\"]]}"));

		FactorioMigrations restored = FactorioMigrations.fromJson(original.toJson());

		assertEquals(Optional.of("bulk-inserter"), restored.migrateEntityName("stack-inserter"));
		assertEquals(Optional.of("bulk-inserter"), restored.migrateEntityName("stack-filter-inserter"));
		assertEquals(Optional.of("b"), restored.migrateTileName("a"));
	}

	@Test
	public void readsMigrationsFromEveryModInTheInstall() throws IOException {
		FactorioMigrations migrations = install(
				migration("base", "2.0.0.json", "{\"entity\":[[\"filter-inserter\",\"fast-inserter\"]]}"),
				migration("space-age", "tungsten-belt-rename.json", "{\"entity\":[[\"tungsten-belt\",\"turbo-belt\"]]}"));

		assertEquals(Optional.of("fast-inserter"), migrations.migrateEntityName("filter-inserter"));
		assertEquals(Optional.of("turbo-belt"), migrations.migrateEntityName("tungsten-belt"));
	}

	@Test
	public void ignoresUnreadableMigrationFiles() throws IOException {
		FactorioMigrations migrations = install(migration("base", "2.0.0-broken.json", "this is not json"),
				migration("base", "2.0.1.json", "{\"entity\":[[\"filter-inserter\",\"fast-inserter\"]]}"));

		assertEquals(Optional.of("fast-inserter"), migrations.migrateEntityName("filter-inserter"));
	}

	@Test
	public void ignoresSectionsOtherThanEntityAndTile() throws IOException {
		FactorioMigrations migrations = install(
				migration("base", "2.0.0.json", "{\"recipe\":[[\"old-recipe\",\"new-recipe\"]],\"technology\":[]}"));

		assertTrue(migrations.isEmpty());
	}

	@Test
	public void readsNothingFromAnInstallWithoutMigrations() throws IOException {
		assertTrue(FactorioMigrations.fromFactorioInstall(folder.newFolder("factorio")).isEmpty());
		assertTrue(FactorioMigrations.empty().isEmpty());
		assertEquals(Optional.empty(), FactorioMigrations.empty().migrateEntityName("filter-inserter"));
	}

	private record Migration(String modName, String fileName, String contents) {}

	private static Migration migration(String modName, String fileName, String contents) {
		return new Migration(modName, fileName, contents);
	}

	private FactorioMigrations install(Migration... migrations) throws IOException {
		File install = folder.newFolder();
		for (Migration migration : migrations) {
			File folderMigrations = new File(new File(new File(install, "data"), migration.modName()), "migrations");
			folderMigrations.mkdirs();
			Files.write(new File(folderMigrations, migration.fileName()).toPath(),
					migration.contents().getBytes(StandardCharsets.UTF_8));
		}
		return FactorioMigrations.fromFactorioInstall(install);
	}
}
