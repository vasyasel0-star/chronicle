package com.chronicle;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Экран статистики в духе меню GTA San Andreas. */
public class ChronicleScreen extends Screen {
    private static final int BG = 0xF0111317;
    private static final int ACCENT = 0xFFE6A23C;
    private static final int ACCENT_FILL = 0x40E6A23C;
    private static final int DIM = 0xFF9AA0A8;
    private static final int TEXT = 0xFFE8EAED;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int SIDE = 108;
    private static final int HEAD = 42;
    private static final int TAB_H = 16;

    private static final Component[] TABS = {
        Component.translatable("chronicle.tab.world"),
        Component.translatable("chronicle.tab.all")
    };

    private static int lastScope;
    private static int lastPage;

    private int scope = lastScope;
    private int sel = lastPage;
    private List<PageBuilder.Page> pages = List.of();
    private double scroll;
    private int maxScroll;
    private int tickCount;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private final int[] tabX = new int[2];
    private final int[] tabW = new int[2];

    public ChronicleScreen() {
        super(Component.translatable("chronicle.title"));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 480);
        ph = Math.min(height - 16, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int x = px + pw - 12;
        for (int i = TABS.length - 1; i >= 0; i--) {
            tabW[i] = font.width(TABS[i]) + 18;
            x -= tabW[i];
            tabX[i] = x;
            x -= 4;
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
        pages = scope == 0 ? PageBuilder.world(mc) : PageBuilder.all(mc);
        sel = pages.isEmpty() ? 0 : Mth.clamp(sel, 0, pages.size() - 1);
        int total = 0;
        if (!pages.isEmpty()) {
            for (Row r : pages.get(sel).rows()) total += r.height();
        }
        maxScroll = Math.max(0, total - chH() + 4);
        scroll = Mth.clamp(scroll, 0.0, (double) maxScroll);
    }

    private Component subtitle() {
        if (scope == 0) {
            WorldId w = ClientEvents.world;
            return Component.literal(w == null ? "" : w.name());
        }
        return Component.translatable("chronicle.sub.all");
    }

    // ---------- отрисовка ----------

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xC0101010, 0xD0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);

        // рамка и фон панели
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF3A2E17);
        g.fill(px, py, px + pw, py + ph, BG);

        renderHeader(g, mx, my);
        renderSidebar(g, mx, my);
        renderContent(g);
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        g.fill(px, py, px + pw, py + HEAD, 0xFF191C21);
        g.fill(px, py + HEAD, px + pw, py + HEAD + 1, ACCENT);
        g.fill(px, py, px + 3, py + HEAD, ACCENT);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(px + 12, py + 8, 0);
        ps.scale(1.6f, 1.6f, 1f);
        g.drawString(font, getTitle().copy().withStyle(ChatFormatting.BOLD), 0, 0, ACCENT, true);
        ps.popPose();

        g.drawString(font, subtitle(), px + 12, py + 28, DIM, false);

        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == scope;
            boolean hover = inside(mx, my, tabX[i], py + 12, tabW[i], TAB_H);
            int bg = on ? ACCENT : hover ? 0xFF2C3139 : 0xFF22262C;
            g.fill(tabX[i], py + 12, tabX[i] + tabW[i], py + 12 + TAB_H, bg);
            g.drawString(font, TABS[i], tabX[i] + 9, py + 16, on ? 0xFF14110A : 0xFFD5D8DC, false);
        }
    }

    private void renderSidebar(GuiGraphics g, int mx, int my) {
        g.fill(px, py + HEAD + 1, px + SIDE, py + ph, 0xFF15181C);
        g.fill(px + SIDE, py + HEAD + 1, px + SIDE + 1, py + ph, 0xFF262A31);

        for (int i = 0; i < pages.size(); i++) {
            int x = px + 6;
            int y = py + HEAD + 8 + i * 22;
            int w = SIDE - 12;
            boolean on = i == sel;
            boolean hover = inside(mx, my, x, y, w, 20);
            if (on) {
                g.fill(x, y, x + w, y + 20, ACCENT_FILL);
                g.fill(x, y, x + 2, y + 20, ACCENT);
            } else if (hover) {
                g.fill(x, y, x + w, y + 20, 0x20FFFFFF);
            }
            g.drawString(font, pages.get(i).title(), x + 9, y + 6, on ? WHITE : DIM, false);
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

        // полоса прокрутки
        if (maxScroll > 0) {
            int tx = cx + cw + 4;
            g.fill(tx, cy, tx + 3, cy + ch, 0xFF22262C);
            int total = maxScroll + ch - 4;
            int thumb = Math.max(16, ch * ch / Math.max(total, 1));
            int ty = cy + (int) ((ch - thumb) * (scroll / maxScroll));
            g.fill(tx, ty, tx + 3, ty + thumb, ACCENT);
        }
    }

    private void drawRow(GuiGraphics g, Row r, int cx, int y, int cw, int h, int alt) {
        switch (r.kind()) {
            case HEADER -> {
                g.drawString(font, r.label().copy().withStyle(ChatFormatting.BOLD), cx + 2, y + 8, ACCENT, false);
                g.fill(cx, y + h - 4, cx + cw, y + h - 3, 0x66E6A23C);
            }
            case ROW -> {
                if ((alt & 1) == 0) g.fill(cx, y, cx + cw, y + h - 1, 0x16FFFFFF);
                if (r.frac() >= 0) {
                    int w = Math.max(2, (int) (cw * Mth.clamp(r.frac(), 0f, 1f)));
                    g.fill(cx, y, cx + w, y + h - 1, ACCENT_FILL);
                    g.fill(cx, y, cx + 2, y + h - 1, ACCENT);
                }
                g.drawString(font, r.label(), cx + 6, y + 3, TEXT, false);
                g.drawString(font, r.value(), cx + cw - 6 - font.width(r.value()), y + 3, WHITE, false);
            }
            case NOTE -> g.drawString(font, r.label(), cx + 6, y + 2, DIM, false);
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
                switchScope(0);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                switchScope(1);
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
