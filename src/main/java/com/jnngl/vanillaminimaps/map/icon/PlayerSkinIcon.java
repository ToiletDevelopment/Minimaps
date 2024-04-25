package com.jnngl.vanillaminimaps.map.icon;

import com.jnngl.vanillaminimaps.VanillaMinimaps;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSkinIcon {

    private static final Map<UUID, BufferedImage> playerImages = new ConcurrentHashMap<>();
    private static final Set<UUID> loading = new HashSet<>();

    public static BufferedImage getPlayerImageFor(Player player) {
        UUID uuid = player.getUniqueId();
        if(!playerImages.containsKey(uuid)) {
            reloadImage(player);
        }
        return playerImages.get(uuid);
    }

    public static void reloadImage(Player player) {
        UUID uuid = player.getUniqueId();
        if (loading.contains(uuid))
            return;
        loading.add(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(VanillaMinimaps.get(), () -> {
            load(uuid, player);
            loading.remove(uuid);
        });
    }

    private static void load(UUID uuid, Player player) {
        String url = String.format("https://minotar.net/helm/%s/%d.png", player.getName(), 8);
        try {
            BufferedImage image = ImageIO.read(new URL(url));
            playerImages.put(uuid, image);
        } catch (IOException iOException)  {
            throw new RuntimeException(iOException);
        }
    }

}
