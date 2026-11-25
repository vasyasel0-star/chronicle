package com.chronicle;

import static com.chronicle.Metrics.custom;
import static com.chronicle.Metrics.dist;
import static com.chronicle.Metrics.hp;
import static com.chronicle.Metrics.num;
import static com.chronicle.Metrics.playTicks;
import static com.chronicle.Metrics.time;
import static com.chronicle.Metrics.total;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/** Собирает страницы статистики для обеих вкладок. */
public final class PageBuilder {
    public record Page(Component title, List<Row> rows, ItemStack icon, List<AdvItem> items) {
        public Page(Component title, List<Row> rows) {
            this(title, rows, ItemStack.EMPTY, null);
        }

        public Page(Component title, List<Row> rows, ItemStack icon) {
            this(title, rows, icon, null);
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private static final String[] MODES = {
        "walk", "sprint", "crouch", "swim", "walk_on_water", "walk_under_water", "climb",
        "fall", "fly", "aviate", "boat", "minecart", "horse", "pig", "strider"
    };

    private PageBuilder() {}

    private static Component t(String key) {
        return Component.translatable("chronicle." + key);
    }

    private static Component stat(String id) {
        return Component.translatable("chronicle.stat." + id);
    }

    // =====================================================================
    //  Вкладка «Этот мир»
    // =====================================================================

    public static List<Page> world(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return List.of();
        StatsCounter s = mc.player.getStats();
        ClientAdvancements adv = mc.getConnection().getAdvancements();

        List<Page> pages = new ArrayList<>();
        pages.add(general(s));
        pages.add(combat(s));
        pages.add(mining(s));
        pages.add(travel(s));
        pages.add(items(s));
        pages.add(advancements(adv));
        return pages;
    }

    private static Page general(StatsCounter s) {
        List<Row> r = new ArrayList<>();
        r.add(Row.header(t("page.general")));
        r.add(Row.of(stat("play"), time(playTicks(s))));
        r.add(Row.of(stat("since_death"), time(custom(s, "time_since_death"))));
        r.add(Row.of(stat("world_time"), time(custom(s, "total_world_time"))));
        r.add(Row.of(stat("deaths"), num(custom(s, "deaths"))));
        r.add(Row.of(stat("jumps"), num(custom(s, "jump"))));
        r.add(Row.of(stat("sleeps"), num(custom(s, "sleep_in_bed"))));
        r.add(Row.of(stat("fish"), num(custom(s, "fish_caught"))));
        r.add(Row.of(stat("bred"), num(custom(s, "animals_bred"))));
        r.add(Row.of(stat("trades"), num(custom(s, "traded_with_villager"))));
        r.add(Row.of(stat("talks"), num(custom(s, "talked_to_villager"))));
        r.add(Row.of(stat("raids"), num(custom(s, "raid_win"))));
        r.add(Row.of(stat("enchanted"), num(custom(s, "enchant_item"))));
        r.add(Row.of(stat("chests"), num(custom(s, "open_chest"))));
        r.add(Row.of(stat("targets"), num(custom(s, "target_hit"))));
        return new Page(t("page.general"), r);
    }

    private static Page combat(StatsCounter s) {
        List<Row> r = new ArrayList<>();
        r.add(Row.header(t("page.combat")));
        r.add(Row.of(stat("mob_kills"), num(custom(s, "mob_kills"))));
        r.add(Row.of(stat("player_kills"), num(custom(s, "player_kills"))));
        r.add(Row.of(stat("deaths"), num(custom(s, "deaths"))));
        r.add(Row.of(stat("dmg_dealt"), hp(custom(s, "damage_dealt"))));
        r.add(Row.of(stat("dmg_taken"), hp(custom(s, "damage_taken"))));
        r.add(Row.of(stat("dmg_blocked"), hp(custom(s, "damage_blocked_by_shield"))));
        r.add(Row.spacer());
        r.add(Row.header(t("sec.top_killed")));
        top(r, s, Stats.ENTITY_KILLED, BuiltInRegistries.ENTITY_TYPE, EntityType::getDescription, 8);
        r.add(Row.spacer());
        r.add(Row.header(t("sec.killed_by")));
        top(r, s, Stats.ENTITY_KILLED_BY, BuiltInRegistries.ENTITY_TYPE, EntityType::getDescription, 5);
        return new Page(t("page.combat"), r);
    }

    private static Page mining(StatsCounter s) {
        List<Row> r = new ArrayList<>();
        r.add(Row.header(t("page.mining")));
        r.add(Row.of(stat("mined"), num(total(s, Stats.BLOCK_MINED, BuiltInRegistries.BLOCK))));
        r.add(Row.spacer());
        r.add(Row.header(t("sec.top_blocks")));
        top(r, s, Stats.BLOCK_MINED, BuiltInRegistries.BLOCK, b -> b.getName(), 12);
        return new Page(t("page.mining"), r);
    }

    private static Page travel(StatsCounter s) {
        List<Row> r = new ArrayList<>();
        r.add(Row.header(t("page.travel")));
        r.add(Row.of(stat("distance"), dist(Metrics.distance(s))));
        r.add(Row.of(stat("jumps"), num(custom(s, "jump"))));
        r.add(Row.spacer());
        r.add(Row.header(t("sec.distance")));

        List<Map.Entry<String, Long>> list = new ArrayList<>();
        for (String mode : MODES) {
            long v = custom(s, mode + "_one_cm");
            if (v > 0) list.add(Map.entry(mode, v));
        }
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        long max = list.isEmpty() ? 1 : list.get(0).getValue();
        for (Map.Entry<String, Long> e : list) {
            r.add(Row.bar(Component.translatable("chronicle.travel." + e.getKey()),
                    dist(e.getValue()), e.getValue() / (float) max));
        }
        if (list.isEmpty()) r.add(Row.of(t("none"), "—"));
        return new Page(t("page.travel"), r);
    }

    private static Page items(StatsCounter s) {
        List<Row> r = new ArrayList<>();
        r.add(Row.header(t("page.items")));
        r.add(Row.of(stat("crafted"), num(total(s, Stats.ITEM_CRAFTED, BuiltInRegistries.ITEM))));
        r.add(Row.of(stat("used"), num(total(s, Stats.ITEM_USED, BuiltInRegistries.ITEM))));
        r.add(Row.of(stat("broken"), num(total(s, Stats.ITEM_BROKEN, BuiltInRegistries.ITEM))));
        r.add(Row.of(stat("picked"), num(total(s, Stats.ITEM_PICKED_UP, BuiltInRegistries.ITEM))));
        r.add(Row.of(stat("dropped"), num(total(s, Stats.ITEM_DROPPED, BuiltInRegistries.ITEM))));
        r.add(Row.spacer());
        r.add(Row.header(t("sec.crafted")));
        top(r, s, Stats.ITEM_CRAFTED, BuiltInRegistries.ITEM, i -> i.getDescription(), 8);
        r.add(Row.spacer());
        r.add(Row.header(t("sec.used")));
        top(r, s, Stats.ITEM_USED, BuiltInRegistries.ITEM, i -> i.getDescription(), 8);
        r.add(Row.spacer());
        r.add(Row.header(t("sec.broken")));
        top(r, s, Stats.ITEM_BROKEN, BuiltInRegistries.ITEM, i -> i.getDescription(), 5);
        return new Page(t("page.items"), r);
    }

    private static Page advancements(ClientAdvancements adv) {
        List<Row> r = new ArrayList<>();
        int[] a = Metrics.advancements(adv);
        float f = a[1] == 0 ? 0f : a[0] / (float) a[1];
        r.add(Row.header(t("page.advancements")));
        r.add(Row.bar(stat("adv_done"), a[0] + " / " + a[1] + "  (" + Math.round(f * 100) + "%)", f));
        r.add(Row.note(t("adv_note")));
        return new Page(t("page.advancements"), r);
    }

    private static <T> void top(List<Row> out, StatsCounter s, StatType<T> type, Registry<T> reg,
                                Function<T, Component> name, int n) {
        List<Map.Entry<T, Integer>> list = new ArrayList<>();
        for (T e : reg) {
            int v = s.getValue(type.get(e));
            if (v > 0) list.add(Map.entry(e, v));
        }
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        if (list.isEmpty()) {
            out.add(Row.of(t("none"), "—"));
            return;
        }
        int max = list.get(0).getValue();
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            Map.Entry<T, Integer> e = list.get(i);
            out.add(Row.bar(name.apply(e.getKey()), num(e.getValue()), e.getValue() / (float) max));
        }
    }

