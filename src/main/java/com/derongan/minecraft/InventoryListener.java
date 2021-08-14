package com.derongan.minecraft;

import com.derongan.minecraft.pdc.PadlockPersistentDataType;
import com.google.common.collect.ImmutableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.util.Vector;

public class InventoryListener implements Listener {

  private final NamespacedKey padlockKey;
  private final PadlockPersistentDataType padlockPersistentDataType;
  private final PadlockEntityManager padlockEntityManager;

  public InventoryListener(NamespacedKey padlockKey,
      PadlockPersistentDataType padlockPersistentDataType,
      PadlockEntityManager padlockEntityManager) {
    this.padlockKey = padlockKey;
    this.padlockPersistentDataType = padlockPersistentDataType;
    this.padlockEntityManager = padlockEntityManager;
  }

  @EventHandler(ignoreCancelled = true)
  void onInventoryOpen(InventoryOpenEvent event) {
    InventoryHolder holder = event.getInventory().getHolder();

    event.getPlayer();

    if (holder instanceof BlockState blockState) {
      getPadlockState(blockState).ifPresent(padlockState -> {
        if (!checkAccess(event.getPlayer(), padlockState)) {
          event.getPlayer().sendMessage("You do not have access to that!");
          event.setCancelled(true);
        }
      });
    }
  }

  @EventHandler(ignoreCancelled = true)
  void onPlayerInteract(PlayerInteractEvent event) {
    if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
      return;
    }

    if (!event.getHand().equals(EquipmentSlot.HAND)) {
      return;
    }

