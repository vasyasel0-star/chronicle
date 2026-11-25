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
import net.minecraft.util.FormattedCharSequence;
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
    private static final int GREEN = 0xFF7BD88F;

    private static final int SIDE = 128;
    private static final int HEAD = 42;
    private static final int TAB_H = 16;
    private static final int CHIP_H = 16;

    private static final Component[] TABS = {
        Component.translatable("chronicle.tab.world"),
        Component.translatable("chronicle.tab.all"),
        Component.translatable("chronicle.tab.adv"),
        Component.translatable("chronicle.tab.settings")
    };
    private static final Component[] CHIPS = {
        Component.translatable("chronicle.filter.all"),
        Component.translatable("chronicle.filter.remaining"),
        Component.translatable("chronicle.filter.done")
    };

    private static int lastScope;
    private static int lastPage;
    private static int lastFilter;

    private final Screen parent;
    private int scope;
    private int sel;
    private int advFilter = lastFilter;
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
        lastFilter = advFilter;
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

    private boolean chipsOn() {
        return scope == ADV && sel > 0 && !pages.isEmpty();
    }

    /** Верх прокручиваемой области (ниже кнопок-фильтров, если они есть). */
    private int ctop() {
        return cy() + (chipsOn() ? CHIP_H + 6 : 0);
    }

    private int cheight() {
        return ph - HEAD - 16 - (chipsOn() ? CHIP_H + 6 : 0);
    }

    private int textW() {
        return cw() - 42 - 8;
    }

    private int cardHeight(Row.Card c) {
        int inner = 13;
        if (c.desc() != null) inner += font.split(c.desc(), textW()).size() * 10;
        if (c.tip() != null) inner += font.split(c.tip(), textW()).size() * 10;
        if (c.frac() >= 0) inner += 8;
        return 12 + Math.max(26, inner) + 4;
    }

    private int rh(Row r) {
        return r.kind() == Row.Kind.CARD ? cardHeight(r.card()) : r.height();
    }

    private void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        pages = switch (scope) {
            case WORLD -> PageBuilder.world(mc);
            case ALL -> PageBuilder.all(mc);
            case ADV -> ExtraPages.advancements(mc, advFilter);
            default -> ExtraPages.settings();
        };
        sel = pages.isEmpty() ? 0 : Mth.clamp(sel, 0, pages.size() - 1);
        int total = 0;
        if (!pages.isEmpty()) {
            for (Row r : pages.get(sel).rows()) total += rh(r);
        }
        maxScroll = Math.max(0, total - cheight() + 4);
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
        renderChips(g, mx, my);
        renderContent(g, mx, my);
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
            PageBuilder.Page p = pages.get(i);
            int tx = x + 9;
            if (!p.icon().isEmpty()) {
                g.renderItem(p.icon(), x + 5, y + 2);
                tx = x + 26;
            }
            drawClipped(g, p.title(), tx, y + 6, x + w - tx - 4, on ? WHITE : DIM);
        }
    }

    private int chipX(int i) {
        int x = cx();
        for (int k = 0; k < i; k++) x += font.width(CHIPS[k]) + 14 + 4;
        return x;
    }

    private void renderChips(GuiGraphics g, int mx, int my) {
        if (!chipsOn()) return;
        for (int i = 0; i < CHIPS.length; i++) {
            int w = font.width(CHIPS[i]) + 14;
            boolean on = i == advFilter;
            boolean hover = inside(mx, my, chipX(i), cy(), w, CHIP_H);
            g.fill(chipX(i), cy(), chipX(i) + w, cy() + CHIP_H,
                    on ? Theme.accent() : hover ? 0xFF2C3139 : 0xFF22262C);
            g.drawString(font, CHIPS[i], chipX(i) + 7, cy() + 4, on ? 0xFF14110A : 0xFFD5D8DC, false);
        }
    }

    private void renderContent(GuiGraphics g, int mx, int my) {
        int cx = cx();
        int top = ctop();
        int cw = cw();
        int ch = cheight();

        if (pages.isEmpty()) {
            g.drawString(font, Component.translatable("chronicle.none"), cx + 6, cy() + 6, DIM, false);
            return;
        }

        List<Row> rows = pages.get(sel).rows();
        int y = top - (int) scroll;
        int alt = 0;

        g.enableScissor(cx, top, cx + cw, top + ch);
        for (Row r : rows) {
            int h = rh(r);
            if (y + h > top && y < top + ch) {
                boolean hover = scope == ADV && sel == 0 && r.kind() == Row.Kind.CARD
                        && inside(mx, my, cx, y, cw, h - 4) && inside(mx, my, cx, top, cw, ch);
                drawRow(g, r, cx, y, cw, h, alt, hover);
            }
            if (r.kind() == Row.Kind.ROW) alt++;
            y += h;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int tx = cx + cw + 4;
            g.fill(tx, top, tx + 3, top + ch, 0xFF22262C);
            int total = maxScroll + ch - 4;
            int thumb = Math.max(16, ch * ch / Math.max(total, 1));
            int ty = top + (int) ((ch - thumb) * (scroll / maxScroll));
            g.fill(tx, ty, tx + 3, ty + thumb, Theme.accent());
        }
    }

    private void drawRow(GuiGraphics g, Row r, int cx, int y, int cw, int h, int alt, boolean hover) {
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
            case CARD -> drawCard(g, r.card(), cx, y, cw, h, hover);
            case SPACER -> {
                // пустое место
            }
        }
    }

    private void drawCard(GuiGraphics g, Row.Card c, int x, int y, int w, int h, boolean hover) {
        int stripe = c.done() ? GREEN : Theme.accent();
        int bodyH = h - 4;
        g.fill(x, y, x + w, y + bodyH, hover ? 0x33FFFFFF : 0x1EFFFFFF);
        g.fill(x, y, x + 3, y + bodyH, stripe);

        // рамка под иконку
        int bx = x + 9;
        int by = y + 6;
        g.fill(bx, by, bx + 26, by + 26, c.done() ? 0xFF2E5A3E : 0xFF3A3F47);
        g.fill(bx + 1, by + 1, bx + 25, by + 25, 0xFF14161A);
        g.renderItem(c.icon(), bx + 5, by + 5);

        int tx = x + 42;
        int tw = w - 42 - 8;
        int bw = font.width(c.badge());
        drawClipped(g, c.title().copy().withStyle(ChatFormatting.BOLD), tx, y + 6, tw - bw - 8,
                c.done() ? GREEN : WHITE);
        g.drawString(font, c.badge(), x + w - 8 - bw, y + 6, c.badgeColor(), false);

        int ty = y + 19;
        if (c.desc() != null) {
            for (FormattedCharSequence line : font.split(c.desc(), tw)) {
                g.drawString(font, line, tx, ty, DIM, false);
                ty += 10;
            }
        }
        if (c.tip() != null) {
            for (FormattedCharSequence line : font.split(c.tip(), tw)) {
                g.drawString(font, line, tx, ty, Theme.accent(), false);
                ty += 10;
            }
        }
        if (c.frac() >= 0) {
            g.fill(tx, ty + 1, tx + tw, ty + 5, 0xFF22262C);
            int fw = (int) (tw * Mth.clamp(c.frac(), 0f, 1f));
            if (fw > 0) g.fill(tx, ty + 1, tx + fw, ty + 5, c.badgeColor());
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
            if (chipsOn()) {
                for (int i = 0; i < CHIPS.length; i++) {
                    if (inside(mx, my, chipX(i), cy(), font.width(CHIPS[i]) + 14, CHIP_H)) {
                        advFilter = i;
                        scroll = 0;
                        rebuild();
                        return true;
                    }
                }
            }
        }
        if ((button == 0 || button == 1) && !pages.isEmpty() && inside(mx, my, cx(), ctop(), cw(), cheight())) {
            int y = ctop() - (int) scroll;
            int idx = 0;
            for (Row r : pages.get(sel).rows()) {
                int h = rh(r);
                boolean hit = my >= y && my < y + h;
                if (scope == SET && r.kind() == Row.Kind.ROW) {
                    if (hit) {
                        Theme.cycle(idx, button == 1 ? -1 : 1);
                        rebuild();
                        return true;
                    }
                    idx++;
                } else if (scope == ADV && sel == 0 && r.kind() == Row.Kind.CARD) {
                    if (hit && button == 0) {
                        if (idx + 1 < pages.size()) selectPage(idx + 1);
                        return true;
                    }
                    idx++;
                }
                y += h;
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
                scroll = Mth.clamp(scroll - cheight() * 0.8, 0.0, (double) maxScroll);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scroll = Mth.clamp(scroll + cheight() * 0.8, 0.0, (double) maxScroll);
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
