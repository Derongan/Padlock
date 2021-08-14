package com.derongan.minecraft;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.derongan.minecraft.PadlockEntityCreationPacketsFactory.PadlockEntityCreationPackets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PadlockEntityManager {

  /**
   * Retrieve the entity counter field used to generate a unique entity ID.
   */
  private static final FieldAccessor ENTITY_ID = Accessors.getFieldAccessor(
      MinecraftReflection.getEntityClass(), AtomicInteger.class, true);


  private final Map<Location, PadlockEntity> padlockEntityByLocation;
  private final Map<Integer, PadlockEntity> padlockEntityById;

  private final Supplier<Integer> entityIdSupplier;
  private final PadlockEntityCreationPacketsFactory padlockPacketFactory;
  private final ProtocolManager protocolManager;

  public PadlockEntityManager(PadlockEntityCreationPacketsFactory padlockPacketFactory,
      ProtocolManager protocolManager) {
    this.padlockPacketFactory = padlockPacketFactory;
    this.protocolManager = protocolManager;

    this.padlockEntityByLocation = new HashMap<>();
    this.padlockEntityById = new HashMap<>();
    this.entityIdSupplier = ((AtomicInteger) ENTITY_ID.get(null))::incrementAndGet;
  }


  /**
   * Loads a padlock entity on chunk load. Only sends to loading player.
   */
  public void loadPadlock(Location location, PadlockState padlockState, Player player) {
    PadlockEntity entity = padlockEntityByLocation.computeIfAbsent(location,
        loc -> new PadlockEntity(entityIdSupplier.get(), location, padlockState));
    padlockEntityById.putIfAbsent(entity.entityId(), entity);

    sendCreatePacket(entity, player);
  }


  public void createPadlock(Location location, PadlockState padlockState) {
    Preconditions.checkArgument(!padlockEntityByLocation.containsKey(location),
        "Padlock already exists! Cannot create new one!");

    int entityId = entityIdSupplier.get();
    PadlockEntity padlock = new PadlockEntity(entityId, location, padlockState);
    padlockEntityByLocation.put(location, padlock);
    padlockEntityById.put(entityId, padlock);

    location.getWorld().getPlayers().stream()
        .filter(player -> canSeePadlock(location, player))
        .forEach(player -> sendCreatePacket(padlock, player));
  }

  public void destroyPadlock(Location location) {
    Preconditions.checkArgument(padlockEntityByLocation.containsKey(location),
        "Padlock does not exists! Cannot remove it!");

    PadlockEntity entity = padlockEntityByLocation.remove(location);
    int entityId = entity.entityId;
    padlockEntityById.remove(entityId);

    location.getWorld().getPlayers().stream()
        .filter(player -> canSeePadlock(location, player))
        .forEach(player -> sendRemovePacket(entityId, player));
  }

  public Optional<PadlockEntity> getPadlock(int entityId) {
    return Optional.ofNullable(padlockEntityById.get(entityId));
  }

  private void sendRemovePacket(int entityId, Player player) {
    PacketContainer removePacket = protocolManager.createPacket(Server.ENTITY_DESTROY);
    removePacket.getIntLists().write(0, ImmutableList.of(entityId));

    try {
      protocolManager.sendServerPacket(player, removePacket);
    } catch (InvocationTargetException e) {
      throw new RuntimeException("Failed to send padlock creation packets!", e);
    }
  }

  private void sendCreatePacket(PadlockEntity padlockEntity, Player player) {
    PadlockEntityCreationPackets packets = padlockPacketFactory.create(padlockEntity, player);

    try {
      protocolManager.sendServerPacket(player, packets.spawn());
      protocolManager.sendServerPacket(player, packets.meta());
    } catch (InvocationTargetException e) {
      throw new RuntimeException("Failed to send padlock creation packets!", e);
    }
  }

  private static boolean canSeePadlock(Location padlockLocation, Player observer) {
    int viewDistanceInBlocks =
        Math.min(Bukkit.getViewDistance(), observer.getClientViewDistance()) * 16;

    return padlockLocation.distanceSquared(observer.getLocation())
        <= viewDistanceInBlocks * viewDistanceInBlocks;
  }

  static record PadlockEntity(int entityId, Location lockedBlock, PadlockState state) {

  }
}