    if (isLockItem(event.getItem())) {

      if (event.getClickedBlock() != null && event.getClickedBlock()
          .getState() instanceof Container container) {

        Optional<PadlockState> existingState = Optional.ofNullable(event.getClickedBlock())
            .map(Block::getState)
            .flatMap(this::getPadlockState);

        if (existingState.isPresent()) {
          event.getPlayer().sendMessage(String.format("This chest is already locked by %s.",
              existingState.get().authorizedUuids().stream().map(Bukkit::getPlayer)
                  .filter(Objects::nonNull).map(Player::getName).collect(
                      Collectors.joining())));
        } else {
          Block storingBlock = getPadlockStoringBlock(event.getClickedBlock());

          PadlockState padlockState = new PadlockState(
              ImmutableSet.of(event.getPlayer().getUniqueId()),
              storingBlock.getBlockData() instanceof Chest chest ? chest.getFacing()
                  : event.getBlockFace(),
              isDouble(storingBlock));

          Container storingContainer = (Container) storingBlock.getState();
          storingContainer.getPersistentDataContainer()
              .set(padlockKey, padlockPersistentDataType, padlockState);
          storingContainer.update();

          if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getItem().setAmount(event.getItem().getAmount() - 1);
          }
          event.getPlayer().sendMessage("Locked!");

          padlockEntityManager.createPadlock(storingBlock.getLocation(), padlockState);

        }
        event.setCancelled(true);
      }
    }
  }

  @EventHandler(ignoreCancelled = true)
  void onBlockPlace(BlockPlaceEvent event) {
    if (isDouble(event.getBlock())) {
      Block otherBlock = getOtherSideOfDoubleChest(event.getBlock());

      getPadlockState(otherBlock.getState()).ifPresent(padlock -> {
        if (!checkAccess(event.getPlayer(), padlock)) {
          event.getPlayer().sendMessage("You cannot extend someone else's locked chest!");
          event.setCancelled(true);
        } else {
          padlockEntityManager.destroyPadlock(otherBlock.getLocation());

          PadlockState updatedPadlockState = new PadlockState(padlock.authorizedUuids(),
              padlock.lockFace(), true);
          if (((Chest) event.getBlock().getBlockData()).getType() == Type.LEFT) {
            BlockState otherState = otherBlock.getState();
            PersistentDataHolder toClear = (PersistentDataHolder) otherState;
            toClear.getPersistentDataContainer().remove(padlockKey);
            BlockState newState = event.getBlock().getState();
            ((PersistentDataHolder) newState).getPersistentDataContainer()
                .set(padlockKey, padlockPersistentDataType, updatedPadlockState);
            otherState.update();
            newState.update();
            padlockEntityManager.createPadlock(event.getBlock().getLocation(), updatedPadlockState);
          } else {
            padlockEntityManager.createPadlock(otherBlock.getLocation(), updatedPadlockState);
          }
        }
      });
    }
  }

  @EventHandler(ignoreCancelled = true)
  void onBlockBreak(BlockBreakEvent event) {
    if (event.getBlock().getState() instanceof PersistentDataHolder) {
      getPadlockState(event.getBlock().getState()).ifPresent(state -> {
        boolean allowed = checkAccess(event.getPlayer(), state);

        if (!allowed) {
          event.getPlayer().sendMessage("You cannot destroy someone else's lock!");
          event.setCancelled(true);
        } else {

          if (isDouble(event.getBlock())) {
            PadlockState updatedPadlockState = new PadlockState(state.authorizedUuids(),
                state.lockFace(), false);
            Block otherSideOfDoubleChest = getOtherSideOfDoubleChest(event.getBlock());

            // Shift the state over
            if (((Chest) event.getBlock().getBlockData()).getType() == Type.LEFT) {
              BlockState otherState = otherSideOfDoubleChest.getState();
              ((PersistentDataHolder) otherState).getPersistentDataContainer()
                  .set(padlockKey, padlockPersistentDataType, updatedPadlockState);
              otherState.update();
              padlockEntityManager.destroyPadlock(event.getBlock().getLocation());
            } else {
              padlockEntityManager.destroyPadlock(otherSideOfDoubleChest.getLocation());
            }
            padlockEntityManager.createPadlock(otherSideOfDoubleChest.getLocation(),
                updatedPadlockState);
          } else {
            padlockEntityManager.destroyPadlock(event.getBlock().getLocation());
          }
        }
      });
    }
  }

  private static boolean isDouble(Block block) {
    return block.getBlockData() instanceof Chest chest && chest.getType() != Type.SINGLE;
  }

  private static Block getOtherSideOfDoubleChest(Block block) {
    Chest chest = (Chest) block.getBlockData();
    BlockFace blockFace = chest.getFacing();

    if (chest.getType() == Type.LEFT) {
      return block.getRelative(-blockFace.getModZ(), 0, blockFace.getModX());
    }
    return block.getRelative(blockFace.getModZ(), 0, -blockFace.getModX());
  }

  private static Block getPadlockStoringBlock(Block block) {
    if (isDouble(block)) {
      return ((Chest) block.getBlockData()).getType() == Type.LEFT ? block
          : getOtherSideOfDoubleChest(block);
    }
    return block;
  }

  private Optional<PadlockState> getPadlockState(@Nullable BlockState blockState) {
    if (blockState instanceof PersistentDataHolder dataHolder) {
      if (isDouble(blockState.getBlock())) {
        dataHolder = (PersistentDataHolder) getPadlockStoringBlock(
            blockState.getBlock()).getState();
      }

      return Optional.ofNullable(
          dataHolder.getPersistentDataContainer().get(padlockKey, padlockPersistentDataType));
    }

    return Optional.empty();
  }

  private static Vector getChunkCoord(Location location) {
    Chunk chunk = location.getChunk();

    return new Vector(chunk.getX(), 0, chunk.getZ());
  }

  private boolean isLockItem(@Nullable ItemStack itemStack) {
    if (itemStack == null) {
      return false;
    }

    return itemStack.getType() == Material.STICK && itemStack.getItemMeta() != null
        && itemStack.getItemMeta().hasCustomModelData()
        && itemStack.getItemMeta().getCustomModelData() == 1;
  }


  private boolean checkAccess(HumanEntity player, PadlockState padlockState) {
    return padlockState.authorizedUuids().isEmpty() || padlockState.authorizedUuids()
        .contains(player.getUniqueId());
  }
}
