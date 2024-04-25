/*
 *  Copyright (C) 2024  JNNGL
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jnngl.vanillaminimaps;

import com.jnngl.vanillaminimaps.clientside.ClientsideMinimapFactory;
import com.jnngl.vanillaminimaps.clientside.MinimapPacketSender;
import com.jnngl.vanillaminimaps.clientside.SteerableViewFactory;
import com.jnngl.vanillaminimaps.clientside.impl.NMSClientsideMinimapFactory;
import com.jnngl.vanillaminimaps.clientside.impl.NMSMinimapPacketSender;
import com.jnngl.vanillaminimaps.clientside.impl.NMSSteerableViewFactory;
import com.jnngl.vanillaminimaps.command.MinimapCommand;
import com.jnngl.vanillaminimaps.command.NMSCommandDispatcherAccessor;
import com.jnngl.vanillaminimaps.config.BlockConfig;
import com.jnngl.vanillaminimaps.config.Config;
import com.jnngl.vanillaminimaps.injection.PassengerRewriter;
import com.jnngl.vanillaminimaps.listener.MinimapBlockListener;
import com.jnngl.vanillaminimaps.listener.MinimapListener;
import com.jnngl.vanillaminimaps.map.Minimap;
import com.jnngl.vanillaminimaps.map.MinimapLayer;
import com.jnngl.vanillaminimaps.map.MinimapProvider;
import com.jnngl.vanillaminimaps.map.SecondaryMinimapLayer;
import com.jnngl.vanillaminimaps.map.fullscreen.FullscreenMinimap;
import com.jnngl.vanillaminimaps.map.icon.MinimapIcon;
import com.jnngl.vanillaminimaps.map.icon.PlayerSkinIcon;
import com.jnngl.vanillaminimaps.map.icon.provider.BuiltinMinimapIconProvider;
import com.jnngl.vanillaminimaps.map.icon.provider.MinimapIconProvider;
import com.jnngl.vanillaminimaps.map.marker.MarkerMinimapLayer;
import com.jnngl.vanillaminimaps.map.renderer.MinimapIconRenderer;
import com.jnngl.vanillaminimaps.map.renderer.world.WorldMinimapRenderer;
import com.jnngl.vanillaminimaps.map.renderer.world.cache.CacheableWorldMinimapRenderer;
import com.jnngl.vanillaminimaps.map.renderer.world.provider.BuiltinMinimapWorldRendererProvider;
import com.jnngl.vanillaminimaps.map.renderer.world.provider.MinimapWorldRendererProvider;
import com.jnngl.vanillaminimaps.storage.MinimapPlayerDatabase;
import com.maximde.minimapapi.MinimapAPI;
import com.maximde.minimapapi.MinimapActions;
import com.maximde.minimapapi.MinimapScreenPosition;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public final class VanillaMinimaps extends JavaPlugin implements MinimapProvider, Listener {

  private static final AtomicReference<VanillaMinimaps> PLUGIN = new AtomicReference<>();

  public static VanillaMinimaps get() {
    return PLUGIN.get();
  }

  @Getter
  private final Map<Player, PassengerRewriter> passengerRewriters = new HashMap<>();

  @MonotonicNonNull
  private ClientsideMinimapFactory defaultClientsideMinimapFactory;

  @MonotonicNonNull
  private MinimapPacketSender defaultMinimapPacketSender;

  @MonotonicNonNull
  private WorldMinimapRenderer defaultWorldRenderer;

  @MonotonicNonNull
  private MinimapIconProvider minimapIconProvider;

  @MonotonicNonNull
  private MinimapWorldRendererProvider worldRendererProvider;

  @MonotonicNonNull
  private MinimapBlockListener minimapBlockListener;

  @MonotonicNonNull
  private MinimapListener minimapListener;

  @MonotonicNonNull
  private SteerableViewFactory steerableViewFactory;

  @MonotonicNonNull
  private MinimapPlayerDatabase playerDataStorage;

  private HashMap<Player, List<LivingEntity>> targetList = new HashMap<>();

  @Override
  @SneakyThrows
  public void onEnable() {
    System.setProperty("com.j256.simplelogging.level", "ERROR");
    PLUGIN.set(this);

    Path dataPath = getDataFolder().toPath();
    Config.instance().reload(dataPath.resolve("config.yml"));
    BlockConfig.instance().reload(dataPath.resolve("blocks.yml"));


    Path iconsPath = dataPath.resolve("icons");
    Files.createDirectories(iconsPath);

    playerDataStorage = new MinimapPlayerDatabase(dataPath.resolve("players.db"));

    defaultClientsideMinimapFactory = new NMSClientsideMinimapFactory();
    defaultMinimapPacketSender = new NMSMinimapPacketSender(this);
    minimapIconProvider = new BuiltinMinimapIconProvider(iconsPath);
    worldRendererProvider = new BuiltinMinimapWorldRendererProvider();
    minimapBlockListener = new MinimapBlockListener(this);
    minimapListener = new MinimapListener(this);
    steerableViewFactory = new NMSSteerableViewFactory();

    defaultWorldRenderer = worldRendererProvider.create(Config.instance().defaultMinimapRenderer);
    if (defaultWorldRenderer == null) {
      throw new IllegalArgumentException("default-world-renderer");
    }

    if (defaultWorldRenderer instanceof CacheableWorldMinimapRenderer cacheable) {
      minimapBlockListener.registerCache(cacheable.getWorldMapCache());
    }

    Bukkit.getPluginManager().registerEvents(this, this);
    Bukkit.getPluginManager().registerEvents(minimapListener, this);
    minimapBlockListener.registerListener(this);

    new MinimapCommand(this).register(NMSCommandDispatcherAccessor.vanillaDispatcher());

    MinimapAPI.setAPI(new MinimapActions() {
      @Override
      public void enableMinimap(Player player) {
        try {
          playerDataStorage().enableMinimap(player);
          playerDataStorage().restore(get(), player);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      @Override
      public void disableMinimap(Player player) {
        minimapListener().disableMinimap(player);

        try {
          playerDataStorage().disableMinimap(player);
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }

      @Override
      public boolean isMinimap(Player player) {
        return false;
      }

      @Override
      public void setPosition(Player player, MinimapScreenPosition minimapScreenPosition) {
        Minimap minimap = getPlayerMinimap(player);
        if (minimap == null) {
          getLogger().log(Level.WARNING, "Minimap is disabled. Tried to change position for player " + player.getName());
          return;
        }

        minimap.screenPosition(minimapScreenPosition);
        minimap.update(get());
        save(minimap);
      }

      @Override
      public void addMarker(Player player, String markerName, String iconName, Location location) {
        Minimap minimap = getPlayerMinimap(player);
        if (minimap == null) {
          getLogger().log(Level.WARNING, "Minimap is disabled. Tried to add marker for player " + player.getName());
          return;
        }

        MinimapIcon icon = minimapIcon(iconName);


        if (minimap.secondaryLayers().containsKey(markerName)) {
          getLogger().log(Level.WARNING, "Marker with this name already exists.");
          return;
        }

        int markers = (int) minimap.secondaryLayers().entrySet().stream()
                .filter(entry -> !"player".equals(entry.getKey())
                        && !"death_point".equals(entry.getKey())
                        && entry.getValue() instanceof MarkerMinimapLayer)
                .count();


        float depth = 0.05F + minimap.secondaryLayers().size() * 0.01F;
        MinimapLayer iconBaseLayer = clientsideMinimapFactory().createMinimapLayer(player.getWorld(), null);
        SecondaryMinimapLayer iconLayer = new MarkerMinimapLayer(iconBaseLayer, new MinimapIconRenderer(icon), true,
                Config.instance().markers.customMarkers.stickToBorder, player.getWorld(), (int) location.getX(), (int) location.getZ(), depth);
        minimap.secondaryLayers().put(markerName, iconLayer);

        packetSender().spawnLayer(player, iconBaseLayer);
        minimap.update(get());
        save(minimap);
      }

      @Override
      public void followEntity(Player player, LivingEntity target) {
        Minimap minimap = getPlayerMinimap(player);

        if (minimap == null) {
          getLogger().log(Level.WARNING, "Minimap is disabled. Tried to add marker for player " + player.getName());
          return;
        }

        if(!(target instanceof Player targetPlayer)) return;
        MinimapIcon icon = MinimapIcon.fromBufferedImage("follow" + target.getEntityId(), PlayerSkinIcon.getPlayerImageFor(targetPlayer));
        if(!iconProvider().allKeys().contains("follow" + target.getEntityId())) iconProvider().registerIcon("follow" + target.getEntityId(), icon);

        if (minimap.secondaryLayers().containsKey("follow" + target.getEntityId())) {
          getLogger().log(Level.WARNING, "Marker with this name already exists.");
          return;
        }

        float depth = 0.05F + minimap.secondaryLayers().size() * 0.01F;
        MinimapLayer iconBaseLayer = clientsideMinimapFactory().createMinimapLayer(player.getWorld(), null);
        SecondaryMinimapLayer iconLayer = new MarkerMinimapLayer(iconBaseLayer, new MinimapIconRenderer(icon), true, Config.instance().markers.customMarkers.stickToBorder, player.getWorld(), (int) target.getX(), (int) target.getZ(), depth);
        minimap.secondaryLayers().put("follow" + target.getEntityId(), iconLayer);
        packetSender().spawnLayer(player, iconBaseLayer);
        minimap.update(get());
        save(minimap);

        List<LivingEntity> list = new ArrayList<>();
        if(targetList.containsKey(player)) list.addAll(targetList.get(player));
        list.add(target);
        targetList.put(player, list);
      }

      @Override
      public void unfollowEntity(Player player, LivingEntity target) {
        List<LivingEntity> list = new ArrayList<>();
        if(targetList.containsKey(player)) list.addAll(targetList.get(player));
        list.remove(target);
        targetList.put(player, list);
      }

    });

    getServer().getScheduler().runTaskTimer(get(), task -> {
      updateFollowedEntities();
    }, 50, 5);

  }


  private void updateFollowedEntities() {
    targetList.forEach((player, livingEntities) -> {
      Minimap minimap = getPlayerMinimap(player);
      livingEntities.forEach(livingEntity -> {
        MinimapIcon icon = minimapIcon("follow" + livingEntity.getEntityId());
        try {
          modifyMarker(player, "follow" + livingEntity.getEntityId(), (m, marker) -> {
                    marker.setPositionX((int) livingEntity.getLocation().getX());
                    marker.setPositionZ((int) livingEntity.getLocation().getZ());
                  }
          );
        } catch (CommandSyntaxException e) {
          throw new RuntimeException(e);
        }
      });
    });
  }

  private void modifyMarker(Player player, String markerName, BiConsumer<Minimap, SecondaryMinimapLayer> consumer) throws CommandSyntaxException {
    Minimap minimap = getPlayerMinimap(player);
    SecondaryMinimapLayer marker = minimap.secondaryLayers().get(markerName);
    if (marker == null) {
      return;
    }
    consumer.accept(minimap, marker);
    minimap.update(get());
    save(minimap);
  }

  private MinimapIcon minimapIcon(String iconName) {
    MinimapIcon icon = null;
    if (!iconProvider().specialIconKeys().contains(iconName)) {
      icon = iconProvider().getIcon(iconName);
    }

    if (icon == null) {
      throw new NullPointerException("Invalid icon: " + iconName);
    }

    return icon;
  }

  private void save(Minimap minimap) {
    try {
      playerDataStorage().save(minimap);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  @SneakyThrows
  public void onDisable() {
    playerDataStorage.close();
  }

  @Override
  public ClientsideMinimapFactory clientsideMinimapFactory() {
    return defaultClientsideMinimapFactory;
  }

  @Override
  public MinimapPacketSender packetSender() {
    return defaultMinimapPacketSender;
  }

  @Override
  public WorldMinimapRenderer worldRenderer() {
    return defaultWorldRenderer;
  }

  @Override
  public MinimapIconProvider iconProvider() {
    return minimapIconProvider;
  }

  @Override
  public MinimapWorldRendererProvider worldRendererProvider() {
    return worldRendererProvider;
  }

  @Override
  public MinimapListener minimapListener() {
    return minimapListener;
  }

  @Override
  public MinimapBlockListener minimapBlockListener() {
    return minimapBlockListener;
  }

  @Override
  public SteerableViewFactory steerableViewFactory() {
    return steerableViewFactory;
  }

  @Override
  public MinimapPlayerDatabase playerDataStorage() {
    return playerDataStorage;
  }

  @Override
  public Minimap getPlayerMinimap(Player player) {
    return minimapListener.getPlayerMinimaps().get(player.getUniqueId());
  }

  @Override
  public FullscreenMinimap getFullscreenMinimap(Player player) {
    return minimapListener.getFullscreenMinimaps().get(player.getUniqueId());
  }

  public PassengerRewriter getPassengerRewriter(Player player) {
    return passengerRewriters.get(player);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerJoin(PlayerJoinEvent event) {
    PassengerRewriter rewriter = new PassengerRewriter();
    ((CraftPlayer) event.getPlayer()).getHandle().connection.connection.channel.pipeline().addBefore("packet_handler", "passenger_rewriter", rewriter);
    passengerRewriters.put(event.getPlayer(), rewriter);
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onPlayerQuit(PlayerQuitEvent event) {
    passengerRewriters.remove(event.getPlayer());
  }
}
