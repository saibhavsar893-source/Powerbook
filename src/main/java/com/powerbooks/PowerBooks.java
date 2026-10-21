package com.powerbooks;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PowerBooks extends JavaPlugin implements Listener {
    private NamespacedKey bookKey;
    private final Map<UUID, Long> powerCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dragonCooldown = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private static final long POWER_CD = 60_000L;
    private static final long DRAGON_CD = 80_000L;

    @Override
    public void onEnable() {
        bookKey = new NamespacedKey(this, "book_type");
        Bukkit.getPluginManager().registerEvents(this, this);

        ShapelessRecipe recipe = new ShapelessRecipe(
                new NamespacedKey(this, "dragon_book_recipe"), makeBook(true));
        recipe.addIngredient(Material.DRAGON_EGG);
        recipe.addIngredient(2, Material.NETHERITE_INGOT);
        recipe.addIngredient(Material.ENCHANTED_GOLDEN_APPLE);
        Bukkit.addRecipe(recipe);

        getLogger().info("PowerBooks enabled!");
    }

    private ItemStack makeBook(boolean dragon) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((dragon ? ChatColor.LIGHT_PURPLE : ChatColor.AQUA)
                + (dragon ? "Dragon Book" : "Power Book"));
        meta.setLore(dragon
                ? List.of(ChatColor.GRAY + "Right-click: Dragon power",
                          ChatColor.GRAY + "Cooldown: 80 seconds")
                : List.of(ChatColor.GRAY + "Right-click: Random positive power",
                          ChatColor.GRAY + "Duration: 45 seconds",
                          ChatColor.GRAY + "Cooldown: 60 seconds"));
        meta.getPersistentDataContainer().set(
                bookKey, PersistentDataType.STRING, dragon ? "dragon" : "power");
        item.setItemMeta(meta);
        return item;
    }

    private boolean isBook(ItemStack item, String type) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK
                || !item.hasItemMeta()) return false;
        String stored = item.getItemMeta().getPersistentDataContainer()
                .get(bookKey, PersistentDataType.STRING);
        return type.equals(stored);
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            player.getInventory().addItem(makeBook(false))
                    .values().forEach(item -> player.getWorld()
                            .dropItemNaturally(player.getLocation(), item));
            player.sendMessage(ChatColor.AQUA + "Welcome! You received a Power Book.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("powerbooks")) return false;
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage("Usage: /powerbooks <power|dragon> [player]");
            return true;
        }
        if (!sender.hasPermission("powerbooks.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /powerbooks <power|dragon> [player]");
            return true;
        }

        boolean dragon;
        if (args[0].equalsIgnoreCase("dragon")) dragon = true;
        else if (args[0].equalsIgnoreCase("power")) dragon = false;
        else {
            sender.sendMessage(ChatColor.RED + "Choose power or dragon.");
            return true;
        }

        Player target;
        if (args.length == 2) target = Bukkit.getPlayerExact(args[1]);
        else target = sender instanceof Player ? (Player) sender : null;

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found. Specify an online player.");
            return true;
        }

        target.getInventory().addItem(makeBook(dragon)).values().forEach(item ->
                target.getWorld().dropItemNaturally(target.getLocation(), item));
        sender.sendMessage(ChatColor.GREEN + "Book given to " + target.getName() + ".");
        return true;
    }

    @EventHandler
    public void onBookUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (isBook(item, "dragon")) {
            event.setCancelled(true);
            activateDragon(player);
        } else if (isBook(item, "power")) {
            event.setCancelled(true);
            activatePower(player);
        }
    }

    private boolean ready(Player player, Map<UUID, Long> cooldown, long duration) {
        long now = System.currentTimeMillis();
        long next = cooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now < next) {
            long seconds = (next - now + 999) / 1000;
            player.sendMessage(ChatColor.RED + "Cooldown: " + seconds + " seconds left.");
            return false;
        }
        cooldown.put(player.getUniqueId(), now + duration);
        return true;
    }

    private void activatePower(Player player) {
        if (!ready(player, powerCooldown, POWER_CD)) return;

        PotionEffectType[] effects = {
            PotionEffectType.SPEED,
            PotionEffectType.STRENGTH,
            PotionEffectType.REGENERATION,
            PotionEffectType.HASTE,
            PotionEffectType.JUMP_BOOST,
            PotionEffectType.RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.WATER_BREATHING,
            PotionEffectType.NIGHT_VISION,
            PotionEffectType.ABSORPTION
        };
        PotionEffectType effect = effects[random.nextInt(effects.length)];
        player.addPotionEffect(new PotionEffect(effect, 45 * 20, 0));
        player.sendMessage(ChatColor.AQUA + "Power Book activated! " +
                ChatColor.YELLOW + effect.getKey().getKey().toUpperCase() +
                ChatColor.GREEN + " for 45 seconds!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
    }

    private void activateDragon(Player player) {
        if (!ready(player, dragonCooldown, DRAGON_CD)) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60 * 20, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60 * 20, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60 * 20, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60 * 20, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60 * 20, 1));

        player.sendMessage(ChatColor.LIGHT_PURPLE + "DRAGON POWER ACTIVATED!");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        if (event.getRecipe() instanceof ShapelessRecipe recipe
                && recipe.getKey().equals(new NamespacedKey(this, "dragon_book_recipe"))) {
            event.getInventory().setResult(makeBook(true));
        }
    }
}
