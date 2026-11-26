package com.chronicle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Окно настроек мода в списке модов (Mods → Chronicle → Config). */
public class ChronicleConfigScreen extends Screen {
    private static final int COUNT = 8;
    private final Screen parent;

    public ChronicleConfigScreen(Screen parent) {
        super(Component.translatable("chronicle.cfg.title"));
        this.parent = parent;
    }

    private static Component label(int i) {
        Theme.Cfg c = Theme.cfg();
        String key;
        String value;
        switch (i) {
            case 0 -> {
                key = "set.accent";
                value = I18n.get("chronicle.color." + Theme.ACCENT_KEYS[c.accent]);
            }
            case 1 -> {
                key = "set.bg";
                value = I18n.get("chronicle.color." + Theme.BG_KEYS[c.bg]);
            }
            case 2 -> {
                key = "set.opacity";
                value = Theme.OPACITY[c.opacity] + "%";
            }
            case 3 -> {
                key = "set.dim";
                value = onOff(c.dim);
            }
            case 4 -> {
                key = "set.repl_adv";
                value = onOff(c.replaceAdv);
            }
            case 5 -> {
                key = "set.repl_stats";
                value = onOff(c.replaceStats);
            }
            case 7 -> {
                key = "set.round";
                value = onOff(c.round);
            }
            default -> {
                key = "set.adv_style";
                value = I18n.get("chronicle.style." + Theme.STYLE_KEYS[c.advStyle]);
            }
        }
        return Component.translatable("chronicle." + key).append(": " + value);
    }

    private static String onOff(boolean on) {
        return I18n.get(on ? "chronicle.set.on" : "chronicle.set.off");
    }

    @Override
    protected void init() {
        int x = width / 2 - 110;
        int y = Math.max(32, height / 2 - (COUNT * 24 + 56) / 2);
        for (int i = 0; i < COUNT; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(label(i), b -> {
                Theme.cycle(idx, 1);
                b.setMessage(label(idx));
            }).bounds(x, y + i * 24, 220, 20).build());
        }
        int by = y + COUNT * 24 + 8;
        addRenderableWidget(Button.builder(Component.translatable("chronicle.cfg.open"),
                b -> minecraft.setScreen(new ChronicleScreen(this, ChronicleScreen.ALL, 0)))
                .bounds(x, by, 220, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, by + 24, 220, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, getTitle(), width / 2, 15, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