    // =====================================================================
    //  Вкладка «Все миры»
    // =====================================================================

    public static List<Page> all(Minecraft mc) {
        Map<String, WorldStore.Rec> recs = new LinkedHashMap<>(WorldStore.all());

        // подмешиваем живые данные текущего мира
        WorldId cur = ClientEvents.world;
        if (cur != null && mc.player != null && mc.getConnection() != null) {
            Map<String, Long> live = Metrics.collect(mc.player.getStats(), mc.getConnection().getAdvancements());
            WorldStore.Rec old = recs.get(cur.key());
            long livePlay = live.getOrDefault(Metrics.PLAY, 0L);
            if (livePlay > 0 && (old == null || old.m.getOrDefault(Metrics.PLAY, 0L) <= livePlay)) {
                WorldStore.Rec r = new WorldStore.Rec();
                r.name = cur.name();
                r.mp = cur.mp();
                r.last = System.currentTimeMillis();
                r.m = live;
                recs.put(cur.key(), r);
            }
        }

        List<Page> pages = new ArrayList<>();
        pages.add(totals(recs.values()));
        pages.add(worlds(recs.values()));
        pages.add(records(recs.values()));
        return pages;
    }

    private static long get(WorldStore.Rec r, String key) {
        return r.m.getOrDefault(key, 0L);
    }

