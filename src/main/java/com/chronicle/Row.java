package com.chronicle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Одна строка на странице. frac < 0 означает «без полосы». */
public record Row(Kind kind, Component label, String value, float frac, Card card) {

    public enum Kind { HEADER, ROW, NOTE, SPACER, CARD }

    /** Карточка достижения (иконка, название, описание, совет, статус). */
    public record Card(ItemStack icon, Component title, Component desc, Component tip,
                       String badge, int badgeColor, boolean done, float frac,
                       String id, int mode) {} // mode: 0 полная, 1 компактная, 2 компактная раскрытая

    public static Row header(Component t) {
        return new Row(Kind.HEADER, t, "", 0f, null);
    }

    public static Row of(Component label, String value) {
        return new Row(Kind.ROW, label, value, -1f, null);
    }

    public static Row bar(Component label, String value, float frac) {
        return new Row(Kind.ROW, label, value, frac, null);
    }

    public static Row note(Component t) {
        return new Row(Kind.NOTE, t, "", 0f, null);
    }

    public static Row spacer() {
        return new Row(Kind.SPACER, Component.empty(), "", 0f, null);
    }

    public static Row card(Card c) {
        return new Row(Kind.CARD, c.title(), "", 0f, c);
    }

    /** Высота обычных строк (у карточек считается на экране, там нужна ширина). */
    public int height() {
        return switch (kind) {
            case HEADER -> 24;
            case ROW -> 14;
            case NOTE -> 12;
            case SPACER -> 6;
            case CARD -> 0;
        };
    }
}
