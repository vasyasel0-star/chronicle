package com.chronicle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;

/** Сбор и форматирование чисел из ванильной статистики. */
public final class Metrics {
    public static final String PLAY = "play";
    public static final String DEATHS = "deaths";
    public static final String MOB_KILLS = "mobKills";
    public static final String PLAYER_KILLS = "playerKills";
    public static final String DMG_DEALT = "dmgDealt";
    public static final String DMG_TAKEN = "dmgTaken";
    public static final String DIST = "distance";
    public static final String JUMPS = "jumps";
    public static final String MINED = "mined";
    public static final String CRAFTED = "crafted";
    public static final String FISH = "fish";
    public static final String BRED = "bred";
    public static final String TRADES = "trades";
    public static final String ADV_DONE = "advDone";
    public static final String ADV_TOTAL = "advTotal";

    /** Ключи, которые суммируются по всем мирам. */
    public static final List<String> SUMMED = List.of(
            PLAY, DEATHS, MOB_KILLS, PLAYER_KILLS, DMG_DEALT, DMG_TAKEN, DIST,
            JUMPS, MINED, CRAFTED, FISH, BRED, TRADES, ADV_DONE);

    private Metrics() {}

    // ---------- чтение статистики ----------

    /** Значение кастомной статистики по id (0, если такой статистики нет в этой версии). */
    public static long custom(StatsCounter s, String path) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace(path);
        if (!BuiltInRegistries.CUSTOM_STAT.containsKey(id)) return 0;
        return s.getValue(Stats.CUSTOM.get(id));
    }

    public static long playTicks(StatsCounter s) {
        return Math.max(custom(s, "play_time"), custom(s, "play_one_minute"));
    }

    /** Сумма всех статистик вида *_one_cm. */
    public static long distance(StatsCounter s) {
        long sum = 0;
        for (ResourceLocation id : BuiltInRegistries.CUSTOM_STAT.keySet()) {
            if (id.getPath().endsWith("_one_cm")) {
                sum += s.getValue(Stats.CUSTOM.get(id));
            }
        }
        return sum;
    }

    public static <T> long total(StatsCounter s, StatType<T> type, Registry<T> registry) {
        long sum = 0;
        for (T t : registry) {
            sum += s.getValue(type.get(t));
        }
        return sum;
    }

    /** {выполнено, всего} среди достижений, которые видны игроку. */
    public static int[] advancements(ClientAdvancements adv) {
        int done = 0;
        int total = 0;
        if (adv == null) return new int[] {0, 0};
        for (Map.Entry<AdvancementHolder, AdvancementProgress> e : adv.progress.entrySet()) {
            if (e.getKey().value().display().isEmpty()) continue; // рецепты и т.п.
            total++;
            if (e.getValue().isDone()) done++;
        }
        return new int[] {done, total};
    }

    /** Компактная сводка мира для сохранения в файл. */
    public static Map<String, Long> collect(StatsCounter s, ClientAdvancements adv) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put(PLAY, playTicks(s));
        m.put(DEATHS, custom(s, "deaths"));
        m.put(MOB_KILLS, custom(s, "mob_kills"));
        m.put(PLAYER_KILLS, custom(s, "player_kills"));
        m.put(DMG_DEALT, custom(s, "damage_dealt"));
        m.put(DMG_TAKEN, custom(s, "damage_taken"));
        m.put(DIST, distance(s));
        m.put(JUMPS, custom(s, "jump"));
        m.put(MINED, total(s, Stats.BLOCK_MINED, BuiltInRegistries.BLOCK));
        m.put(CRAFTED, total(s, Stats.ITEM_CRAFTED, BuiltInRegistries.ITEM));
        m.put(FISH, custom(s, "fish_caught"));
        m.put(BRED, custom(s, "animals_bred"));
        m.put(TRADES, custom(s, "traded_with_villager"));
        int[] a = advancements(adv);
        m.put(ADV_DONE, (long) a[0]);
        m.put(ADV_TOTAL, (long) a[1]);
        return m;
    }

    // ---------- форматирование ----------

    public static String num(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    public static String time(long ticks) {
        long sec = ticks / 20;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return num(h) + " " + I18n.get("chronicle.unit.h") + " " + m + " " + I18n.get("chronicle.unit.min");
        }
        if (m > 0) {
            return m + " " + I18n.get("chronicle.unit.min") + " " + s + " " + I18n.get("chronicle.unit.s");
        }
        return s + " " + I18n.get("chronicle.unit.s");
    }

    public static String dist(long cm) {
        if (cm >= 100_000) {
            return String.format(Locale.ROOT, "%.1f %s", cm / 100_000.0, I18n.get("chronicle.unit.km"));
        }
        return (cm / 100) + " " + I18n.get("chronicle.unit.m");
    }

    /** Урон в ванили хранится в десятых долях HP. */
    public static String hp(long tenths) {
        return num(tenths / 10) + " " + I18n.get("chronicle.unit.hp");
    }
}
