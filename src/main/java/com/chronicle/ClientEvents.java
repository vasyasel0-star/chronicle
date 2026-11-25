package com.chronicle;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
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

    /** Ванильные экраны «Достижения» (клавиша L) и «Статистика» заменяем нашим. */
    public static void onScreenOpening(ScreenEvent.Opening e) {
        Screen n = e.getNewScreen();
        String name = n.getClass().getSimpleName();
        if (name.equals("AdvancementsScreen") && Theme.cfg().replaceAdv) {
            e.setNewScreen(new ChronicleScreen(e.getCurrentScreen(), ChronicleScreen.ADV, 0));
        } else if (name.equals("StatsScreen") && Theme.cfg().replaceStats) {
            e.setNewScreen(new ChronicleScreen(e.getCurrentScreen(), ChronicleScreen.WORLD, 0));
        }
    }

    /** Маленькая кнопка в главном меню, настройках и при создании мира. */
    public static void onScreenInit(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        String name = s.getClass().getSimpleName();
        int x;
        int y;
        if (s instanceof TitleScreen) {
            x = s.width / 2 + 104;
            y = s.height / 4 + 48;
        } else if (name.equals("OptionsScreen") || name.equals("CreateWorldScreen")) {
            x = s.width - 24;
            y = 4;
        } else {
            return;
        }
        Button b = Button.builder(Component.literal("Σ"),
                        btn -> Minecraft.getInstance().setScreen(new ChronicleScreen(s, ChronicleScreen.ALL, 0)))
                .bounds(x, y, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("chronicle.button")))
                .build();
        e.addListener(b);
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
