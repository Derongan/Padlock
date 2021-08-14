package com.derongan.minecraft.pdc;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.derongan.minecraft.PadlockState;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PadlockPersistentDataType implements
    PersistentDataType<PersistentDataContainer, PadlockState> {

  private final UuidPersistentDataType uuidPersistentDataType;
  private final NamespacedKey authorizedUuidsNamespacedKey;
  private final NamespacedKey uuidNamespacedKey;
  private final NamespacedKey lockFace;
  private final NamespacedKey isDoubleKey;

  public PadlockPersistentDataType(NamespacedKey authorizedUuidsNamespacedKey,
      NamespacedKey uuidNamespacedKey, NamespacedKey lockFace, NamespacedKey isDoubleKey) {
    uuidPersistentDataType = new UuidPersistentDataType();
    this.lockFace = lockFace;
    this.authorizedUuidsNamespacedKey = authorizedUuidsNamespacedKey;
    this.uuidNamespacedKey = uuidNamespacedKey;
    this.isDoubleKey = isDoubleKey;
  }

  @Override
  public Class<PersistentDataContainer> getPrimitiveType() {
    return PersistentDataContainer.class;
  }

  @Override
  public Class<PadlockState> getComplexType() {
    return PadlockState.class;
  }

  @Override
  public PersistentDataContainer toPrimitive(PadlockState complex,
      PersistentDataAdapterContext context) {
    PersistentDataContainer root = context.newPersistentDataContainer();

    PersistentDataContainer[] uuids = complex.authorizedUuids().stream()
        .map(uuid -> {
          PersistentDataContainer container = context.newPersistentDataContainer();
          container.set(uuidNamespacedKey, uuidPersistentDataType, uuid);
          return container;
        })
        .toArray(PersistentDataContainer[]::new);

    root.set(authorizedUuidsNamespacedKey, TAG_CONTAINER_ARRAY, uuids);
    root.set(lockFace, STRING, complex.lockFace().name());
    root.set(isDoubleKey, BYTE, (byte) (complex.isDouble() ? 1 : 0));

    return root;
  }

  @Override
  public PadlockState fromPrimitive(PersistentDataContainer primitive,
      PersistentDataAdapterContext context) {
    PersistentDataContainer[] uuidContainers = primitive.get(authorizedUuidsNamespacedKey,
        TAG_CONTAINER_ARRAY);

    Set<UUID> padlockedUuids = Optional.ofNullable(uuidContainers).map(uuids -> Arrays.stream(uuids)
        .map(uuid -> uuid.get(uuidNamespacedKey, uuidPersistentDataType)).collect(
            toImmutableSet())).orElse(ImmutableSet.of());

    byte isDouble = primitive.get(isDoubleKey, BYTE);

    return new PadlockState(padlockedUuids, BlockFace.valueOf(primitive.get(lockFace, STRING)),
        isDouble > 0);
  }
}
