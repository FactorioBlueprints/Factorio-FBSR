package com.demod.fbsr.bs;

import java.util.List;
import java.util.Optional;

import org.json.JSONObject;

import com.demod.fbsr.BSUtils;

public class BSSectionFilters {
	public final int index;
	public final List<BSFilter> filters;
	public final Optional<String> group;
	public final boolean active;

	public BSSectionFilters(JSONObject json) {
		index = json.getInt("index");
		filters = BSUtils.list(json, "filters", BSFilter::new);
		group = BSUtils.optString(json, "group");
		// TODO check if default is true
		active = json.optBoolean("active", true);
	}
}
