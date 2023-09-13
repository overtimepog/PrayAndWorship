package com.prayandworship;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

import org.bukkit.scheduler.BukkitRunnable;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import java.nio.file.Paths;
import java.io.FileInputStream;

public class PrayAndWorshipPlugin extends JavaPlugin implements Listener {
    private Map<Player, List<String>> playerDroppedItems = new HashMap<>();
    private Map<Player, BukkitRunnable> playerTimers = new HashMap<>();
    private FileConfiguration config;
    private Map<String, String> altarSchematics = new HashMap<>();
    private Map<String, Material> altarSacrificeBlocks = new HashMap<>();
    private Map<String, Map<String, Map<String, Object>>> altarSacrifices = new HashMap<>();
    private Map<Player, List<Item>> playerDroppedEntities = new HashMap<>(); // To keep track of the actual dropped items
    private Map<String, Map<String, String>> altarMessages = new HashMap<>();


    @Override
    public void onEnable() {
        if (!new File(getDataFolder(), "prayconfig.yml").exists()) {
            saveResource("prayconfig.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "prayconfig.yml"));
    
        getLogger().info("PrayAndWorship plugin is initializing...");
    
        if (config.contains("altars")) {
            ConfigurationSection altarsSection = config.getConfigurationSection("altars");
            for (String altarKey : altarsSection.getKeys(false)) {
                getLogger().info("Loading configuration for altar: " + altarKey);
    
                // Load altar schematic path
                String schematicPathKey = "altars." + altarKey + ".schematic";
                if (config.contains(schematicPathKey)) {
                    String schematicPath = config.getString(schematicPathKey);
                    File schematicFile = new File(schematicPath);

                    // If the file doesn't exist in the designated path, look in the PrayAndWorship folder
                    if (!schematicFile.exists()) {
                        schematicPath = new File(getDataFolder(), schematicPath).getAbsolutePath();
                        schematicFile = new File(schematicPath);

                        if (!schematicFile.exists()) {
                            this.getLogger().warning("Schematic file for " + altarKey + " not found in both designated path and PrayAndWorship folder!");
                            continue; // Skip this altar's loading process
                        }
                    }

                    this.getLogger().info("Loading schematic file: " + schematicPath);
                    altarSchematics.put(altarKey, schematicPath);
                } else {
                    this.getLogger().warning(schematicPathKey + " not found in the config!");
                }

                // Load sacrifice block
                String sacrificeBlockPath = "altars." + altarKey + ".sacrifice_block";
                if (config.contains(sacrificeBlockPath)) {
                    altarSacrificeBlocks.put(altarKey, Material.valueOf(config.getString(sacrificeBlockPath).toUpperCase()));
                    this.getLogger().info("Sacrifice block for " + altarKey + " is " + config.getString(sacrificeBlockPath));
                } else {
                    this.getLogger().warning(sacrificeBlockPath + " not found in the config!");
                }
    
                // Load sacrifices and their effects
                String sacrificesPath = "altars." + altarKey + ".sacrifices";
                if (config.contains(sacrificesPath)) {
                    getLogger().info("Processing sacrifices for altar: " + altarKey);
                    ConfigurationSection sacSection = config.getConfigurationSection(sacrificesPath);
                    Map<String, Map<String, Object>> localSacrifices = new HashMap<>();
                    for (String itemsKey : sacSection.getKeys(false)) {
                        getLogger().info("Reading sacrifice: " + itemsKey);
                        Map<String, Object> sacrificeData = new HashMap<>();
                        sacrificeData.put("effect", sacSection.getString(itemsKey + ".effect"));
                        sacrificeData.put("duration", sacSection.getInt(itemsKey + ".duration"));
                        sacrificeData.put("amplifier", sacSection.getInt(itemsKey + ".amplifier"));
                        sacrificeData.put("success-message", sacSection.getString(itemsKey + ".success-message"));
                    
                        getLogger().info("Effect: " + sacrificeData.get("effect"));
                        getLogger().info("Duration: " + sacrificeData.get("duration"));
                        getLogger().info("Amplifier: " + sacrificeData.get("amplifier"));
                        getLogger().info("Success message: " + sacrificeData.get("success-message"));
                    
                        localSacrifices.put(itemsKey, sacrificeData);
                    }
                    altarSacrifices.put(altarKey, localSacrifices);
                } else {
                    this.getLogger().warning(sacrificesPath + " not found in the config!");
                }

                String messagePath = "altars." + altarKey + ".messages";
                if (config.contains(messagePath)) {
                    ConfigurationSection msgSection = config.getConfigurationSection(messagePath);
                    Map<String, String> localMessages = new HashMap<>();
                    for (String msgKey : msgSection.getKeys(false)) {
                        localMessages.put(msgKey, msgSection.getString(msgKey));
                    }
                    altarMessages.put(altarKey, localMessages);
                } else {
                    this.getLogger().warning(messagePath + " not found in the config!");
                }
            }
        }
    
        this.getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("PrayAndWorship plugin has been enabled!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("PrayAndWorship plugin has been disabled!");
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item droppedItem = event.getItemDrop();

        getLogger().info(player.getName() + " has dropped an item: " + droppedItem.getItemStack().getType().toString() + " x" + droppedItem.getItemStack().getAmount());

        // If there's already a timer running for that player, cancel it.
        if (playerTimers.containsKey(player)) {
            playerTimers.get(player).cancel();
            playerDroppedEntities.get(player).add(droppedItem); // Add the new item to the list
        } else {
            playerDroppedItems.put(player, new ArrayList<>());
            playerDroppedEntities.put(player, new ArrayList<>(Arrays.asList(droppedItem))); // Initialize with the dropped item
        }

        String itemKey = droppedItem.getItemStack().getType().toString() + "_" + droppedItem.getItemStack().getAmount();
        playerDroppedItems.get(player).add(itemKey);

        playerTimers.put(player, new BukkitRunnable() {
            @Override
            public void run() {
                // Check if any of the items were picked up
                for (Item item : playerDroppedEntities.get(player)) {
                    if (!item.isValid() && isAltar(item.getLocation())) { // Check if item is on an altar
                        String altarType = getAltarType(droppedItem.getLocation());
                        player.sendMessage(altarMessages.get(altarType).getOrDefault("ritual_interrupted", "§cThe ritual was interrupted!"));
                        playerDroppedItems.get(player).clear();
                        playerDroppedEntities.get(player).clear();
                        playerTimers.remove(player);
                        return; // Exit early
                    }
                }

                // Continue with the existing logic if none of the items were picked up
                List<String> playerItems = playerDroppedItems.get(player);
                Collections.sort(playerItems);
                String combinedKey = String.join(", ", playerItems);

                if (!checkAndApplyReward(player, combinedKey, droppedItem.getLocation())) {
                    String altarType = getAltarType(droppedItem.getLocation());
                    player.sendMessage(altarMessages.get(altarType).getOrDefault("sacrifice_failed", "§cYour sacrifice was not accepted. Try again with the correct items."));
                }

                playerDroppedItems.get(player).clear();
                playerDroppedEntities.get(player).clear();
                playerTimers.remove(player);
            }
        });
        playerTimers.get(player).runTaskLater(this, 100L); // 5 seconds
    }

    private boolean checkAndApplyReward(Player player, String combinedKey, Location itemLocation) {
        // Check if the dropped item is near a valid altar
        String altarType = getAltarType(itemLocation);
        if (altarType == null) {
            // The item is not near a valid altar. Handle this case appropriately.
            player.sendMessage("§cYour sacrifice was not near a valid altar.");
            return false;
        }
    
        // Retrieve the specific altar's sacrifices from the configuration
        Map<String, Map<String, Object>> altarSacrificesMap = altarSacrifices.get(altarType);
        
        // Convert both keys to a List and sort them, ensuring order doesn't matter
        List<String> configItems = new ArrayList<>(Arrays.asList(combinedKey.split(", ")));
        Collections.sort(configItems);
        
        for (String configKey : altarSacrificesMap.keySet()) {
            List<String> playerItems = new ArrayList<>(Arrays.asList(configKey.split(", ")));
            Collections.sort(playerItems);
            if (configItems.equals(playerItems)) {
                Map<String, Object> reward = altarSacrificesMap.get(configKey);
                PotionEffect effect = new PotionEffect(
                    PotionEffectType.getByName((String) reward.get("effect")),
                    (int) reward.get("duration") * 20, 
                    (int) reward.get("amplifier")
                );
                this.getLogger().info("Giving " + player.getName() + " " + effect.getType().getName() + " for " + effect.getDuration() + " seconds.");
                player.addPotionEffect(effect);
    
                // Send the custom success message
                if (reward.containsKey("success-message")) {
                    player.sendMessage((String) reward.get("success-message"));
                } else {
                    player.sendMessage("Your sacrifice was accepted! You have been rewarded with " + effect.getType().getName() + " for " + effect.getDuration() + " seconds.");
                }
                //delete the items
                for (Item item : playerDroppedEntities.get(player)) {
                    item.remove();
                }
                return true; // Indicate that a reward was given
            }
        }
        return false; // No reward was given
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Location placedBlockLocation = event.getBlock().getLocation();
        if (isAltar(placedBlockLocation)) {
            Player player = event.getPlayer();
            String altarType = getAltarType(placedBlockLocation); 
            player.sendMessage(altarMessages.get(altarType).getOrDefault("altar_created", "§aYou have successfully created an altar!"));
        }
    }

    private boolean isAltar(Location location) {
        try {
            // Convert Bukkit location to WorldEdit vector
            BlockVector3 placedBlockPos = BukkitAdapter.asBlockVector(location);
    
            for (String altarKey : altarSchematics.keySet()) {
                String schematicPath = altarSchematics.get(altarKey);
    
                // Load the schematic
                ClipboardReader reader = ClipboardFormats.findByFile(Paths.get(schematicPath).toFile())
                        .getReader(new FileInputStream(schematicPath));
                Clipboard clipboard = reader.read();
    
                for (BlockVector3 schematicVector : clipboard.getRegion()) {
                    // For each block in the schematic, treat it as if it's the block that was just placed
                    // and see if it matches the world
                    BlockVector3 adjustedCheckPos = placedBlockPos.subtract(schematicVector);
    
                    boolean isMatch = true; // assume it's a match until proven otherwise
    
                    for (BlockVector3 relativeVector : clipboard.getRegion()) {
                        BlockVector3 worldPos = adjustedCheckPos.add(relativeVector);
                        BlockState schematicBlock = clipboard.getBlock(relativeVector);
    
                        Material worldMaterial = location.getWorld().getBlockAt(worldPos.getX(), worldPos.getY(), worldPos.getZ()).getType();
                        com.sk89q.worldedit.world.block.BlockType worldEditBlockType = BukkitAdapter.asBlockType(worldMaterial);
                        BaseBlock worldBlock = worldEditBlockType.getDefaultState().toBaseBlock();
    
                        if (!worldBlock.equalsFuzzy(schematicBlock)) {
                            isMatch = false;
                            break;
                        }
                    }
    
                    if (isMatch) {
                        return true; // We found a match, no need to continue
                    }
                }
            }
            return false; // No matches found
        } catch (Exception e) {
            getLogger().severe("Error during isAltar: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }   
    
    private String getAltarType(Location location) {
        try {
            // Convert Bukkit location to WorldEdit vector
            BlockVector3 placedBlockPos = BukkitAdapter.asBlockVector(location);
    
            for (String altarKey : altarSchematics.keySet()) {
                String schematicPath = altarSchematics.get(altarKey);
    
                // Load the schematic
                ClipboardReader reader = ClipboardFormats.findByFile(Paths.get(schematicPath).toFile())
                        .getReader(new FileInputStream(schematicPath));
                Clipboard clipboard = reader.read();
    
                for (BlockVector3 schematicVector : clipboard.getRegion()) {
                    // For each block in the schematic, treat it as if it's the block that was just placed
                    // and see if it matches the world
                    BlockVector3 adjustedCheckPos = placedBlockPos.subtract(schematicVector);
    
                    boolean isMatch = true; // assume it's a match until proven otherwise
    
                    for (BlockVector3 relativeVector : clipboard.getRegion()) {
                        BlockVector3 worldPos = adjustedCheckPos.add(relativeVector);
                        BlockState schematicBlock = clipboard.getBlock(relativeVector);
    
                        Material worldMaterial = location.getWorld().getBlockAt(worldPos.getX(), worldPos.getY(), worldPos.getZ()).getType();
                        com.sk89q.worldedit.world.block.BlockType worldEditBlockType = BukkitAdapter.asBlockType(worldMaterial);
                        BaseBlock worldBlock = worldEditBlockType.getDefaultState().toBaseBlock();
    
                        if (!worldBlock.equalsFuzzy(schematicBlock)) {
                            isMatch = false;
                            break;
                        }
                    }
    
                    if (isMatch) {
                        return altarKey; // We found a match, return the type of altar
                    }
                }
            }
            return null; // No matches found
        } catch (Exception e) {
            getLogger().severe("Error during getAltarType: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }    
    
}