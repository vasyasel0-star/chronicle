package com.chronicle;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Экран статистики и достижений в духе меню GTA San Andreas. */
public class ChronicleScreen extends Screen {
    public static final int WORLD = 0;
    public static final int ALL = 1;
    public static final int ADV = 2;
    public static final int SET = 3;

    private static final int DIM = 0xFF9AA0A8;
    private static final int TEXT = 0xFFE8EAED;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int SIDE = 108;
    private static final int HEAD = 42;
    private static final int TAB_H = 16;

    private static final Component[] TABS = {
        Component.translatable("chronicle.tab.world"),
        Component.translatable("chronicle.tab.all"),
        Component.translatable("chronicle.tab.adv"),
        Component.translatable("chronicle.tab.settings")
    };

    private static int lastScope;
    private static int lastPage;

    private final Screen parent;
    private int scope;
    private int sel;
    private List<PageBuilder.Page> pages = List.of();
    private double scroll;
    private int maxScroll;
    private int tickCount;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private final int[] tabX = new int[TABS.length];
    private final int[] tabW = new int[TABS.length];

    public ChronicleScreen() {
        this(null, lastScope, lastPage);
    }

    public ChronicleScreen(Screen parent, int scope, int page) {
        super(Component.translatable("chronicle.title"));
        this.parent = parent;
        this.scope = scope;
        this.sel = page;
    }

    @Override
    protected void init() {
        if (minecraft != null && minecraft.player == null && (scope == WORLD || scope == ADV)) {
            scope = ALL;
            sel = 0;
        }
        pw = Math.min(width - 16, 480);
        ph = Math.min(height - 16, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int x = px + pw - 12;
        for (int i = TABS.length - 1; i >= 0; i--) {
            tabW[i] = font.width(TABS[i]) + 14;
            x -= tabW[i];
            tabX[i] = x;
            x -= 3;
        }

        ClientEvents.requestStats();
        rebuild();
    }

    @Override
    public void removed() {
        lastScope = scope;
        lastPage = sel;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        tickCount++;
        if (tickCount % 100 == 0) ClientEvents.requestStats();
        if (tickCount % 20 == 5) rebuild();
    }

    // ---------- геометрия контента ----------

    private int cx() {
        return px + SIDE + 12;
    }

    private int cy() {
        return py + HEAD + 8;
    }

    private int cw() {
        return pw - SIDE - 12 - 14;
    }

    private int chH() {
        return ph - HEAD - 16;
    }

    private void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        pages = switch (scope) {
            case WORLD -> PageBuilder.world(mc);
            case ALL -> PageBuilder.all(mc);
            case ADV -> ExtraPages.advancements(mc);
            default -> ExtraPages.settings();
        };
        sel = pages.isEmpty() ? 0 : Mth.clamp(sel, 0, pages.size() - 1);
        int total = 0;
        if (!pages.isEmpty()) {
            for (Row r : pages.get(sel).rows()) total += r.height();
        }
        maxScroll = Math.max(0, total - chH() + 4);
        scroll = Mth.clamp(scroll, 0.0, (double) maxScroll);
    }

    private Component subtitle() {
        switch (scope) {
            case WORLD -> {
                WorldId w = ClientEvents.world;
                return Component.literal(w == null ? "" : w.name());
            }
            case ALL -> {
                return Component.translatable("chronicle.sub.all");
            }
            case ADV -> {
                return Component.translatable("chronicle.sub.adv");
            }
            default -> {
                return Component.translatable("chronicle.sub.settings");
            }
        }
    }

