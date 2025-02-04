package com.demod.fbsr.entity;

import java.util.Optional;
import java.util.function.Consumer;

import com.demod.factorio.DataTable;
import com.demod.factorio.prototype.EntityPrototype;
import com.demod.fbsr.EntityRendererFactory;
import com.demod.fbsr.FPUtils;
import com.demod.fbsr.RenderUtils;
import com.demod.fbsr.Renderer;
import com.demod.fbsr.WorldMap;
import com.demod.fbsr.bs.BSEntity;
import com.demod.fbsr.fp.FPAnimation4Way;

public class BurnerGeneratorRendering extends EntityRendererFactory<BSEntity> {
	private FPAnimation4Way protoAnimation;

	@Override
	public void createRenderers(Consumer<Renderer> register, WorldMap map, DataTable dataTable, BSEntity entity) {
		register.accept(RenderUtils.spriteRenderer(protoAnimation.createSprites(entity.direction, 0), entity,
				protoSelectionBox));
	}

	@Override
	public void initFromPrototype(DataTable dataTable, EntityPrototype prototype) {

		Optional<FPAnimation4Way> idleAnimation = FPUtils.opt(prototype.lua().get("idle_animation"),
				FPAnimation4Way::new);

		if (idleAnimation.isPresent()) {
			protoAnimation = idleAnimation.get();
		} else {
			protoAnimation = new FPAnimation4Way(prototype.lua().get("animation"));
		}
	}
}
