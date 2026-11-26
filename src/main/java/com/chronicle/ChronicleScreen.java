package com.chronicle;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Два режима одного окна: «Статистика» и «Достижения» (с переходом друг в друга). */
public class ChronicleScreen extends Screen {
    public static final int WORLD = 0;
    public static final int ALL = 1;
    public static final int ADV = 2;
    public static final int SET = 3;

    private static final int DIM = 0xFF9AA0A8;
    private static final int TEXT = 0xFFE8EAED;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF7BD88F;
    private static final int GREEN_FULL = 0xFF46C37B;
    private static final int LINE = 0xFF555B63;

    private static final int SIDE = 128;
    private static final int HEAD = 42;
    private static final int TAB_H = 16;
    private static final int CHIP_H = 16;
    private static final int DETAIL_H = 66;

    private static final int NODE = 26;
    private static final int TX = 32;
    private static final int TY = 30;
    private static final int PAD = 10;

    private static final Component[] CHIPS = {
        Component.translatable("chronicle.filter.all"),
        Component.translatable("chronicle.filter.remaining"),
        Component.translatable("chronicle.filter.done")
    };

    private static int lastScope;
    private static int lastPage;
    private static int lastFilter;
    private static boolean lastAdvMode;

    private final Screen parent;
    private boolean advMode;
    private int scope;
    private int sel;
    private int advFilter = lastFilter;
    private List<PageBuilder.Page> pages = List.of();
    private double scroll;
    private double panX;
    private int maxScroll;
    private int maxPanX;
    private int tickCount;

    private String selId;
    private String hoverId;
    private final Set<String> expanded = new HashSet<>();
    private float treeMinX;
    private float treeMinY;

    private int px;
    private int py;
    private int pw;
    private int ph;

    private Component[] tabLabels = new Component[0];
    private int[] tabScopes = new int[0];
    private int[] tabX = new int[0];
    private int[] tabW = new int[0];
    private int linkTab = -1;

    private EditBox box;
    private boolean searchOpen;
    private String query = "";

    public ChronicleScreen() {
        this(null, lastScope, lastPage);
        this.advMode = lastAdvMode;
    }

    public ChronicleScreen(Screen parent, int scope, int page) {
        super(Component.translatable("chronicle.title"));
        this.parent = parent;
        this.scope = scope;
        this.sel = page;
        this.advMode = scope == ADV;
    }

