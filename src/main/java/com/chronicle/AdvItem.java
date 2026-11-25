package com.chronicle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Одно достижение для экрана (x, y — позиция в дереве как в ванильном окне). */
public record AdvItem(String id, String parent, ItemStack icon, Component title, Component desc,
                      Component tip, boolean done, boolean avail, boolean root,
                      float x, float y, int depth) {}
