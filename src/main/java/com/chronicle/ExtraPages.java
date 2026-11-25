package com.chronicle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

/** Страницы «Достижения» (карточки с иконками и советами) и «Настройки». */
public final class ExtraPages {
    private record Adv(ResourceLocation id, DisplayInfo info, boolean done, boolean avail, int depth) {}

    private static final List<String> ORDER = List.of(
            "minecraft:story", "minecraft:nether", "minecraft:end",
            "minecraft:adventure", "minecraft:husbandry");

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
        while (p != null && d < 40) {
            d++;
            p = parentOf.get(p);
        }
        return d;
    }

    private static Component nameOf(String key, Map<String, Component> names) {
        Component n = names.get(key);
        if (n != null) return n;
        String cat = key.substring(key.indexOf(':') + 1).replace('_', ' ');
        if (cat.isEmpty()) return Component.literal(key);
        return Component.literal(Character.toUpperCase(cat.charAt(0)) + cat.substring(1));
    }

    private static Row.Card card(Adv a) {
        String tipKey = "chronicle.tip." + a.id().getPath().replace('/', '.');
        Component tip = null;
        if (!a.done() && a.id().getNamespace().equals("minecraft") && I18n.exists(tipKey)) {
            tip = t("tip.label").append(Component.translatable(tipKey));
        }
        String badge;
        int color;
        if (a.done()) {
            badge = I18n.get("chronicle.adv.done");
            color = GREEN;
        } else if (a.avail()) {
            badge = I18n.get("chronicle.adv.available");
            color = Theme.accent();
        } else {
            badge = I18n.get("chronicle.adv.locked");
            color = GRAY;
        }
        return new Row.Card(a.info().getIcon(), a.info().getTitle(), a.info().getDescription(),
                tip, badge, color, a.done(), -1f);
    }

    /** filter: 0 все, 1 осталось, 2 выполнено. */
    public static List<PageBuilder.Page> advancements(Minecraft mc, int filter) {
        if (mc.player == null || mc.getConnection() == null) return List.of();
        ClientAdvancements ca = mc.getConnection().getAdvancements();

        Map<ResourceLocation, ResourceLocation> parentOf = new HashMap<>();
        Set<ResourceLocation> doneIds = new HashSet<>();
        for (Map.Entry<AdvancementHolder, AdvancementProgress> e : ca.progress.entrySet()) {
            ResourceLocation id = e.getKey().id();
            e.getKey().value().parent().ifPresent(p -> parentOf.put(id, p));
            if (e.getValue().isDone()) doneIds.add(id);
        }

        Map<String, List<Adv>> cats = new LinkedHashMap<>();
        Map<String, Component> names = new LinkedHashMap<>();
        Map<String, ItemStack> icons = new LinkedHashMap<>();
        for (Map.Entry<AdvancementHolder, AdvancementProgress> e : ca.progress.entrySet()) {
            DisplayInfo d = e.getKey().value().display().orElse(null);
            if (d == null) continue;
            ResourceLocation id = e.getKey().id();
            String path = id.getPath();
            int slash = path.indexOf('/');
            String key = id.getNamespace() + ":" + (slash > 0 ? path.substring(0, slash) : path);
            ResourceLocation parent = parentOf.get(id);
            if (parent == null) { // корень вкладки: берём из него имя и иконку категории
                names.put(key, d.getTitle());
                icons.put(key, d.getIcon());
                continue;
            }
            cats.computeIfAbsent(key, k -> new ArrayList<>()).add(
                    new Adv(id, d, e.getValue().isDone(), doneIds.contains(parent), depth(id, parentOf)));
        }

        List<String> keys = new ArrayList<>(cats.keySet());
        keys.sort(Comparator.comparingInt((String k) -> {
            int i = ORDER.indexOf(k);
            return i < 0 ? 100 : i;
        }).thenComparing(k -> k));

        // обзор: карточки категорий (по клику открывается категория)
        List<Row> ov = new ArrayList<>();
        ov.add(Row.header(t("adv.overview")));
        int allDone = 0;
        int allTotal = 0;
        for (String k : keys) {
            int done = 0;
            for (Adv a : cats.get(k)) if (a.done()) done++;
            int total = cats.get(k).size();
            allDone += done;
            allTotal += total;
            ItemStack icon = icons.getOrDefault(k, cats.get(k).get(0).info().getIcon());
            ov.add(Row.card(new Row.Card(icon, nameOf(k, names), null, null, pct(done, total),
                    done == total ? GREEN : Theme.accent(), done == total,
                    total == 0 ? 0f : done / (float) total)));
        }
        ov.add(Row.spacer());
        ov.add(Row.card(new Row.Card(new ItemStack(Items.NETHER_STAR), t("adv.total"), null, null,
                pct(allDone, allTotal), Theme.accent(), false,
                allTotal == 0 ? 0f : allDone / (float) allTotal)));
        ov.add(Row.note(t("adv.hint")));

        List<PageBuilder.Page> pages = new ArrayList<>();
        pages.add(new PageBuilder.Page(t("adv.overview"), ov, new ItemStack(Items.KNOWLEDGE_BOOK)));

        for (String k : keys) {
            List<Adv> todo = new ArrayList<>();
            List<Adv> done = new ArrayList<>();
            for (Adv a : cats.get(k)) (a.done() ? done : todo).add(a);
            todo.sort(Comparator.comparing((Adv a) -> !a.avail()).thenComparingInt(Adv::depth)
                    .thenComparing(a -> a.id().getPath()));
            done.sort(Comparator.comparingInt(Adv::depth).thenComparing(a -> a.id().getPath()));

            int total = todo.size() + done.size();
            Component name = nameOf(k, names);
            List<Row> r = new ArrayList<>();
            r.add(Row.header(name));
            r.add(Row.bar(t("adv.progress"), pct(done.size(), total),
                    total == 0 ? 0f : done.size() / (float) total));
            r.add(Row.spacer());
            if (filter != 2 && !todo.isEmpty()) {
                r.add(Row.header(t("adv.remaining").append(" (" + todo.size() + ")")));
                for (Adv a : todo) r.add(Row.card(card(a)));
            }
            if (filter != 1 && !done.isEmpty()) {
                r.add(Row.header(t("adv.completed").append(" (" + done.size() + ")")));
                for (Adv a : done) r.add(Row.card(card(a)));
            }
            if ((filter == 1 && todo.isEmpty()) || (filter == 2 && done.isEmpty())) {
                r.add(Row.note(t("none")));
            }
            pages.add(new PageBuilder.Page(name, r, icons.getOrDefault(k, cats.get(k).get(0).info().getIcon())));
        }
        return pages;
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
        r.add(Row.spacer());
        r.add(Row.note(t("set.hint")));
        return List.of(new PageBuilder.Page(t("set.header"), r));
    }
}
