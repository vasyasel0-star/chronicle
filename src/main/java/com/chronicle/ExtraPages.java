package com.chronicle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/** Страницы «Достижения» (по вкладкам-корням как в ванили, моды отдельно) и «Настройки». */
public final class ExtraPages {
    private record Group(String key, Component name, ItemStack icon, List<AdvItem> all,
                         List<AdvItem> kids, String mod) {}

    private static final List<String> ORDER = List.of(
            "minecraft:story/root", "minecraft:nether/root", "minecraft:end/root",
            "minecraft:adventure/root", "minecraft:husbandry/root");

    private static final int GREEN = 0xFF46C37B;
    private static final int GRAY = 0xFF9AA0A8;

    private ExtraPages() {}

    private static MutableComponent t(String key) {
        return Component.translatable("chronicle." + key);
    }

    private static String pct(int done, int total) {
        return done + " / " + total + "  (" + (total == 0 ? 0 : Math.round(done * 100f / total)) + "%)";
    }

    private static int depth(ResourceLocation id, Map<ResourceLocation, ResourceLocation> parentOf) {
        int d = 0;
        ResourceLocation p = parentOf.get(id);
        while (p != null && d < 50) {
            d++;
            p = parentOf.get(p);
        }
        return d;
    }

    /** Корень вкладки: поднимаемся по родителям до самого верха (так делает и ванильное окно). */
    private static ResourceLocation rootOf(ResourceLocation id, Map<ResourceLocation, ResourceLocation> parentOf) {
        ResourceLocation r = id;
        int n = 0;
        while (parentOf.containsKey(r) && n++ < 50) r = parentOf.get(r);
        return r;
    }

    private static String modName(String ns) {
        String n = ModList.get().getModContainerById(ns)
                .map(m -> m.getModInfo().getDisplayName()).orElse(null);
        if (n != null) return n;
        String x = ns.replace('_', ' ');
        return x.isEmpty() ? ns : Character.toUpperCase(x.charAt(0)) + x.substring(1);
    }

    private static AdvItem asChild(AdvItem a) {
        return new AdvItem(a.id(), a.parent(), a.icon(), a.title(), a.desc(), a.tip(), a.done(),
                a.avail(), false, a.x(), a.y(), a.depth());
    }

    private static Row.Card card(AdvItem a, int mode) {
        String badge = I18n.get(a.done() ? "chronicle.adv.done" : "chronicle.adv.todo");
        int color = a.done() ? GREEN : GRAY;
        return new Row.Card(a.icon(), a.title(), a.desc(), a.done() ? null : a.tip(), badge, color,
                a.done(), -1f, a.id(), mode);
    }