    @Override
    protected void init() {
        if (minecraft != null && minecraft.player == null) {
            advMode = false;
            if (scope == WORLD || scope == ADV) {
                scope = ALL;
                sel = 0;
            }
        }
        pw = Math.min(width - 16, 480);
        ph = Math.min(height - 16, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        layoutTabs();
        box = new EditBox(font, closeX() - 3 - 150, py + 29, 150, 12, Component.empty());
        box.setMaxLength(40);
        box.setHint(Component.translatable("chronicle.search.hint"));
        box.setValue(query);
        box.setResponder(v -> {
            query = v;
            resetView();
            rebuild();
        });
        box.visible = searchOpen;
        addWidget(box);
        ClientEvents.requestStats();
        rebuild();
    }

    /** Вкладки зависят от режима: у достижений свой набор, у статистики свой. */
    private void layoutTabs() {
        if (advMode) {
            tabLabels = new Component[] {
                Component.translatable("chronicle.tab.adv"),
                Component.translatable("chronicle.tab.settings"),
                Component.translatable("chronicle.tab.stats_link")
            };
            tabScopes = new int[] {ADV, SET, WORLD};
            linkTab = 2;
        } else {
            tabLabels = new Component[] {
                Component.translatable("chronicle.tab.world"),
                Component.translatable("chronicle.tab.all"),
                Component.translatable("chronicle.tab.settings"),
                Component.translatable("chronicle.tab.adv_link")
            };
            tabScopes = new int[] {WORLD, ALL, SET, ADV};
            linkTab = 3;
        }
        tabX = new int[tabLabels.length];
        tabW = new int[tabLabels.length];
        int x = px + pw - 12 - TAB_H - 6; // справа от вкладок остаётся место под крестик
        for (int i = tabLabels.length - 1; i >= 0; i--) {
            tabW[i] = font.width(tabLabels[i]) + 14;
            x -= tabW[i];
            tabX[i] = x;
            x -= 3;
        }
    }

    @Override
    public void removed() {
        lastScope = scope;
        lastPage = sel;
        lastFilter = advFilter;
        lastAdvMode = advMode;
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

    // ---------- геометрия ----------

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
        return scope == ADV && sel > 0 && !pages.isEmpty() && !searching();
    }

    private int ctop() {
        return cy() + (chipsOn() ? CHIP_H + 6 : 0);
    }

    private int cheight() {
        return ph - HEAD - 16 - (chipsOn() ? CHIP_H + 6 : 0);
    }

    private int style() {
        return Theme.cfg().advStyle;
    }

    /** «Древо» рисуется прямо экраном, а не строками. */
    private boolean customView() {
        return scope == ADV && sel > 0 && !pages.isEmpty() && !searching() && style() == 1 && pages.get(sel).items() != null;
    }

    private int viewH() {
        return customView() ? cheight() - DETAIL_H - 6 : cheight();
    }

    private int textW() {
        return cw() - 42 - 8;
    }

    private int cardHeight(Row.Card c) {
        int inner = 13;
        if (c.desc() != null) inner += font.split(c.desc(), textW()).size() * 10;
        if (c.tip() != null && c.mode() == 2) inner += font.split(c.tip(), textW()).size() * 10;
        if (c.frac() >= 0) inner += 8;
        return 12 + Math.max(26, inner) + 4;
    }

    private int rh(Row r) {
        return r.kind() == Row.Kind.CARD ? cardHeight(r.card()) : r.height();
    }

    private List<AdvItem> items() {
        if (pages.isEmpty()) return List.of();
        List<AdvItem> it = pages.get(sel).items();
        return it == null ? List.of() : it;
    }

    private void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        boolean q = searching();
        pages = switch (scope) {
            case WORLD -> q ? List.of(SearchPages.stats(mc, query)) : PageBuilder.world(mc);
            case ALL -> q ? List.of(SearchPages.worlds(query)) : PageBuilder.all(mc);
            case ADV -> ExtraPages.advancements(mc, advFilter, style(), expanded, q ? query : "");
            default -> ExtraPages.settings();
        };
        sel = pages.isEmpty() ? 0 : Mth.clamp(sel, 0, pages.size() - 1);

        maxPanX = 0;
        if (customView()) {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (AdvItem a : items()) {
                minX = Math.min(minX, a.x());
                minY = Math.min(minY, a.y());
                maxX = Math.max(maxX, a.x());
                maxY = Math.max(maxY, a.y());
            }
            if (items().isEmpty()) {
                minX = 0;
                minY = 0;
                maxX = 0;
                maxY = 0;
            }
            treeMinX = minX;
            treeMinY = minY;
            int tw = Math.round((maxX - minX) * TX) + NODE + 2 * PAD;
            int th = Math.round((maxY - minY) * TY) + NODE + 2 * PAD;
            maxScroll = Math.max(0, th - viewH());
            maxPanX = Math.max(0, tw - cw());
        } else {
            int total = 0;
            if (!pages.isEmpty()) {
                for (Row r : pages.get(sel).rows()) total += rh(r);
            }
            maxScroll = Math.max(0, total - cheight() + 4);
        }
        scroll = Mth.clamp(scroll, 0.0, (double) maxScroll);
        panX = Mth.clamp(panX, 0.0, (double) maxPanX);
    }

    private int onAccent() {
        int c = Theme.accent();
        int l = ((c >> 16 & 255) * 299 + (c >> 8 & 255) * 587 + (c & 255) * 114) / 1000;
        return l > 140 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private boolean searching() {
        return searchOpen && scope != SET && !query.isBlank();
    }

    private void toggleSearch() {
        searchOpen = !searchOpen;
        box.visible = searchOpen;
        if (searchOpen) {
            setFocused(box);
            box.setFocused(true);
        } else {
            box.setFocused(false);
            setFocused(null);
            box.setValue("");
            query = "";
            resetView();
            rebuild();
        }
    }

    private void drawLoupe(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y + 1, x + 7, y + 2, color);
        g.fill(x + 2, y + 6, x + 7, y + 7, color);
        g.fill(x + 1, y + 2, x + 2, y + 6, color);
        g.fill(x + 7, y + 2, x + 8, y + 6, color);
        g.fill(x + 7, y + 7, x + 8, y + 8, color);
        g.fill(x + 8, y + 8, x + 9, y + 9, color);
        g.fill(x + 9, y + 9, x + 10, y + 10, color);
    }

