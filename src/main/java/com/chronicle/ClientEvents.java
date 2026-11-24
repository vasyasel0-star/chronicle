package com.chronicle;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.chronicle.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.chronicle");

    /** Текущий мир (null, если игрок не в мире). */
    public static volatile WorldId world;

    private static int ticks;

    private ClientEvents() {}

    public static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(OPEN);
    }

    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
        world = WorldId.current(Minecraft.getInstance());
        ticks = 0;
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        LocalPlayer p = e.getPlayer();
    ClientPacketListener c = Minecraft.getInstance().getConnection();
        if (p != null && c != null) snapshot(p, c);
        world = null;
    }

    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        while (OPEN.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ChronicleScreen());
        }

        if (world == null) world = WorldId.current(mc);
        ticks++;
        // раз в минуту просим у сервера свежую статистику и через 2 секунды сохраняем сводку
        if (ticks == 60 || ticks % 1200 == 0) {
            requestStats();
        } else if (ticks == 100 || ticks % 1200 == 40) {
            snapshot(mc.player, mc.getConnection());
        }
    }

    public static void requestStats() {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        if (c != null) {
            c.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }
    }

    private static void snapshot(LocalPlayer p, ClientPacketListener c) {
        WorldId w = world;
        if (w == null) return;
        Map<String, Long> m = Metrics.collect(p.getStats(), c.getAdvancements());
        WorldStore.save(w.key(), w.name(), w.mp(), m);
    }
}