    /** filter: 0 все, 1 осталось, 2 выполнено. style: 0 карточки, 1 список, 2 сетка, 3 дерево. */
    public static List<PageBuilder.Page> advancements(Minecraft mc, int filter, int style, Set<String> expanded,
                                                      String query) {
        if (mc.player == null || mc.getConnection() == null) return List.of();
        ClientAdvancements ca = mc.getConnection().getAdvancements();

        Map<ResourceLocation, ResourceLocation> parentOf = new HashMap<>();
        Set<ResourceLocation> doneIds = new HashSet<>();
        for (Map.Entry<AdvancementHolder, AdvancementProgress> e : ca.progress.entrySet()) {
            ResourceLocation id = e.getKey().id();
            e.getKey().value().parent().ifPresent(p -> parentOf.put(id, p));
            if (e.getValue().isDone()) doneIds.add(id);
        }

        Map<ResourceLocation, List<AdvItem>> byRoot = new LinkedHashMap<>();
        for (Map.Entry<AdvancementHolder, AdvancementProgress> e : ca.progress.entrySet()) {
            DisplayInfo d = e.getKey().value().display().orElse(null);
            if (d == null) continue;
            ResourceLocation id = e.getKey().id();
            ResourceLocation parent = parentOf.get(id);
            boolean isRoot = parent == null;
            Component tip = null;
            if (!isRoot && id.getNamespace().equals("minecraft")) {
                String tipKey = "chronicle.tip." + id.getPath().replace('/', '.');
                if (I18n.exists(tipKey)) tip = t("tip.label").append(Component.translatable(tipKey));
            }
            AdvItem it = new AdvItem(id.toString(), parent == null ? null : parent.toString(), d.getIcon(),
                    d.getTitle(), d.getDescription(), tip, e.getValue().isDone(),
                    parent == null || doneIds.contains(parent), isRoot, d.getX(), d.getY(),
                    depth(id, parentOf));
            byRoot.computeIfAbsent(rootOf(id, parentOf), k -> new ArrayList<>()).add(it);
        }

        List<Group> groups = new ArrayList<>();
        Map<String, List<AdvItem>> solo = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<AdvItem>> g : byRoot.entrySet()) {
            AdvItem root = null;
            List<AdvItem> kids = new ArrayList<>();
            for (AdvItem a : g.getValue()) {
                if (a.root()) root = a;
                else kids.add(a);
            }
            String ns = g.getKey().getNamespace();
            if (kids.isEmpty()) { // одиночные достижения без вкладки собираем по модам
                if (root != null) solo.computeIfAbsent(ns, k -> new ArrayList<>()).add(asChild(root));
                continue;
            }
            Component name = root != null ? root.title() : Component.literal(modName(ns));
            ItemStack icon = root != null ? root.icon() : kids.get(0).icon();
            groups.add(new Group(g.getKey().toString(), name, icon, g.getValue(), kids,
                    ns.equals("minecraft") ? null : modName(ns)));
        }
        for (Map.Entry<String, List<AdvItem>> s : solo.entrySet()) {
            List<AdvItem> kids = s.getValue();
            groups.add(new Group(s.getKey() + "#solo", Component.literal(modName(s.getKey())),
                    kids.get(0).icon(), kids, kids, s.getKey().equals("minecraft") ? null : modName(s.getKey())));
        }
        groups.sort(Comparator.comparingInt((Group g) -> {
            int i = ORDER.indexOf(g.key());
            return i < 0 ? 100 : i;
        }).thenComparing(g -> g.name().getString()));

        if (query != null && !query.isBlank()) return List.of(searchPage(groups, query, expanded));

        // обзор: карточки категорий (клик открывает категорию)
        List<Row> ov = new ArrayList<>();
        ov.add(Row.header(t("adv.overview")));
        int allDone = 0;
        int allTotal = 0;
        for (Group g : groups) {
            int done = 0;
            for (AdvItem a : g.kids()) if (a.done()) done++;
            int total = g.kids().size();
            allDone += done;
            allTotal += total;
            ov.add(Row.card(new Row.Card(g.icon(), g.name(), g.mod() == null ? null : Component.literal(g.mod()),
                    null, pct(done, total), done == total ? GREEN : Theme.accent(), done == total,
                    total == 0 ? 0f : done / (float) total, g.key(), 0)));
        }
        ov.add(Row.spacer());
        ov.add(Row.card(new Row.Card(new ItemStack(Items.NETHER_STAR), t("adv.total"), null, null,
                pct(allDone, allTotal), Theme.accent(), false,
                allTotal == 0 ? 0f : allDone / (float) allTotal, "total", 0)));
        ov.add(Row.note(t("adv.hint")));

        List<PageBuilder.Page> pages = new ArrayList<>();
        pages.add(new PageBuilder.Page(t("adv.overview"), ov, new ItemStack(Items.KNOWLEDGE_BOOK)));