    private Component screenTitle() {
        return Component.translatable(advMode ? "chronicle.title.adv" : "chronicle.title");
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
        hoverId = null;
        if (customView()) {
            drawTree(g, mx, my);
            drawDetail(g);
        } else {
            renderContent(g, mx, my);
        }
    }

    /** Текст в рамку: длинный либо «ездит» туда-сюда (marquee), либо обрезается многоточием. */
    private void drawText(GuiGraphics g, Component c, int x, int y, int maxW, int color, boolean marquee) {
        int w = Math.max(8, maxW);
        int tw = font.width(c);
        if (tw <= w) {
            g.drawString(font, Language.getInstance().getVisualOrder(c), x, y, color, false);
            return;
        }
        if (!marquee) {
            FormattedText t = FormattedText.composite(font.substrByWidth(c, w - font.width("…")),
                    FormattedText.of("…"));
            g.drawString(font, Language.getInstance().getVisualOrder(t), x, y, color, false);
            return;
        }
        int over = tw - w;
        long pause = 1200;
        long run = over * 30L;
        long phase = System.currentTimeMillis() % (2 * (pause + run));
        int off;
        if (phase < pause) off = 0;
        else if (phase < pause + run) off = (int) ((phase - pause) / 30);
        else if (phase < 2 * pause + run) off = over;
        else off = over - (int) ((phase - 2 * pause - run) / 30);
        off = Mth.clamp(off, 0, over);
        g.enableScissor(x, y - 1, x + w, y + 10);
        g.drawString(font, Language.getInstance().getVisualOrder(c), x - off, y, color, false);
        g.disableScissor();
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        g.fill(px, py, px + pw, py + HEAD, Theme.shade(0.05f));
        g.fill(px, py + HEAD, px + pw, py + HEAD + 1, Theme.accent());
        g.fill(px, py, px + 3, py + HEAD, Theme.accent());

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(px + 12, py + 8, 0);
        ps.scale(1.6f, 1.6f, 1f);
        g.drawString(font, screenTitle().copy().withStyle(ChatFormatting.BOLD), 0, 0, Theme.accent(), true);
        ps.popPose();

        if (!searchOpen) g.drawString(font, subtitle(), px + 12, py + 28, DIM, false);

        if (scope != SET) {
            boolean lh = inside(mx, my, closeX(), py + 29, TAB_H, 12);
            g.fill(closeX(), py + 29, closeX() + TAB_H, py + 41,
                    searchOpen ? Theme.accent() : lh ? 0xFF2C3139 : 0xFF22262C);
            drawLoupe(g, closeX() + 3, py + 30, searchOpen ? onAccent() : 0xFFD5D8DC);
        }
        if (searchOpen && scope != SET) box.render(g, mx, my, 0f);

        // крестик закрытия
        boolean xHover = inside(mx, my, closeX(), py + 8, TAB_H, TAB_H);
        g.fill(closeX(), py + 8, closeX() + TAB_H, py + 8 + TAB_H, xHover ? 0xFFB23A3A : 0xFF22262C);
        g.drawString(font, "X", closeX() + (TAB_H - font.width("X")) / 2 + 1, py + 12, WHITE, false);

        for (int i = 0; i < tabLabels.length; i++) {
            boolean link = i == linkTab;
            boolean on = !link && tabScopes[i] == scope;
            boolean hover = inside(mx, my, tabX[i], py + 12, tabW[i], TAB_H);
            int x = tabX[i];
            int y = py + 12;
            if (link) { // кнопка перехода в другое окно: контур вместо заливки
                g.fill(x, y, x + tabW[i], y + TAB_H, Theme.accent());
                g.fill(x + 1, y + 1, x + tabW[i] - 1, y + TAB_H - 1, hover ? 0xFF2C3139 : 0xFF14161A);
                g.drawString(font, tabLabels[i], x + 7, y + 4, Theme.accent(), false);
            } else {
                g.fill(x, y, x + tabW[i], y + TAB_H, on ? Theme.accent() : hover ? 0xFF2C3139 : 0xFF22262C);
                g.drawString(font, tabLabels[i], x + 7, y + 4, on ? onAccent() : 0xFFD5D8DC, false);
            }
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
            drawText(g, p.title(), tx, y + 6, x + w - tx - 4, on ? WHITE : DIM, on || hover);
        }
    }

