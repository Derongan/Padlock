package com.derongan.minecraft;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.derongan.minecraft.pdc.PadlockPersistentDataType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class PadlockPlugin extends JavaPlugin {

  private static final String PADLOCK_KEY = "padlock";
  private static final String AUTHORIZED_UUIDS_KEY = "authorized_uuids";
  private static final String UUID_KEY = "uuid";
  private static final String FACE_KEY = "lockFace";
  private static final String IS_DOUBLE_KEY = "double";

  private final NamespacedKey padlockKey;
  private final NamespacedKey lockFaceKey;
  private final NamespacedKey isDoubleKey;
  private final PadlockPersistentDataType padlockPdt;
  private final NamespacedKey authorizedUuidsNamespacedKey;
  private final NamespacedKey uuidNamespacedKey;

  public PadlockPlugin() {
    padlockKey = createKey(PADLOCK_KEY);
    lockFaceKey = createKey(FACE_KEY);
    isDoubleKey = createKey(IS_DOUBLE_KEY);
    authorizedUuidsNamespacedKey = createKey(AUTHORIZED_UUIDS_KEY);
    uuidNamespacedKey = createKey(UUID_KEY);

    padlockPdt = new PadlockPersistentDataType(authorizedUuidsNamespacedKey, uuidNamespacedKey,
        lockFaceKey, isDoubleKey);

  }

  @Override
  public void onEnable() {
    ItemStack itemStack = getPadlockItemStack();
    ShapedRecipe padlockRecipe = new ShapedRecipe(createKey(PADLOCK_KEY), itemStack);
    padlockRecipe
        .shape("S", "I")
        .setIngredient('I',
            new RecipeChoice.ExactChoice(new ItemStack(Material.IRON_NUGGET)))
        .setIngredient('S',
            new RecipeChoice.ExactChoice(new ItemStack(Material.STICK)));

    getServer().addRecipe(padlockRecipe);

    ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

    PadlockEntityCreationPacketsFactory padlockFactory = new PadlockEntityCreationPacketsFactory(
        protocolManager, itemStack);

    PadlockEntityManager padlockEntityManager = new PadlockEntityManager(padlockFactory,
        protocolManager);

    getServer().getPluginManager()
        .registerEvents(new InventoryListener(padlockKey, padlockPdt, padlockEntityManager), this);

    protocolManager
        .addPacketListener(
            new MapChunkPacketAdapter(protocolManager, this, padlockEntityManager,
                new PadlockCompoundToStateConverter(authorizedUuidsNamespacedKey, uuidNamespacedKey,
                    lockFaceKey, isDoubleKey), padlockKey
            ));
  }

  private ItemStack getPadlockItemStack() {
    ItemMeta itemMeta = getServer().getItemFactory().getItemMeta(Material.STICK);
    itemMeta.setDisplayName("Padlock");
    itemMeta.setCustomModelData(1);
    ItemStack itemStack = new ItemStack(Material.STICK);
    itemStack.setItemMeta(itemMeta);
    return itemStack;
  }

  @Override
  public void onDisable() {
    getServer().removeRecipe(padlockKey);
  }

  private NamespacedKey createKey(String uuidKey) {
    return new NamespacedKey(this, uuidKey);
  }

}
