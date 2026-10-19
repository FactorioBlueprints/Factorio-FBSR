package com.demod.fbsr.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Steam on macOS installs Factorio as {@code Factorio/factorio.app}, with the executable and the
 * {@code data} folder both under {@code factorio.app/Contents}. The install path must be that
 * {@code Contents} folder, because Factorio reads its data relative to the install path.
 */
public class FactorioInstallSearchTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void aSteamAppBundleResolvesToItsContentsFolder() throws IOException {
		assumeTrue(System.getProperty("os.name").toLowerCase().contains("mac"));
		File steamFolder = temp.newFolder("Factorio");
		File contents = new File(steamFolder, "factorio.app/Contents");
		new File(contents, "MacOS").mkdirs();
		new File(contents, "MacOS/factorio").createNewFile();

		Optional<File> install = CmdConfig.factorioInstallIn(steamFolder);

		assertEquals(Optional.of(contents), install);
	}

	@Test
	public void aFolderWithoutFactorioHasNoInstall() throws IOException {
		Optional<File> install = CmdConfig.factorioInstallIn(temp.newFolder("empty"));

		assertEquals(Optional.empty(), install);
	}
}
