package com.chronicle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/** Хранит сводки по всем мирам в config/chronicle-worlds.json. */
public final class WorldStore {
    public static final class Rec {
        public String name = "";
        public boolean mp;
        public long last;
        public Map<String, Long> m = new LinkedHashMap<>();
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, Rec>>() {}.getType();
    private static Map<String, Rec> data;

    private WorldStore() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("chronicle-worlds.json");
    }

    public static synchronized Map<String, Rec> all() {
        if (data == null) load();
        return data;
    }

    private static void load() {
        data = new LinkedHashMap<>();
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Map<String, Rec> loaded = GSON.fromJson(r, TYPE);
            if (loaded != null) data = loaded;
        } catch (Exception e) {
            LOGGER.error("Chronicle: cannot read {}, keeping a backup", f, e);
            try {
                Files.move(f, f.resolveSibling("chronicle-worlds.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // ничего не поделать
            }
        }
    }

    public static synchronized void save(String key, String name, boolean mp, Map<String, Long> m) {
        long play = m.getOrDefault(Metrics.PLAY, 0L);
        if (play <= 0) return; // статистика ещё не пришла с сервера
        Rec old = all().get(key);
        if (old != null && old.m.getOrDefault(Metrics.PLAY, 0L) > play) return; // пришли неполные данные

        Rec r = new Rec();
        r.name = name;
        r.mp = mp;
        r.last = System.currentTimeMillis();
        r.m = new LinkedHashMap<>(m);
        data.put(key, r);
        write();
    }

    private static void write() {
        Path f = file();
        Path tmp = f.resolveSibling("chronicle-worlds.json.tmp");
        try {
            Files.writeString(tmp, GSON.toJson(data, TYPE), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Chronicle: cannot write {}", f, e);
        }
    }
}