    private int chipX(int i) {
        int x = cx();
        for (int k = 0; k < i; k++) x += font.width(CHIPS[k]) + 14 + 4;
        return x;
    }

    private Component styleLabel() {
        return Component.translatable("chronicle.adv.view").append(": ")
                .append(Component.translatable("chronicle.style." + Theme.STYLE_KEYS[style()]));
    }

    private int styleW() {
        return font.width(styleLabel()) + 14;
    }

    private int styleX() {
        return cx() + cw() - styleW();
    }

    private void renderChips(GuiGraphics g, int mx, int my) {
        if (!chipsOn()) return;
        for (int i = 0; i < CHIPS.length; i++) {
            int w = font.width(CHIPS[i]) + 14;
            boolean on = i == advFilter;
            boolean hover = inside(mx, my, chipX(i), cy(), w, CHIP_H);
            g.fill(chipX(i), cy(), chipX(i) + w, cy() + CHIP_H,
                    on ? Theme.accent() : hover ? 0xFF2C3139 : 0xFF22262C);
            g.drawString(font, CHIPS[i], chipX(i) + 7, cy() + 4, on ? onAccent() : 0xFFD5D8DC, false);
        }
        boolean hover = inside(mx, my, styleX(), cy(), styleW(), CHIP_H);
        g.fill(styleX(), cy(), styleX() + styleW(), cy() + CHIP_H, hover ? 0xFF3A414B : 0xFF2C3139);
        g.drawString(font, styleLabel(), styleX() + 7, cy() + 4, WHITE, false);
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
                boolean hover = scope == ADV && r.kind() == Row.Kind.CARD
                        && inside(mx, my, cx, y, cw, h - 4) && inside(mx, my, cx, top, cw, ch);
                drawRow(g, r, cx, y, cw, h, alt, hover);
            }
            if (r.kind() == Row.Kind.ROW) alt++;
            y += h;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int tx = cx + cw + 3;
            boolean barHover = draggingBar || inside(mx, my, cx + cw + 1, top, 12, ch);
            g.fill(tx, top, tx + 6, top + ch, 0xFF22262C);
            int total = maxScroll + ch - 4;
            int thumb = Math.max(16, ch * ch / Math.max(total, 1));
            int ty = top + (int) ((ch - thumb) * (scroll / maxScroll));
            g.fill(tx, ty, tx + 6, ty + thumb, barHover ? WHITE : Theme.accent());
        }
    }

    private void drawRow(GuiGraphics g, Row r, int cx, int y, int cw, int h, int alt, boolean hover) {
        switch (r.kind()) {
            case HEADER -> {
                drawText(g, r.label().copy().withStyle(ChatFormatting.BOLD), cx + 2, y + 8, cw - 4,
                        Theme.accent(), false);
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
                drawText(g, r.label(), cx + 6, y + 3, cw - 20 - vw, TEXT, false);
                g.drawString(font, r.value(), cx + cw - 6 - vw, y + 3, WHITE, false);
            }
            case NOTE -> drawText(g, r.label(), cx + 6, y + 2, cw - 12, DIM, false);
            case CARD -> drawCard(g, r.card(), cx, y, cw, h, hover);
            case SPACER -> {
                // пустое место
            }
        }
    }

    /** Карточка: иконка, название, описание. Совет скрыт за стрелкой и раскрывается по клику. */
    private void drawCard(GuiGraphics g, Row.Card c, int x, int y, int w, int h, boolean hover) {
        int stripe = c.done() ? GREEN_FULL : Theme.accent();
        int bodyH = h - 4;
        g.fill(x, y, x + w, y + bodyH, hover ? 0x33FFFFFF : 0x1EFFFFFF);
        g.fill(x, y, x + 3, y + bodyH, stripe);

        int bx = x + 9;
        int by = y + 6;
        g.fill(bx, by, bx + 26, by + 26, c.done() ? 0xFF2E5A3E : 0xFF3A3F47);
        g.fill(bx + 1, by + 1, bx + 25, by + 25, 0xFF14161A);
        g.renderItem(c.icon(), bx + 5, by + 5);

        int tx = x + 42;
        int tw = w - 42 - 8;
        boolean hasTip = c.tip() != null;
        boolean open = hasTip && c.mode() == 2;

        String right;
        int rightColor;
        if (hasTip) {
            right = Component.translatable("chronicle.adv.tip_toggle").getString() + (open ? " v" : " >");
            rightColor = Theme.accent();
        } else {
            right = c.badge();
            rightColor = c.badgeColor();
        }
        int rw = font.width(right);
        g.drawString(font, right, x + w - 8 - rw, y + 6, rightColor, false);
        drawText(g, c.title().copy().withStyle(ChatFormatting.BOLD), tx, y + 6, tw - rw - 8,
                c.done() ? GREEN : WHITE, true);

        int ty = y + 19;
        if (c.desc() != null) {
            for (FormattedCharSequence line : font.split(c.desc(), tw)) {
                g.drawString(font, line, tx, ty, DIM, false);
                ty += 10;
            }
        }
        if (open) {
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

    // ---------- древо ----------

    private void drawTile(GuiGraphics g, AdvItem it, int x, int y, int size, boolean highlight, boolean dimmed) {
        int border = it.done() ? GREEN_FULL : Theme.accent();
        g.fill(x, y, x + size, y + size, border);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF14161A);
        g.renderItem(it.icon(), x + (size - 16) / 2, y + (size - 16) / 2);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0, 0, 200);
        if (dimmed) g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xBB000000);
        else if (!it.done()) g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x77000000);
        if (highlight) {
            g.fill(x - 1, y - 1, x + size + 1, y, WHITE);
            g.fill(x - 1, y + size, x + size + 1, y + size + 1, WHITE);
            g.fill(x - 1, y, x, y + size, WHITE);
            g.fill(x + size, y, x + size + 1, y + size, WHITE);
        }
        ps.popPose();
    }

    private boolean dimmedByFilter(AdvItem a) {
        return (advFilter == 1 && a.done()) || (advFilter == 2 && !a.done());
    }

    private int nodeX(AdvItem a) {
        return cx() + PAD + Math.round((a.x() - treeMinX) * TX) - (int) panX;
    }

    private int nodeY(AdvItem a) {
        return ctop() + PAD + Math.round((a.y() - treeMinY) * TY) - (int) scroll;
    }

    private void drawTree(GuiGraphics g, int mx, int my) {
        int vx = cx();
        int top = ctop();
        int vw = cw();
        int vh = viewH();
        boolean inView = inside(mx, my, vx, top, vw, vh);

        Map<String, AdvItem> byId = new HashMap<>();
        for (AdvItem a : items()) byId.put(a.id(), a);

        g.enableScissor(vx, top, vx + vw, top + vh);
        g.fill(vx, top, vx + vw, top + vh, 0x33000000);

        for (AdvItem a : items()) {
            AdvItem p = a.parent() == null ? null : byId.get(a.parent());
            if (p == null) continue;
            int x1 = nodeX(p) + NODE;
            int y1 = nodeY(p) + NODE / 2;
            int x2 = nodeX(a);
            int y2 = nodeY(a) + NODE / 2;
            int mid = x1 + Math.max(4, (x2 - x1) / 2);
            int col = p.done() && a.done() ? GREEN_FULL : LINE;
            g.fill(Math.min(x1, mid), y1, Math.max(x1, mid) + 2, y1 + 2, col);
            g.fill(mid, Math.min(y1, y2), mid + 2, Math.max(y1, y2) + 2, col);
            g.fill(Math.min(mid, x2), y2, Math.max(mid, x2) + 1, y2 + 2, col);
        }
        for (AdvItem a : items()) {
            int x = nodeX(a);
            int y = nodeY(a);
            if (x + NODE < vx || x > vx + vw || y + NODE < top || y > top + vh) continue;
            boolean hov = inView && inside(mx, my, x, y, NODE, NODE);
            if (hov) hoverId = a.id();
            drawTile(g, a, x, y, NODE, hov || a.id().equals(selId), dimmedByFilter(a));
        }
        g.disableScissor();
    }

    private AdvItem findItem(String id) {
        if (id == null) return null;
        for (AdvItem a : items()) if (a.id().equals(id)) return a;
        return null;
    }

    private void drawDetail(GuiGraphics g) {
        int x = cx();
        int y = ctop() + viewH() + 6;
        int w = cw();
        int h = DETAIL_H;
        g.fill(x, y, x + w, y + h, 0x1EFFFFFF);
        g.fill(x, y, x + 3, y + h, Theme.accent());

        AdvItem a = findItem(hoverId != null ? hoverId : selId);
        g.enableScissor(x, y, x + w, y + h);
        if (a == null) {
            int done = 0;
            int total = 0;
            for (AdvItem it : items()) {
                if (it.root()) continue;
                total++;
                if (it.done()) done++;
            }
            float frac = total == 0 ? 0f : done / (float) total;
            g.drawString(font, Component.translatable("chronicle.adv.progress").withStyle(ChatFormatting.BOLD),
                    x + 10, y + 8, Theme.accent(), false);
            String s = done + " / " + total + "  (" + Math.round(frac * 100) + "%)";
            g.drawString(font, s, x + w - 10 - font.width(s), y + 8, WHITE, false);
            g.fill(x + 10, y + 22, x + w - 10, y + 28, 0xFF22262C);
            int fw = (int) ((w - 20) * frac);
            if (fw > 0) g.fill(x + 10, y + 22, x + 10 + fw, y + 28, Theme.accent());
            drawText(g, Component.translatable("chronicle.adv.pick"), x + 10, y + 38, w - 20, DIM, false);
        } else {
            int bx = x + 9;
            int by = y + 6;
            g.fill(bx, by, bx + 26, by + 26, a.done() ? 0xFF2E5A3E : 0xFF3A3F47);
            g.fill(bx + 1, by + 1, bx + 25, by + 25, 0xFF14161A);
            g.renderItem(a.icon(), bx + 5, by + 5);

            String badge = a.done() ? Component.translatable("chronicle.adv.done").getString() : "";
            int bw = font.width(badge);
            drawText(g, a.title().copy().withStyle(ChatFormatting.BOLD), x + 42, y + 6, w - 42 - bw - 18,
                    a.done() ? GREEN : WHITE, true);
            g.drawString(font, badge, x + w - 8 - bw, y + 6, GREEN_FULL, false);

            int ty = y + 19;
            int tw = w - 42 - 8;
            for (FormattedCharSequence line : font.split(a.desc(), tw)) {
                g.drawString(font, line, x + 42, ty, DIM, false);
                ty += 10;
            }
            if (!a.done() && a.tip() != null) {
                for (FormattedCharSequence line : font.split(a.tip(), tw)) {
                    g.drawString(font, line, x + 42, ty, Theme.accent(), false);
                    ty += 10;
                }
            }
        }
        g.disableScissor();
    }

    // ---------- ввод ----------

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void resetView() {
        selId = null;
        scroll = 0;
        panX = 0;
    }

    private int closeX() {
        return px + pw - 12 - TAB_H;
    }

    // ---------- полоса прокрутки (тянется мышью) ----------

    private boolean draggingBar;

    private boolean overBar(double mx, double my) {
        return maxScroll > 0 && !customView()
                && inside(mx, my, cx() + cw() + 1, ctop(), 12, cheight());
    }

    private void dragBarTo(double my) {
        int ch = cheight();
        int total = maxScroll + ch - 4;
        int thumb = Math.max(16, ch * ch / Math.max(total, 1));
        double ratio = (my - ctop() - thumb / 2.0) / Math.max(1, ch - thumb);
        scroll = Mth.clamp(ratio, 0.0, 1.0) * maxScroll;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingBar = false;
        return super.mouseReleased(mx, my, button);
    }

    private void activateTab(int i) {
        if (i == linkTab) { // переход между окнами «Статистика» и «Достижения»
            if (advMode) {
                advMode = false;
                scope = minecraft != null && minecraft.player == null ? ALL : WORLD;
            } else {
                advMode = true;
                scope = ADV;
            }
            sel = 0;
            resetView();
            layoutTabs();
            rebuild();
            return;
        }
        switchScope(tabScopes[i]);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (inside(mx, my, closeX(), py + 12, TAB_H, TAB_H)) {
                onClose();
                return true;
            }
            if (overBar(mx, my)) {
                draggingBar = true;
                dragBarTo(my);
                return true;
            }
            if (scope != SET && inside(mx, my, closeX(), py + 29, TAB_H, 12)) {
                toggleSearch();
                return true;
            }
            if (searchOpen && scope != SET && box.mouseClicked(mx, my, button)) {
                setFocused(box);
                return true;
            }
            if (searchOpen) box.setFocused(false);
            for (int i = 0; i < tabLabels.length; i++) {
                if (inside(mx, my, tabX[i], py + 12, tabW[i], TAB_H)) {
                    activateTab(i);
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
                        resetView();
                        rebuild();
                        return true;
                    }
                }
                if (inside(mx, my, styleX(), cy(), styleW(), CHIP_H)) {
                    Theme.cycle(6, 1);
                    resetView();
                    rebuild();
                    return true;
                }
            }
            if (customView() && hoverId != null) {
                selId = hoverId;
                return true;
            }
        }
        if ((button == 0 || button == 1) && !customView() && !pages.isEmpty()
                && inside(mx, my, cx(), ctop(), cw(), cheight())) {
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
                } else if (scope == ADV && r.kind() == Row.Kind.CARD) {
                    if (sel == 0 && !searching()) { // обзор: клик по категории
                        if (hit && button == 0) {
                            if (idx + 1 < pages.size()) selectPage(idx + 1);
                            return true;
                        }
                        idx++;
                    } else if (hit && button == 0 && r.card().tip() != null) { // раскрыть/свернуть совет
                        String id = r.card().id();
                        if (!expanded.remove(id)) expanded.add(id);
                        rebuild();
                        return true;
                    }
                }
                y += h;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && draggingBar) {
            dragBarTo(my);
            return true;
        }
        if (button == 0 && customView() && inside(mx, my, cx(), ctop(), cw(), viewH())) {
            panX = Mth.clamp(panX - dx, 0.0, (double) maxPanX);
            scroll = Mth.clamp(scroll - dy, 0.0, (double) maxScroll);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        double d = scrollY != 0 ? scrollY : scrollX;
        if (customView() && hasShiftDown()) {
            panX = Mth.clamp(panX - d * 28, 0.0, (double) maxPanX);
        } else {
            scroll = Mth.clamp(scroll - d * 28, 0.0, (double) maxScroll);
        }
        return true;
    }

    /** Соседняя вкладка (без кнопки-перехода) в сторону dir. */
    private void stepTab(int dir) {
        int cur = 0;
        for (int i = 0; i < tabScopes.length; i++) {
            if (i != linkTab && tabScopes[i] == scope) cur = i;
        }
        int next = cur + dir;
        if (next < 0 || next >= tabScopes.length || next == linkTab) return;
        switchScope(tabScopes[next]);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchOpen && scope != SET && box.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                toggleSearch();
                return true;
            }
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
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
                stepTab(-1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                stepTab(1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                scroll = Mth.clamp(scroll - viewH() * 0.8, 0.0, (double) maxScroll);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scroll = Mth.clamp(scroll + viewH() * 0.8, 0.0, (double) maxScroll);
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
        resetView();
        rebuild();
    }

    private void selectPage(int i) {
        if (sel == i) return;
        sel = i;
        resetView();
        rebuild();
    }
}
