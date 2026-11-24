package com.chronicle;

import net.minecraft.network.chat.Component;

/** Одна строка на странице статистики. frac < 0 означает «без полосы». */
public record Row(Kind kind, Component label, String value, float frac) {

    public enum Kind { HEADER, ROW, NOTE, SPACER }

    public static Row header(Component t) {
        return new Row(Kind.HEADER, t, "", 0f);
    }

    public static Row of(Component label, String value) {
        return new Row(Kind.ROW, label, value, -1f);
    }

    public static Row bar(Component label, String value, float frac) {
        return new Row(Kind.ROW, label, value, frac);
    }

    public static Row note(Component t) {
        return new Row(Kind.NOTE, t, "", 0f);
    }

    public static Row spacer() {
        return new Row(Kind.SPACER, Component.empty(), "", 0f);
    }

    public int height() {
        return switch (kind) {
            case HEADER -> 24;
            case ROW -> 14;
            case NOTE -> 12;
            case SPACER -> 6;
        };
    }
}
