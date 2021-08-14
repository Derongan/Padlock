package com.derongan.minecraft;

import java.util.Set;
import java.util.UUID;
import org.bukkit.block.BlockFace;

//todo redstone stuff. Allow redstone if other container matches auth ids or if set to allow.
public record PadlockState(Set<UUID> authorizedUuids, BlockFace lockFace, boolean isDouble) {

}
