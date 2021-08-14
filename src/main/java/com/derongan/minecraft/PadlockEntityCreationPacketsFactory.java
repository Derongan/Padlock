package com.derongan.minecraft;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry;
import com.derongan.minecraft.PadlockEntityManager.PadlockEntity;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

public class PadlockEntityCreationPacketsFactory {

  private final ProtocolManager protocolManager;
  private final ItemStack lockItem;


  public PadlockEntityCreationPacketsFactory(ProtocolManager protocolManager,
      ItemStack lockItem) {
    this.protocolManager = protocolManager;
    this.lockItem = lockItem;
  }

  record PadlockEntityCreationPackets(PacketContainer spawn, PacketContainer meta) {

  }

  public PadlockEntityCreationPackets create(PadlockEntity padlockEntity, Player player) {
    PacketContainer spawnPacket = createSpawnPacket(padlockEntity.lockedBlock().toVector(),
        padlockEntity.state()
            .lockFace(), padlockEntity.entityId());
    PacketContainer metaPacket = createMetaPacket(padlockEntity, player);

    return new PadlockEntityCreationPackets(spawnPacket, metaPacket);
  }

  private PacketContainer createSpawnPacket(Vector location, BlockFace blockFace, int entityId) {
    PacketContainer spawnPacket = protocolManager.createPacket(Server.SPAWN_ENTITY);
    spawnPacket.getIntegers().write(0, entityId); // ID
    spawnPacket.getUUIDs().write(0, UUID.randomUUID()); // UUID
    spawnPacket.getEntityTypeModifier().write(0, EntityType.ITEM_FRAME); // TYPE
    spawnPacket.getDoubles().write(0, location.getX() + blockFace.getModX()); // X
    spawnPacket.getDoubles().write(1, location.getY() + blockFace.getModY()); // Y
    spawnPacket.getDoubles().write(2, location.getZ() + blockFace.getModZ()); // Z
    spawnPacket.getIntegers().write(6, dataBitFromBlockFace(blockFace)); // Entity Data

    return spawnPacket;
  }

  private PacketContainer createMetaPacket(PadlockEntity padlockEntity, Entity player) {
    PacketContainer metaPacket = protocolManager.createPacket(Server.ENTITY_METADATA);
    metaPacket.getIntegers().write(0, padlockEntity.entityId());

    String text = getLockText(padlockEntity.state());

    ItemStack itemStack = lockItem.clone();
    ItemMeta itemMeta = itemStack.getItemMeta();
    itemMeta.setDisplayName(text);
    itemMeta.setCustomModelData(padlockEntity.state().isDouble() ? 2 : 1);
    itemStack.setItemMeta(itemMeta);

    WrappedDataWatcher watcher = new WrappedDataWatcher(); // Entity meta kinda.
    watcher.setEntity(player);
    watcher.setObject(0, Registry.get(Byte.class), (byte) (0x20));
    watcher.setObject(8, Registry.getItemStackSerializer(false), itemStack);

    metaPacket.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());

    return metaPacket;
  }

  // TODO move elsewhere for reuse
  private static String getLockText(PadlockState state) {
    String owners = state.authorizedUuids().stream().map(Bukkit::getOfflinePlayer)
        .map(OfflinePlayer::getName)
        .collect(
            Collectors.joining(", "));

    return ChatColor.GRAY + owners;
  }

  private static int dataBitFromBlockFace(BlockFace blockFace) {
    switch (blockFace) {
      case NORTH -> {
        return 2;
      }
      case EAST -> {
        return 5;
      }
      case SOUTH -> {
        return 3;
      }
      case WEST -> {
        return 4;
      }
      case UP -> {
        return 1;
      }
      case DOWN -> {
        return 0;
      }
    }

    return 0; // Fallback, todo throw
  }
}
