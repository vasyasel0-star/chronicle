package com.chronicle;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/** Идентификатор текущего мира: одиночный (по папке сохранения) или сервер (по адресу). */
public record WorldId(String key, String name, boolean mp) {

    public static WorldId current(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv != null) {
            Path p = srv.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            String folder = p.getFileName() == null ? "world" : p.getFileName().toString();
            return new WorldId("sp:" + folder, srv.getWorldData().getLevelName(), false);
        }
        ServerData sd = mc.getCurrentServer();
        if (sd != null) {
            return new WorldId("mp:" + sd.ip, sd.name, true);
        }
        return new WorldId("mp:unknown", "?", true);
    }
}
