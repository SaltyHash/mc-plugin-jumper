package me.saltyhash.jumper;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bukkit plugin that allows players to teleport to line-of-sight destinations.
 */
public class Jumper extends JavaPlugin implements Listener {
    private static final Set<Material> BLOCK_MATERIALS_TO_IGNORE = new HashSet<>(Arrays.asList(
            Material.AIR,
//            Material.CARPET,
            Material.FIRE,
            Material.LADDER,
//            Material.SAPLING,
            Material.SUGAR_CANE,
            Material.SNOW,
            Material.TALL_GRASS,
            Material.TORCH,
            Material.VINE,
            Material.WATER
    ));

    private static final double MAX_PLAYER_FOOD_LEVEL = 20.0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled.");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        // Command:  /jumper [...]
        if (cmd.getName().equalsIgnoreCase("jumper")) {
            // Command:  /jumper
            if (args.length == 0) {
                sender.sendMessage("Jumper allows you to teleport to wherever you are looking, " +
                        "simply by jumping and right-clicking while in the air.");
                return true;
            }

            // Command:  /jumper reload
            else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                // Sender does not have permission to reload the config?
                if (!sender.hasPermission("jumper.reload")) {
                    sender.sendMessage(ChatColor.RED +
                            "You do not have permission to execute that command");
                    return true;
                }

                // Reload the config
                reloadConfig();
                sender.sendMessage("Reloaded Jumper config file");
                getLogger().info(sender.getName() + " reloaded config file");
                return true;
            }

            // Command:  /jumper version
            else if (args.length == 1 && args[0].equalsIgnoreCase("version")) {
                // Sender does not have permission to display version information?
                if (!sender.hasPermission("jumper.version")) {
                    sender.sendMessage(ChatColor.RED +
                            "You do not have permission to execute that command");
                    return true;
                }

                // Build and send message
                final PluginDescriptionFile pdf = getDescription();
                final List<String> authors = pdf.getAuthors();
                final StringBuilder msg = new StringBuilder();
                msg.append(pdf.getFullName());
                if (authors.size() == 1) {
                    msg.append("\nAuthor: ").append(authors.get(0));
                } else if (authors.size() > 1) {
                    msg.append("\nAuthors:");
                    for (final String author : authors)
                        msg.append("\n    ").append(author);
                }
                sender.sendMessage(msg.toString());
                return true;
            } else {
                sender.sendMessage(ChatColor.RED +
                        "Unrecognized Jumper command; type '/help jumper' for a list of commands.");
                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();

        // Player must have permission
        if (!player.hasPermission("jumper.use")) return;

        /* Checks are in Frequently Failed First order */

        // Event must be for HAND (not OFF_HAND)
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Player must be jumping
        if (player.isOnGround()) return;

        // Player must be right-clicking air
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;

        // Player must have enough health
        final AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        final double playerHealthPercent =
                maxHealth != null ? 100.0 * player.getHealth() / maxHealth.getValue() : 100.0;
        if (playerHealthPercent < getConfigMinHealth()) return;

        // Player must not be too hungry
        final double playerFoodPercent = 100.0 * (double) player.getFoodLevel() / MAX_PLAYER_FOOD_LEVEL;
        if (playerFoodPercent < getConfigMinFood()) return;

        // Player must not be holding a bow
        if (player.getInventory().getItemInMainHand().getType() == Material.BOW ||
                player.getInventory().getItemInOffHand().getType() == Material.BOW) return;

        // Player must not be riding anything
        if (player.isInsideVehicle()) return;

        // Player must not be flying
        if (player.isFlying()) return;

        // Get min and max distances
        final int minDistance = getConfigDistanceMin();
        final int maxDistance = getConfigDistanceMax();
        if (minDistance > maxDistance) return;      // This would be dumb but okay

        // Determine the destination by finding the block at the crosshairs
        Location newLocation = null;
        final BlockIterator bi = new BlockIterator(player.getEyeLocation(), 0, maxDistance);
        Block nextBlock, prevBlock = bi.next();
        while (bi.hasNext()) {
            nextBlock = bi.next();

            // Reached the selected block?
            if (!ignoreBlock(nextBlock)) {
                // Get the two blocks above the selected block
                Location l = nextBlock.getLocation();
                final Block b0 = l.add(0.0, 1.0, 0.0).getBlock();
                final Block b1 = l.add(0.0, 1.0, 0.0).getBlock();

                newLocation = player.getLocation();

                // If two blocks above selected block are clear, then teleport ON TOP of the selected block
                if (ignoreBlock(b0) && ignoreBlock(b1)) {
                    l = nextBlock.getLocation();
                    newLocation.setX(l.getX() + 0.5);
                    newLocation.setY(l.getY() + 1.1);
                }

                // Otherwise, teleport IN FRONT of the selected block
                else {
                    l = prevBlock.getLocation();
                    newLocation.setX(l.getX() + 0.5);
                    newLocation.setY(l.getY());
                }

                newLocation.setZ(l.getZ() + 0.5);

                break;
            }
            prevBlock = nextBlock;
        }
        if (newLocation == null) return;
        final Location oldLocation = player.getLocation();
        if (oldLocation.distance(newLocation) < minDistance) return;

        /* Teleport the player to the selected location */

        // Play teleport sound at current location
        playTeleportSound(oldLocation);

        // Get the player's current velocity
        final Vector playerVelocity = player.getVelocity();

        // Teleport the player to the new location
        player.teleport(newLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);

        // Set the player's velocity to that before the teleport
        player.setVelocity(playerVelocity);

        // Play teleport sound at the new location
        playTeleportSound(newLocation);

        // Increase the player's exhaustion
        final double exhaustion = getConfigExhaustionPerMeter() * oldLocation.distance(newLocation);
        if (exhaustion > 0.0) player.setExhaustion(player.getExhaustion() + (float) exhaustion);

        // This will prevent things like snowballs from being thrown
        event.setCancelled(true);
    }

    /**
     * Returns the max teleportation distance [0, server view distance].
     */
    private int getConfigDistanceMax() {
        final int max = Math.max(getConfig().getInt("distance.max"), 0);
        final int serverViewDistance = 16 * getServer().getViewDistance();
        return (max > 0) ? Math.min(max, serverViewDistance) : serverViewDistance;
    }

    /**
     * Returns the min teleportation distance [0, inf].
     */
    private int getConfigDistanceMin() {
        return Math.max(getConfig().getInt("distance.min"), 0);
    }

    private double getConfigExhaustionPerMeter() {
        return Math.max(0.0, getConfig().getDouble("exhaustion_per_meter"));
    }

    /**
     * Min health; should be [0, 100], but could technically be outside this range.
     */
    private double getConfigMinHealth() {
        return getConfig().getDouble("min_health");
    }

    /**
     * Min food; should be [0, 100], but could technically be outside this range.
     */
    private int getConfigMinFood() {
        return getConfig().getInt("min_food");
    }

    private boolean getConfigSound() {
        return getConfig().getBoolean("sound");
    }

    private static boolean ignoreBlock(final Block block) {
        return BLOCK_MATERIALS_TO_IGNORE.contains(block.getType());
    }

    private void playTeleportSound(final Location location) {
        if (!getConfigSound()) return;

        final World world = location.getWorld();
        if (world == null) return;

        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }
}