        for (Group g : groups) {
            List<AdvItem> todo = new ArrayList<>();
            List<AdvItem> done = new ArrayList<>();
            for (AdvItem a : g.kids()) (a.done() ? done : todo).add(a);
            todo.sort(Comparator.comparingInt(AdvItem::depth).thenComparing(AdvItem::id));
            done.sort(Comparator.comparingInt(AdvItem::depth).thenComparing(AdvItem::id));

            int total = todo.size() + done.size();
            List<Row> r = new ArrayList<>();
            r.add(Row.header(g.name()));
            if (g.mod() != null) r.add(Row.note(t("adv.mod").append(" " + g.mod())));
            r.add(Row.bar(t("adv.progress"), pct(done.size(), total),
                    total == 0 ? 0f : done.size() / (float) total));
            if (style == 0) { // карточки строятся строками, древо рисуется самим экраном
                r.add(Row.spacer());
                if (filter != 2 && !todo.isEmpty()) {
                    r.add(Row.header(t("adv.remaining").append(" (" + todo.size() + ")")));
                    for (AdvItem a : todo) r.add(Row.card(card(a, mode(style, a, expanded))));
                }
                if (filter != 1 && !done.isEmpty()) {
                    r.add(Row.header(t("adv.completed").append(" (" + done.size() + ")")));
                    for (AdvItem a : done) r.add(Row.card(card(a, mode(style, a, expanded))));
                }
                if ((filter == 1 && todo.isEmpty()) || (filter == 2 && done.isEmpty())) {
                    r.add(Row.note(t("none")));
                }
            }
            pages.add(new PageBuilder.Page(g.name(), r, g.icon(), g.all()));
        }
        return pages;
    }

    /** Поиск по всем категориям сразу: название, описание, совет, категория и название мода. */
    private static PageBuilder.Page searchPage(List<Group> groups, String query, Set<String> expanded) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<Row> cards = new ArrayList<>();
        for (Group g : groups) {
            String mod = g.mod() == null ? "" : g.mod();
            for (AdvItem a : g.kids()) {
                if (cards.size() >= 100) break;
                String hay = (a.title().getString() + " " + a.desc().getString() + " "
                        + (a.tip() == null ? "" : a.tip().getString()) + " "
                        + g.name().getString() + " " + mod).toLowerCase(Locale.ROOT);
                if (hay.contains(needle)) cards.add(Row.card(card(a, expanded.contains(a.id()) ? 2 : 0)));
            }
        }
        List<Row> rows = new ArrayList<>();
        rows.add(Row.header(t("search.results").append(" (" + cards.size() + ")")));
        if (cards.isEmpty()) rows.add(Row.note(t("search.none")));
        rows.addAll(cards);
        return new PageBuilder.Page(t("search.results"), rows, new ItemStack(Items.COMPASS));
    }

    private static int mode(int style, AdvItem a, Set<String> expanded) {
        return expanded.contains(a.id()) ? 2 : 0;
    }

    public static List<PageBuilder.Page> settings() {
        Theme.Cfg c = Theme.cfg();
        List<Row> r = new ArrayList<>();
        r.add(Row.header(t("set.header")));
        r.add(Row.of(t("set.accent"), I18n.get("chronicle.color." + Theme.ACCENT_KEYS[c.accent])));
        r.add(Row.of(t("set.bg"), I18n.get("chronicle.color." + Theme.BG_KEYS[c.bg])));
        r.add(Row.of(t("set.opacity"), Theme.OPACITY[c.opacity] + "%"));
        r.add(Row.of(t("set.dim"), I18n.get(c.dim ? "chronicle.set.on" : "chronicle.set.off")));
        r.add(Row.of(t("set.repl_adv"), I18n.get(c.replaceAdv ? "chronicle.set.on" : "chronicle.set.off")));
        r.add(Row.of(t("set.repl_stats"), I18n.get(c.replaceStats ? "chronicle.set.on" : "chronicle.set.off")));
        r.add(Row.of(t("set.adv_style"), I18n.get("chronicle.style." + Theme.STYLE_KEYS[c.advStyle])));
        r.add(Row.of(t("set.round"), I18n.get(c.round ? "chronicle.set.on" : "chronicle.set.off")));
        r.add(Row.spacer());
        r.add(Row.note(t("set.hint")));
        return List.of(new PageBuilder.Page(t("set.header"), r));
    }
}