    private static Page totals(Collection<WorldStore.Rec> recs) {
        Map<String, Long> sum = new LinkedHashMap<>();
        for (String k : Metrics.SUMMED) {
            long v = 0;
            for (WorldStore.Rec r : recs) v += get(r, k);
            sum.put(k, v);
        }
        List<Row> r = new ArrayList<>();
        r.add(Row.header(t("page.totals")));
        r.add(Row.of(stat("worlds"), num(recs.size())));
        r.add(Row.of(stat("play"), time(sum.get(Metrics.PLAY))));
        r.add(Row.of(stat("deaths"), num(sum.get(Metrics.DEATHS))));
        r.add(Row.of(stat("mob_kills"), num(sum.get(Metrics.MOB_KILLS))));
        r.add(Row.of(stat("player_kills"), num(sum.get(Metrics.PLAYER_KILLS))));
        r.add(Row.of(stat("dmg_dealt"), hp(sum.get(Metrics.DMG_DEALT))));
        r.add(Row.of(stat("dmg_taken"), hp(sum.get(Metrics.DMG_TAKEN))));
        r.add(Row.of(stat("distance"), dist(sum.get(Metrics.DIST))));
        r.add(Row.of(stat("jumps"), num(sum.get(Metrics.JUMPS))));
        r.add(Row.of(stat("mined"), num(sum.get(Metrics.MINED))));
        r.add(Row.of(stat("crafted"), num(sum.get(Metrics.CRAFTED))));
        r.add(Row.of(stat("fish"), num(sum.get(Metrics.FISH))));
        r.add(Row.of(stat("bred"), num(sum.get(Metrics.BRED))));
        r.add(Row.of(stat("trades"), num(sum.get(Metrics.TRADES))));
        r.add(Row.of(stat("adv_done"), num(sum.get(Metrics.ADV_DONE))));
        if (recs.isEmpty()) r.add(Row.note(t("none")));
        return new Page(t("page.totals"), r);
    }

    private static Page worlds(Collection<WorldStore.Rec> recs) {
        List<WorldStore.Rec> list = new ArrayList<>(recs);
        list.sort((a, b) -> Long.compare(b.last, a.last));
        long maxPlay = 1;
        for (WorldStore.Rec w : list) maxPlay = Math.max(maxPlay, get(w, Metrics.PLAY));

        List<Row> r = new ArrayList<>();
        for (WorldStore.Rec w : list) {
            String kind = I18n.get(w.mp ? "chronicle.world.mp" : "chronicle.world.sp");
            r.add(Row.header(Component.literal(w.name + "  ·  " + kind)));
            r.add(Row.bar(stat("play"), time(get(w, Metrics.PLAY)), get(w, Metrics.PLAY) / (float) maxPlay));
            r.add(Row.of(stat("deaths"), num(get(w, Metrics.DEATHS))));
            r.add(Row.of(stat("mob_kills"), num(get(w, Metrics.MOB_KILLS))));
            r.add(Row.of(stat("mined"), num(get(w, Metrics.MINED))));
            r.add(Row.of(stat("adv_done"), get(w, Metrics.ADV_DONE) + " / " + get(w, Metrics.ADV_TOTAL)));
            r.add(Row.of(t("last"), DATE.format(Instant.ofEpochMilli(w.last).atZone(ZoneId.systemDefault()))));
            r.add(Row.spacer());
        }
        if (list.isEmpty()) {
            r.add(Row.header(t("page.worlds")));
            r.add(Row.note(t("none")));
        }
        return new Page(t("page.worlds"), r);
    }

    private static Page records(Collection<WorldStore.Rec> recs) {
        List<Row> r = new ArrayList<>();
        r.add(Row.header(t("page.records")));
        r.add(record("rec_play", recs, Metrics.PLAY, Metrics::time));
        r.add(record("rec_deaths", recs, Metrics.DEATHS, Metrics::num));
        r.add(record("rec_kills", recs, Metrics.MOB_KILLS, Metrics::num));
        r.add(record("rec_mined", recs, Metrics.MINED, Metrics::num));
        r.add(record("rec_dist", recs, Metrics.DIST, Metrics::dist));
        return new Page(t("page.records"), r);
    }

    private static Row record(String labelKey, Collection<WorldStore.Rec> recs, String key,
                              Function<Long, String> fmt) {
        WorldStore.Rec best = null;
        for (WorldStore.Rec w : recs) {
            if (best == null || get(w, key) > get(best, key)) best = w;
        }
        if (best == null || get(best, key) <= 0) return Row.of(stat(labelKey), "—");
        return Row.of(stat(labelKey), best.name + " — " + fmt.apply(get(best, key)));
    }
}
