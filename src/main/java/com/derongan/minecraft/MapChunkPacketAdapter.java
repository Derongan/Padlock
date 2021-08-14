package com.derongan.minecraft;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.comphenix.protocol.PacketType.Play.Client;
import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers.Direction;
import com.comphenix.protocol.wrappers.EnumWrappers.EntityUseAction;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import com.comphenix.protocol.wrappers.WrappedEnumEntityUseAction;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

class MapChunkPacketAdapter extends PacketAdapter {

  private final ProtocolManager protocolManager;
  private final PadlockEntityManager padlockEntityManager;
  private final PadlockCompoundToStateConverter converter;
  private final NamespacedKey padlockKey;

  MapChunkPacketAdapter(ProtocolManager protocolManager, Plugin plugin,
      PadlockEntityManager padlockEntityManager,
      PadlockCompoundToStateConverter converter,
      NamespacedKey padlockKey) {
    super(plugin, Server.MAP_CHUNK, Client.USE_ENTITY);
    this.protocolManager = protocolManager;
    this.padlockEntityManager = padlockEntityManager;
    this.converter = converter;
    this.padlockKey = padlockKey;
  }

  @Override
  public void onPacketSending(PacketEvent event) {
    List<NbtBase<?>> tileEntities = event.getPacket().getListNbtModifier().read(0);

    ImmutableList<NbtCompound> padlocksContainingEntities = tileEntities.stream()
        .map(NbtFactory::asCompound)
        .filter(nbt -> nbt.containsKey("PublicBukkitValues"))
        .filter(nbt -> nbt.getCompound("PublicBukkitValues")
            .containsKey(padlockKey.toString()))
        .collect(toImmutableList());

    for (NbtCompound padlockContainingEntity : padlocksContainingEntities) {
      NbtCompound bukkitRoot = padlockContainingEntity.getCompound("PublicBukkitValues")
          .getCompound(padlockKey.toString());
      double x = padlockContainingEntity.getInteger("x");
      double y = padlockContainingEntity.getInteger("y");
      double z = padlockContainingEntity.getInteger("z");

      PadlockState padlockState = converter.convert(bukkitRoot);

      padlockEntityManager.loadPadlock(new Location(event.getPlayer().getWorld(), x, y, z),
          padlockState, event.getPlayer());
    }
  }

  @Override
  public void onPacketReceiving(PacketEvent event) {
    int entityId = event.getPacket().getIntegers().read(0);
    WrappedEnumEntityUseAction action = event.getPacket().getEnumEntityUseActions().read(0);

    if (action.getAction().equals(EntityUseAction.INTERACT_AT)) {

      padlockEntityManager.getPadlock(entityId).ifPresent(padlock -> {
        PacketContainer placeInstead = protocolManager.createPacket(Client.USE_ITEM);
        MovingObjectPositionBlock movingObjectPositionBlock = new MovingObjectPositionBlock(
            new BlockPosition(padlock.lockedBlock().toVector()),
            action.getPosition(),
            Direction.valueOf(padlock.state().lockFace().name()), false);

        placeInstead.getMovingBlockPositions().write(0, movingObjectPositionBlock);
        event.setPacket(placeInstead);
      });
    }
  }
}
