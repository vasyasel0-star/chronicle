package com.chronicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Поиск по статистике (в том числе модовым блокам, предметам и мобам) и по сохранённым мирам. */
public final class SearchPages {
    private static final int LIMIT = 150;

    private SearchPages() {}

    private static boolean matches(Component name, ResourceLocation id, String needle) {
        return name.getString().toLowerCase(Locale.ROOT).contains(needle)
                || (id != null && id.toString().contains(needle));
    }

    private static PageBuilder.Page page(List<Row> body) {
        List<Row> rows = new ArrayList<>();
        int found = 0;
        for (Row r : body) if (r.kind() == Row.Kind.ROW) found++;
        rows.add(Row.header(Component.translatable("chronicle.search.results").append(" (" + found + ")")));
        if (body.isEmpty()) rows.add(Row.note(Component.translatable("chronicle.search.none")));
        rows.addAll(body);
        return new PageBuilder.Page(Component.translatable("chronicle.search.results"), rows,
                new ItemStack(Items.COMPASS));
    }

    private static <T> void scan(List<Row> out, StatsCounter s, StatType<T> type, Registry<T> reg,
                                 Function<T, Component> nameOf, String suffixKey, String needle) {
        for (T t : reg) {
            if (out.size() >= LIMIT) return;
            Component name = nameOf.apply(t);
            if (!matches(name, reg.getKey(t), needle)) continue;
            int v = s.getValue(type.get(t));
            if (v <= 0) continue;
            out.add(Row.of(Component.empty().append(name).append(" · ").append(Component.translatable(suffixKey)),
                    Metrics.num(v)));
        }
    }

    /** Поиск по статистике текущего мира. */
    public static PageBuilder.Page stats(Minecraft mc, String query) {
        String n = query.toLowerCase(Locale.ROOT).trim();
        List<Row> out = new ArrayList<>();
        if (mc.player != null) {
            StatsCounter s = mc.player.getStats();
            for (ResourceLocation id : BuiltInRegistries.CUSTOM_STAT) {
                if (out.size() >= LIMIT) break;
                Component name = Component.translatable("stat." + id.toLanguageKey());
                if (!matches(name, id, n)) continue;
                Stat<ResourceLocation> st = Stats.CUSTOM.get(id);
                int v = s.getValue(st);
                if (v > 0) out.add(Row.of(name, st.format(v)));
            }
            scan(out, s, Stats.BLOCK_MINED, BuiltInRegistries.BLOCK, Block::getName, "chronicle.srch.mined", n);
            scan(out, s, Stats.ITEM_CRAFTED, BuiltInRegistries.ITEM, i -> i.getDescription(),
                    "chronicle.srch.crafted", n);
            scan(out, s, Stats.ITEM_USED, BuiltInRegistries.ITEM, i -> i.getDescription(),
                    "chronicle.srch.used", n);
            scan(out, s, Stats.ITEM_BROKEN, BuiltInRegistries.ITEM, i -> i.getDescription(),
                    "chronicle.srch.broken", n);
            scan(out, s, Stats.ITEM_PICKED_UP, BuiltInRegistries.ITEM, i -> i.getDescription(),
                    "chronicle.srch.picked", n);
            scan(out, s, Stats.ITEM_DROPPED, BuiltInRegistries.ITEM, i -> i.getDescription(),
                    "chronicle.srch.dropped", n);
            scan(out, s, Stats.ENTITY_KILLED, BuiltInRegistries.ENTITY_TYPE, EntityType::getDescription,
                    "chronicle.srch.killed", n);
            scan(out, s, Stats.ENTITY_KILLED_BY, BuiltInRegistries.ENTITY_TYPE, EntityType::getDescription,
                    "chronicle.srch.killed_by", n);
        }
        return page(out);
    }

    private static long get(WorldStore.Rec w, String key) {
        return w.m.getOrDefault(key, 0L);
    }

    /** Поиск по названиям сохранённых миров (вкладка «Все миры»). */
    public static PageBuilder.Page worlds(String query) {
        String n = query.toLowerCase(Locale.ROOT).trim();
        List<Row> out = new ArrayList<>();
        for (WorldStore.Rec w : WorldStore.all().values()) {
            if (!w.name.toLowerCase(Locale.ROOT).contains(n)) continue;
            out.add(Row.header(Component.literal(w.name)));
            out.add(Row.of(Component.translatable("chronicle.stat.play"), Metrics.time(get(w, Metrics.PLAY))));
            out.add(Row.of(Component.translatable("chronicle.stat.deaths"), Metrics.num(get(w, Metrics.DEATHS))));
            out.add(Row.of(Component.translatable("chronicle.stat.mob_kills"),
                    Metrics.num(get(w, Metrics.MOB_KILLS))));
            out.add(Row.of(Component.translatable("chronicle.stat.mined"), Metrics.num(get(w, Metrics.MINED))));
            out.add(Row.of(Component.translatable("chronicle.stat.adv_done"),
                    get(w, Metrics.ADV_DONE) + " / " + get(w, Metrics.ADV_TOTAL)));
            out.add(Row.spacer());
        }
        return page(out);
    }
}
