package com.demod.fbsr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
						"{\"entity\":[[\"stack-filter-inserter\",\"bulk-inserter\"]],\"tile\":[[\"a\",\"b\"]],"
								+ "\"item\":[[\"old-item\",\"new-item\"]]}"));

		FactorioMigrations restored = FactorioMigrations.fromJson(original.toJson());

		assertEquals(Optional.of("bulk-inserter"), restored.migrateEntityName("stack-inserter"));
		assertEquals(Optional.of("bulk-inserter"), restored.migrateEntityName("stack-filter-inserter"));
		assertEquals(Optional.of("b"), restored.migrateTileName("a"));
		assertEquals(Optional.of("new-item"), restored.migrateItemName("old-item"));
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
	public void readsItemRenames() throws IOException {
		FactorioMigrations migrations = install(
				migration("base", "2.0.0.json", "{\"item\":[[\"filter-inserter\",\"fast-inserter\"]]}"));

		assertEquals(Optional.of("fast-inserter"), migrations.migrateItemName("filter-inserter"));
		assertEquals(Optional.empty(), migrations.migrateEntityName("filter-inserter"));
	}

	@Test
	public void ignoresSectionsThatCannotAppearInABlueprint() throws IOException {
		FactorioMigrations migrations = install(
				migration("base", "2.0.0.json", "{\"recipe\":[[\"old-recipe\",\"new-recipe\"]],\"technology\":[]}"));

		assertTrue(migrations.isEmpty());
	}

	@Test
	public void readsRenamesFromInsideModZips() throws IOException {
		File mods = folder.newFolder("mods");
		modZip(mods, "some-mod_1.2.3.zip", "some-mod_1.2.3",
				"{\"entity\":[[\"old-machine\",\"new-machine\"]],\"item\":[[\"old-item\",\"new-item\"]]}");

		FactorioMigrations migrations = FactorioMigrations.fromMods(mods);

		assertEquals(Optional.of("new-machine"), migrations.migrateEntityName("old-machine"));
		assertEquals(Optional.of("new-item"), migrations.migrateItemName("old-item"));
	}

	/** Some mods put migrations under a versioned folder, others under a bare mod name. */
	@Test
	public void readsModMigrationsUnderEitherFolderNamingConvention() throws IOException {
		File mods = folder.newFolder("mods");
		modZip(mods, "versioned_3.3.0.zip", "versioned_3.3.0", "{\"entity\":[[\"a\",\"b\"]]}");
		modZip(mods, "bare_3.1.2.zip", "bare", "{\"entity\":[[\"c\",\"d\"]]}");

		FactorioMigrations migrations = FactorioMigrations.fromMods(mods);

		assertEquals(Optional.of("b"), migrations.migrateEntityName("a"));
		assertEquals(Optional.of("d"), migrations.migrateEntityName("c"));
	}

	@Test
	public void ignoresUnreadableModZips() throws IOException {
		File mods = folder.newFolder("mods");
		Files.write(new File(mods, "broken_1.0.0.zip").toPath(), "not a zip".getBytes(StandardCharsets.UTF_8));
		modZip(mods, "good_1.0.0.zip", "good_1.0.0", "{\"entity\":[[\"a\",\"b\"]]}");

		assertEquals(Optional.of("b"), FactorioMigrations.fromMods(mods).migrateEntityName("a"));
	}

	@Test
	public void readsNothingFromAModsFolderThatIsNotThere() {
		assertTrue(FactorioMigrations.fromMods(new File(folder.getRoot(), "no-such-folder")).isEmpty());
	}

	/** A mod may rename something the base game already renamed, so the game's steps come first. */
	@Test
	public void appliesInstallMigrationsBeforeModMigrations() throws IOException {
		File root = folder.newFolder();
		File installMigrations = new File(new File(new File(root, "data"), "base"), "migrations");
		installMigrations.mkdirs();
		Files.write(new File(installMigrations, "2.0.0.json").toPath(),
				"{\"entity\":[[\"first-name\",\"second-name\"]]}".getBytes(StandardCharsets.UTF_8));
		File mods = new File(root, "mods");
		mods.mkdirs();
		modZip(mods, "a-mod_1.0.0.zip", "a-mod", "{\"entity\":[[\"second-name\",\"third-name\"]]}");

		FactorioMigrations migrations = FactorioMigrations.fromFactorioInstall(root)
				.andThen(FactorioMigrations.fromMods(mods));

		assertEquals(Optional.of("third-name"), migrations.migrateEntityName("first-name"));
	}

	@Test
	public void andThenLeavesBothSidesUnchanged() throws IOException {
		FactorioMigrations first = install(migration("base", "1.0.0.json", "{\"entity\":[[\"a\",\"b\"]]}"));
		File mods = folder.newFolder("mods2");
		modZip(mods, "m_1.0.0.zip", "m", "{\"entity\":[[\"b\",\"c\"]]}");
		FactorioMigrations second = FactorioMigrations.fromMods(mods);

		assertEquals(Optional.of("c"), first.andThen(second).migrateEntityName("a"));
		assertEquals(Optional.of("b"), first.migrateEntityName("a"));
		assertEquals(Optional.empty(), second.migrateEntityName("a"));
	}

	@Test
	public void readsNothingFromAnInstallWithoutMigrations() throws IOException {
		assertTrue(FactorioMigrations.fromFactorioInstall(folder.newFolder("factorio")).isEmpty());
		assertTrue(FactorioMigrations.empty().isEmpty());
		assertEquals(Optional.empty(), FactorioMigrations.empty().migrateEntityName("filter-inserter"));
	}

	private void modZip(File modsFolder, String zipName, String folderInZip, String contents) throws IOException {
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(modsFolder, zipName)))) {
			zos.putNextEntry(new ZipEntry(folderInZip + "/info.json"));
			zos.write("{}".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			zos.putNextEntry(new ZipEntry(folderInZip + "/migrations/1.0.0.json"));
			zos.write(contents.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}
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
