package com.chronicle;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Chronicle.MOD_ID, dist = Dist.CLIENT)
public class Chronicle {
    public static final String MOD_ID = "chronicle";

    public Chronicle(IEventBus modBus, ModContainer container) {
        modBus.addListener(ClientEvents::registerKeys);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLogout);
    }
}
