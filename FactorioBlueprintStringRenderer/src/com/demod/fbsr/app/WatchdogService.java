package com.demod.fbsr.app;

import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.AbstractScheduledService;

import com.demod.factorio.Config;

public class WatchdogService extends AbstractScheduledService {

	public static interface WatchdogReporter {
		public void notifyInactive(String label);

		public void notifyReactive(String label);
	}

	private final LinkedHashSet<String> known = new LinkedHashSet<>();
	private final LinkedHashSet<String> active = new LinkedHashSet<>();
	private final LinkedHashSet<String> alarmed = new LinkedHashSet<>();
	private JsonNode configJson;

	public synchronized void notifyActive(String label) {
		known.add(label);
		active.add(label);
		if (alarmed.remove(label)) {
			System.out.println("WATCHDOG: " + label + " is now active again!");
			ServiceFinder.findService(WatchdogReporter.class).ifPresent(reporter -> {
				reporter.notifyReactive(label);
			});
		}
	}

	public synchronized void notifyKnown(String label) {
		if (known.add(label)) {
			active.add(label);
		}
	}

	@Override
	protected synchronized void runOneIteration() throws Exception {
		for (String label : known) {
			if (!active.contains(label) && !alarmed.contains(label)) {
				alarmed.add(label);
				System.out.println("WATCHDOG: " + label + " has gone inactive!");
				ServiceFinder.findService(WatchdogReporter.class).ifPresent(reporter -> {
					reporter.notifyInactive(label);
				});
			}
		}
		active.clear();
	}

	@Override
	protected Scheduler scheduler() {
		return Scheduler.newFixedDelaySchedule(0, configJson.path("interval_minutes").intValue(), TimeUnit.MINUTES);
	}

	@Override
	protected void shutDown() throws Exception {
		ServiceFinder.removeService(this);
	}

	@Override
	protected void startUp() throws Exception {
		ServiceFinder.addService(this);

		configJson = Config.get().path("watchdog");
	}

}
