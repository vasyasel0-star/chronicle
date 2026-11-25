package com.chronicle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Страницы «Достижения» (с советами) и «Настройки». */
public final class ExtraPages {
    private record Adv(ResourceLocation id, DisplayInfo info, boolean done) {}

    private static final List<String> ORDER = List.of(
            "minecraft:story", "minecraft:nether", "minecraft:end",
            "minecraft:adventure", "minecraft:husbandry");

    private ExtraPages() {}

    private static Component t(String key) {
        return Component.translatable("chronicle." + key);
    }

    private static String pct(int done, int total) {
        return done + " / " + total + "  (" + (total == 0 ? 0 : Math.round(done * 100f / total)) + "%)";
    }

    public static List<PageBuilder.Page> advancements(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return List.of();
        ClientAdvancements ca = mc.getConnection().getAdvancements();

        Map<String, List<Adv>> cats = new LinkedHashMap<>();
        Map<String, Component> names = new LinkedHashMap<>();
        for (Map.Entry<AdvancementHolder, AdvancementProgress> e : ca.progress.entrySet()) {
            DisplayInfo d = e.getKey().value().display().orElse(null);
            if (d == null) continue;
            ResourceLocation id = e.getKey().id();
            String path = id.getPath();
            int slash = path.indexOf('/');
            String cat = slash > 0 ? path.substring(0, slash) : path;
            String key = id.getNamespace() + ":" + cat;
            cats.computeIfAbsent(key, k -> new ArrayList<>()).add(new Adv(id, d, e.getValue().isDone()));
            if (path.equals(cat + "/root") || path.equals("root")) names.put(key, d.getTitle());
        }

        List<String> keys = new ArrayList<>(cats.keySet());
        keys.sort(Comparator.comparingInt((String k) -> {
            int i = ORDER.indexOf(k);
            return i < 0 ? 100 : i;
        }).thenComparing(k -> k));

        // обзор
        List<Row> ov = new ArrayList<>();
        int allDone = 0;
        int allTotal = 0;
        ov.add(Row.header(t("adv.overview")));
        for (String k : keys) {
            int done = 0;
            for (Adv a : cats.get(k)) if (a.done()) done++;
            int total = cats.get(k).size();
            allDone += done;
            allTotal += total;
            ov.add(Row.bar(names.getOrDefault(k, Component.literal(k)), pct(done, total),
                    total == 0 ? 0f : done / (float) total));
        }
        ov.add(Row.spacer());
        ov.add(Row.bar(t("adv.total"), pct(allDone, allTotal), allTotal == 0 ? 0f : allDone / (float) allTotal));
        ov.add(Row.note(t("adv.hint")));

        List<PageBuilder.Page> pages = new ArrayList<>();
        pages.add(new PageBuilder.Page(t("adv.overview"), ov));

        for (String k : keys) {
            List<Adv> list = new ArrayList<>(cats.get(k));
            list.sort(Comparator.comparing((Adv a) -> a.done()).thenComparing(a -> a.id().getPath()));
            int done = 0;
            for (Adv a : list) if (a.done()) done++;
            Component name = names.getOrDefault(k, Component.literal(k));

            List<Row> r = new ArrayList<>();
            r.add(Row.header(name));
            r.add(Row.bar(t("adv.progress"), pct(done, list.size()),
                    list.isEmpty() ? 0f : done / (float) list.size()));
            r.add(Row.spacer());
            for (Adv a : list) {
                if (a.done()) {
                    r.add(Row.of(a.info().getTitle().copy().withStyle(ChatFormatting.GREEN),
                            I18n.get("chronicle.adv.done")));
                } else {
                    r.add(Row.of(a.info().getTitle(), "—"));
                    String tipKey = "chronicle.tip." + a.id().getPath().replace('/', '.');
                    Component tip = a.id().getNamespace().equals("minecraft") && I18n.exists(tipKey)
                            ? Component.translatable(tipKey)
                            : a.info().getDescription();
                    r.add(Row.note(Component.literal("   › ").append(tip)));
                }
            }
            pages.add(new PageBuilder.Page(name, r));
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
        r.add(Row.spacer());
        r.add(Row.note(t("set.hint")));
        return List.of(new PageBuilder.Page(t("set.header"), r));
    }
}
