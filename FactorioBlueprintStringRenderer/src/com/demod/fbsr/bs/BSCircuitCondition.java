package com.demod.fbsr.bs;

import java.util.Optional;
import java.util.OptionalInt;

import org.json.JSONObject;

import com.demod.fbsr.BSUtils;

public class BSCircuitCondition {
	public final Optional<BSSignalID> firstSignal;
	public final Optional<BSSignalID> secondSignal;
	public final OptionalInt constant;
	public final Optional<String> comparator;
	public final Optional<BSNetworkPorts> firstSignalNetworks;
	public final Optional<BSNetworkPorts> secondSignalNetworks;

	public BSCircuitCondition(JSONObject json) {
		firstSignal = BSUtils.opt(json, "first_signal", BSSignalID::new);
		secondSignal = BSUtils.opt(json, "second_signal", BSSignalID::new);
		constant = BSUtils.optInt(json, "constant");
		comparator = BSUtils.optString(json, "comparator");
		firstSignalNetworks = BSUtils.opt(json, "first_signal_networks", BSNetworkPorts::new);
		secondSignalNetworks = BSUtils.opt(json, "second_signal_networks", BSNetworkPorts::new);
	}
}
