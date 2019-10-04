package com.demod.fbsr.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import javax.swing.JOptionPane;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.io.output.TeeOutputStream;
import com.demod.factorio.Config;
import com.google.common.util.concurrent.AbstractIdleService;

public class LoggingService extends AbstractIdleService {

	static {
		PrintStream err = System.err;
		try {
			JsonNode configJson = Config.get().path("logging");

			JsonNode fileJson = configJson.path("file");
			assert fileJson.isTextual();
			File file = new File(fileJson.textValue());
			FileOutputStream fos = new FileOutputStream(file);
			System.setOut(new PrintStream(new TeeOutputStream(System.out, fos)));
			System.setErr(new PrintStream(new TeeOutputStream(System.err, fos)));

		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, e.getMessage());
			e.printStackTrace(err);
			throw new InternalError(e);
		}
	}

	@Override
	protected void shutDown() throws Exception {
		ServiceFinder.removeService(this);
	}

	@Override
	protected void startUp() throws Exception {
		ServiceFinder.addService(this);

	}

}
