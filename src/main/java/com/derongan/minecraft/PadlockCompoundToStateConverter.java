package com.derongan.minecraft;

import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtList;
import com.comphenix.protocol.wrappers.nbt.NbtWrapper;
import com.google.common.collect.ImmutableSet;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;

class PadlockCompoundToStateConverter {

  private final NamespacedKey authorizedUuidsNamespacedKey;
  private final NamespacedKey uuidNamespacedKey;
  private final NamespacedKey lockFaceKey;
  private final NamespacedKey isDoubleKey;

  PadlockCompoundToStateConverter(NamespacedKey authorizedUuidsNamespacedKey,
      NamespacedKey uuidNamespacedKey, NamespacedKey lockFaceKey,
      NamespacedKey isDoubleKey) {
    this.authorizedUuidsNamespacedKey = authorizedUuidsNamespacedKey;
    this.uuidNamespacedKey = uuidNamespacedKey;
    this.lockFaceKey = lockFaceKey;
    this.isDoubleKey = isDoubleKey;
  }

  PadlockState convert(NbtCompound bukkitRoot) {
    NbtList<Map<String, NbtWrapper<byte[]>>> authorizedUuidsCompound = bukkitRoot.getList(
        authorizedUuidsNamespacedKey.toString());

    BlockFace blockFace = BlockFace.valueOf(bukkitRoot.getString(lockFaceKey.toString()));

    ImmutableSet.Builder<UUID> authorizedIdsBuilder = ImmutableSet.builder();

    for (var nbtMap : authorizedUuidsCompound) {
      ByteBuffer bb = ByteBuffer.wrap(nbtMap.get(uuidNamespacedKey.toString()).getValue());
      long firstLong = bb.getLong();
      long secondLong = bb.getLong();
      authorizedIdsBuilder.add(new UUID(firstLong, secondLong));
    }

    byte isDouble = bukkitRoot.getByte(isDoubleKey.toString());

    return new PadlockState(authorizedIdsBuilder.build(), blockFace, isDouble > 0);
  }
}
