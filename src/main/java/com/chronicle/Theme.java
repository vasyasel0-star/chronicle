package com.chronicle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/** Настройки оформления (config/chronicle-theme.json). */
public final class Theme {
    public static final class Cfg {
        public int accent = 0;
        public int bg = 0;
        public int opacity = 4;
        public boolean dim = true;
        public boolean replaceAdv = true;
        public boolean replaceStats = true;
        public int advStyle = 0;
    }

    public static final String[] STYLE_KEYS = {"cards", "checklist", "grid", "tree"};

    public static final int[] ACCENTS = {
        0xE6A23C, 0xE5484D, 0x46C37B, 0x4C9AFF, 0xA06BFF, 0x3CC8D8, 0xF06BB0, 0xE8EAED
    };
    public static final String[] ACCENT_KEYS = {
        "amber", "red", "green", "blue", "purple", "cyan", "pink", "white"
    };
    public static final int[] BGS = {0x111317, 0x000000, 0x0F1626, 0x0F1A14, 0x1E1015, 0x1E2024};
    public static final String[] BG_KEYS = {"dark", "black", "navy", "forest", "wine", "gray"};
    public static final int[] OPACITY = {40, 55, 70, 85, 94, 100};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Cfg cfg;

    private Theme() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("chronicle-theme.json");
    }

    public static synchronized Cfg cfg() {
        if (cfg == null) {
            cfg = new Cfg();
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Cfg c = GSON.fromJson(r, Cfg.class);
                    if (c != null) cfg = c;
                } catch (Exception ignored) {
                    // битый файл — остаются значения по умолчанию
                }
            }
            cfg.accent = Math.floorMod(cfg.accent, ACCENTS.length);
            cfg.bg = Math.floorMod(cfg.bg, BGS.length);
            cfg.opacity = Math.max(0, Math.min(OPACITY.length - 1, cfg.opacity));
            cfg.advStyle = Math.floorMod(cfg.advStyle, STYLE_KEYS.length);
        }
        return cfg;
    }

    /** Меняет настройку по номеру строки: 0 акцент, 1 фон, 2 прозрачность, 3 затемнение. */
    public static synchronized void cycle(int setting, int dir) {
        Cfg c = cfg();
        switch (setting) {
            case 0 -> c.accent = Math.floorMod(c.accent + dir, ACCENTS.length);
            case 1 -> c.bg = Math.floorMod(c.bg + dir, BGS.length);
            case 2 -> c.opacity = Math.floorMod(c.opacity + dir, OPACITY.length);
            case 3 -> c.dim = !c.dim;
            case 4 -> c.replaceAdv = !c.replaceAdv;
            case 5 -> c.replaceStats = !c.replaceStats;
            case 6 -> c.advStyle = Math.floorMod(c.advStyle + dir, STYLE_KEYS.length);
            default -> {
                return;
            }
        }
        try {
            Files.writeString(file(), GSON.toJson(c), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // не критично
        }
    }

    private static int mix(int rgb, int to, float t) {
        int r = Math.round(((rgb >> 16) & 255) + (((to >> 16) & 255) - ((rgb >> 16) & 255)) * t);
        int g = Math.round(((rgb >> 8) & 255) + (((to >> 8) & 255) - ((rgb >> 8) & 255)) * t);
        int b = Math.round((rgb & 255) + ((to & 255) - (rgb & 255)) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int alpha() {
        return Math.round(OPACITY[cfg().opacity] * 255 / 100f);
    }

    public static int accent() {
        return 0xFF000000 | ACCENTS[cfg().accent];
    }

    public static int accentFill() {
        return 0x40000000 | ACCENTS[cfg().accent];
    }

    public static int accentLine() {
        return 0x66000000 | ACCENTS[cfg().accent];
    }

    public static int border() {
        return 0xFF000000 | mix(ACCENTS[cfg().accent], 0x000000, 0.6f);
    }

    /** Цвет фона: f > 0 светлее, f < 0 темнее. */
    public static int shade(float f) {
        int base = BGS[cfg().bg];
        int rgb = f >= 0 ? mix(base, 0xFFFFFF, f) : mix(base, 0x000000, -f);
        return (alpha() << 24) | rgb;
    }
}