    // ---------- отрисовка ----------

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        if (Theme.cfg().dim) g.fillGradient(0, 0, width, height, 0xC0101010, 0xD0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);

        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Theme.border());
        g.fill(px, py, px + pw, py + ph, Theme.shade(0f));

        renderHeader(g, mx, my);
        renderSidebar(g, mx, my);
        renderContent(g);
    }

    private void drawClipped(GuiGraphics g, Component c, int x, int y, int maxW, int color) {
        int w = Math.max(8, maxW);
        FormattedText t = c;
        if (font.width(c) > w) {
            t = FormattedText.composite(font.substrByWidth(c, w - font.width("…")), FormattedText.of("…"));
        }
        g.drawString(font, Language.getInstance().getVisualOrder(t), x, y, color, false);
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        g.fill(px, py, px + pw, py + HEAD, Theme.shade(0.05f));
        g.fill(px, py + HEAD, px + pw, py + HEAD + 1, Theme.accent());
        g.fill(px, py, px + 3, py + HEAD, Theme.accent());

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(px + 12, py + 8, 0);
        ps.scale(1.6f, 1.6f, 1f);
        g.drawString(font, getTitle().copy().withStyle(ChatFormatting.BOLD), 0, 0, Theme.accent(), true);
        ps.popPose();

        g.drawString(font, subtitle(), px + 12, py + 28, DIM, false);

        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == scope;
            boolean hover = inside(mx, my, tabX[i], py + 12, tabW[i], TAB_H);
            int bg = on ? Theme.accent() : hover ? 0xFF2C3139 : 0xFF22262C;
            g.fill(tabX[i], py + 12, tabX[i] + tabW[i], py + 12 + TAB_H, bg);
            g.drawString(font, TABS[i], tabX[i] + 7, py + 16, on ? 0xFF14110A : 0xFFD5D8DC, false);
        }
    }

    private void renderSidebar(GuiGraphics g, int mx, int my) {
        g.fill(px, py + HEAD + 1, px + SIDE, py + ph, Theme.shade(-0.2f));
        g.fill(px + SIDE, py + HEAD + 1, px + SIDE + 1, py + ph, Theme.shade(0.1f));

        for (int i = 0; i < pages.size(); i++) {
            int x = px + 6;
            int y = py + HEAD + 8 + i * 22;
            if (y + 20 > py + ph) break;
            int w = SIDE - 12;
            boolean on = i == sel;
            boolean hover = inside(mx, my, x, y, w, 20);
            if (on) {
                g.fill(x, y, x + w, y + 20, Theme.accentFill());
                g.fill(x, y, x + 2, y + 20, Theme.accent());
            } else if (hover) {
                g.fill(x, y, x + w, y + 20, 0x20FFFFFF);
            }
            drawClipped(g, pages.get(i).title(), x + 9, y + 6, w - 12, on ? WHITE : DIM);
        }
    }

    private void renderContent(GuiGraphics g) {
        int cx = cx();
        int cy = cy();
        int cw = cw();
        int ch = chH();

        if (pages.isEmpty()) {
            g.drawString(font, Component.translatable("chronicle.none"), cx + 6, cy + 6, DIM, false);
            return;
        }

        List<Row> rows = pages.get(sel).rows();
        int y = cy - (int) scroll;
        int alt = 0;

        g.enableScissor(cx, cy, cx + cw, cy + ch);
        for (Row r : rows) {
            int h = r.height();
            if (y + h > cy && y < cy + ch) drawRow(g, r, cx, y, cw, h, alt);
            if (r.kind() == Row.Kind.ROW) alt++;
            y += h;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int tx = cx + cw + 4;
            g.fill(tx, cy, tx + 3, cy + ch, 0xFF22262C);
            int total = maxScroll + ch - 4;
            int thumb = Math.max(16, ch * ch / Math.max(total, 1));
            int ty = cy + (int) ((ch - thumb) * (scroll / maxScroll));
            g.fill(tx, ty, tx + 3, ty + thumb, Theme.accent());
        }
    }

    private void drawRow(GuiGraphics g, Row r, int cx, int y, int cw, int h, int alt) {
        switch (r.kind()) {
            case HEADER -> {
                drawClipped(g, r.label().copy().withStyle(ChatFormatting.BOLD), cx + 2, y + 8, cw - 4,
                        Theme.accent());
                g.fill(cx, y + h - 4, cx + cw, y + h - 3, Theme.accentLine());
            }
            case ROW -> {
                if ((alt & 1) == 0) g.fill(cx, y, cx + cw, y + h - 1, 0x16FFFFFF);
                if (r.frac() >= 0) {
                    int w = Math.max(2, (int) (cw * Mth.clamp(r.frac(), 0f, 1f)));
                    g.fill(cx, y, cx + w, y + h - 1, Theme.accentFill());
                    g.fill(cx, y, cx + 2, y + h - 1, Theme.accent());
                }
                int vw = font.width(r.value());
                drawClipped(g, r.label(), cx + 6, y + 3, cw - 20 - vw, TEXT);
                g.drawString(font, r.value(), cx + cw - 6 - vw, y + 3, WHITE, false);
            }
            case NOTE -> drawClipped(g, r.label(), cx + 6, y + 2, cw - 12, DIM);
            case SPACER -> {
                // пустое место
            }
        }
    }

    // ---------- ввод ----------

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < TABS.length; i++) {
                if (inside(mx, my, tabX[i], py + 12, tabW[i], TAB_H)) {
                    switchScope(i);
                    return true;
                }
            }
            for (int i = 0; i < pages.size(); i++) {
                if (inside(mx, my, px + 6, py + HEAD + 8 + i * 22, SIDE - 12, 20)) {
                    selectPage(i);
                    return true;
                }
            }
        }
        if ((button == 0 || button == 1) && scope == SET && !pages.isEmpty()
                && inside(mx, my, cx(), cy(), cw(), chH())) {
            int y = cy() - (int) scroll;
            int idx = 0;
            for (Row r : pages.get(sel).rows()) {
                if (r.kind() == Row.Kind.ROW) {
                    if (my >= y && my < y + r.height()) {
                        Theme.cycle(idx, button == 1 ? -1 : 1);
                        rebuild();
                        return true;
                    }
                    idx++;
                }
                y += r.height();
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * 28, 0.0, (double) maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.OPEN.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                if (!pages.isEmpty()) selectPage(Math.max(0, sel - 1));
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (!pages.isEmpty()) selectPage(Math.min(pages.size() - 1, sel + 1));
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                switchScope(Math.max(0, scope - 1));
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                switchScope(Math.min(TABS.length - 1, scope + 1));
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                scroll = Mth.clamp(scroll - chH() * 0.8, 0.0, (double) maxScroll);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scroll = Mth.clamp(scroll + chH() * 0.8, 0.0, (double) maxScroll);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void switchScope(int i) {
        if (scope == i) return;
        scope = i;
        sel = 0;
        scroll = 0;
        rebuild();
    }

    private void selectPage(int i) {
        if (sel == i) return;
        sel = i;
        scroll = 0;
        rebuild();
    }
}
