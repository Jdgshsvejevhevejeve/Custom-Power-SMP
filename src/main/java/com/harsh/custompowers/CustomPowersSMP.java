package com.harsh.custompowers;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

import java.util.*;

public final class CustomPowersSMP extends JavaPlugin implements Listener {
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, Set<String>> powers = new HashMap<>();
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private NamespacedKey powerKey;

    private static final LinkedHashMap<String, String> POWER_NAMES = new LinkedHashMap<>();
    static {
        POWER_NAMES.put("chronos", "Chronos");
        POWER_NAMES.put("mirage", "Mirage");
        POWER_NAMES.put("glitch", "Glitch");
        POWER_NAMES.put("rift", "Rift");
        POWER_NAMES.put("prism", "Prism");
        POWER_NAMES.put("phantom", "Phantom");
        POWER_NAMES.put("mimic", "Mimic");
        POWER_NAMES.put("soul", "Soul");
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        powerKey = new NamespacedKey(this, "power_item");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("power")).setExecutor(this::powerCommand);
        Objects.requireNonNull(getCommand("cpsmp")).setExecutor(this::adminCommand);
        loadData();
        getLogger().info("CustomPowersSMP enabled. 8 powers ready.");
    }

    @Override public void onDisable() { saveData(); }

    private boolean powerCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        ensurePlayer(p);
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            p.sendMessage(ChatColor.LIGHT_PURPLE + "Your powers: " + ChatColor.AQUA + String.join(", ", powers.get(p.getUniqueId())));
            p.sendMessage(ChatColor.GRAY + "Selected: " + selected.get(p.getUniqueId()) + " | Use /power select <name>, then right-click your Power Core.");
            return true;
        }
        if (args[0].equalsIgnoreCase("select") && args.length >= 2) {
            String key = normalize(args[1]);
            if (!powers.get(p.getUniqueId()).contains(key)) { p.sendMessage(ChatColor.RED + "You don't own that power."); return true; }
            selected.put(p.getUniqueId(), key);
            giveCore(p);
            p.sendMessage(ChatColor.GREEN + "Selected " + POWER_NAMES.get(key) + ". Right-click your Power Core to activate.");
            return true;
        }
        if (args[0].equalsIgnoreCase("use")) { activate(p); return true; }
        p.sendMessage(ChatColor.YELLOW + "Usage: /power [list|select <power>|use]");
        return true;
    }

    private boolean adminCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("custompowers.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig(); sender.sendMessage(ChatColor.GREEN + "Config reloaded."); return true;
        }
        sender.sendMessage("/cpsmp reload"); return true;
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p=e.getPlayer(); ensurePlayer(p);
        String url=getConfig().getString("resource-pack-url", "");
        if (url != null && !url.isBlank()) {
            String sha=getConfig().getString("resource-pack-sha1", "");
            try { if (sha != null && sha.matches("(?i)[0-9a-f]{40}")) p.setResourcePack(url, sha); else p.setResourcePack(url); }
            catch (Exception ex) { getLogger().warning("Could not send resource pack: "+ex.getMessage()); }
        }
        p.sendMessage(ChatColor.LIGHT_PURPLE+"Custom Powers SMP "+ChatColor.GRAY+"| Lives: "+lives.get(p.getUniqueId()));
        giveCore(p);
    }

    private void ensurePlayer(Player p) {
        UUID id=p.getUniqueId();
        if (!lives.containsKey(id)) {
            lives.put(id, getConfig().getInt("starting-lives",3));
            String k=new ArrayList<>(POWER_NAMES.keySet()).get(new Random().nextInt(POWER_NAMES.size()));
            powers.put(id,new HashSet<>(Set.of(k))); selected.put(id,k);
            p.sendMessage(ChatColor.GOLD+"Your starting power is "+ChatColor.AQUA+POWER_NAMES.get(k)+ChatColor.GOLD+"!");
        }
        powers.putIfAbsent(id,new HashSet<>());
        selected.putIfAbsent(id, powers.get(id).stream().findFirst().orElse("chronos"));
    }

    private void giveCore(Player p) {
        String key=selected.get(p.getUniqueId());
        if (key==null) return;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it!=null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(powerKey, PersistentDataType.STRING)) return;
        }
        ItemStack item=new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE+"Power Core: "+ChatColor.AQUA+POWER_NAMES.getOrDefault(key,key));
        meta.setCustomModelData(modelData(key));
        meta.getPersistentDataContainer().set(powerKey, PersistentDataType.STRING, key);
        meta.setLore(List.of(ChatColor.GRAY+"Right-click to activate", ChatColor.DARK_GRAY+"Custom Powers SMP"));
        item.setItemMeta(meta); p.getInventory().addItem(item);
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (e.getItem()==null || !e.getItem().hasItemMeta()) return;
        String key=e.getItem().getItemMeta().getPersistentDataContainer().get(powerKey, PersistentDataType.STRING);
        if (key==null) return;
        e.setCancelled(true); activate(e.getPlayer());
    }

    private void activate(Player p) {
        ensurePlayer(p);
        UUID id=p.getUniqueId(); String key=selected.get(id);
        if (key==null || !powers.get(id).contains(key)) { p.sendMessage(ChatColor.RED+"Select a power you own with /power select <name>."); return; }
        long now=System.currentTimeMillis(), cd=getConfig().getLong("cooldown-seconds",25)*1000L;
        long ready=cooldowns.getOrDefault(id,0L);
        if (now<ready) { p.sendMessage(ChatColor.RED+"Cooldown: "+((ready-now+999)/1000)+"s"); return; }
        cooldowns.put(id,now+cd);
        double radius=getConfig().getDouble("ability-radius",8.0);
        switch(key) {
            case "chronos" -> {
                for (Entity en:p.getNearbyEntities(radius,radius,radius)) if(en instanceof Player t && t!=p) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,80,3));
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL,p.getLocation(),90,2,1,2,.2); p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1,0.7f);
            }
            case "mirage" -> {
                for(int i=0;i<3;i++){ Location l=p.getLocation().add((i-1)*1.4,0,1.2); p.getWorld().spawnParticle(Particle.CLOUD,l,35,.4,.8,.4,.03); }
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,50,0));
                p.playSound(p.getLocation(),Sound.ENTITY_ILLUSIONER_MIRROR_MOVE,1,1);
            }
            case "glitch" -> {
                for(Entity en:p.getNearbyEntities(radius,radius,radius)) if(en instanceof Player t && t!=p) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,50,0)); t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,1));
                }
                p.getWorld().spawnParticle(Particle.DUST,new Location(p.getWorld(),p.getLocation().getX(),p.getLocation().getY()+1,p.getLocation().getZ()),100,1,1,1,.2,new Particle.DustOptions(Color.FUCHSIA,1.5f));
            }
            case "rift" -> {
                Vector v=p.getLocation().getDirection().normalize().multiply(8);
                Location dest=p.getLocation().add(v); dest.setY(p.getWorld().getHighestBlockYAt(dest)+1);
                p.getWorld().spawnParticle(Particle.PORTAL,p.getLocation(),100,1,1,1,.4); p.teleport(dest);
                p.getWorld().spawnParticle(Particle.PORTAL,dest,100,1,1,1,.4); p.playSound(dest,Sound.ENTITY_ENDERMAN_TELEPORT,1,1);
            }
            case "prism" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,100,0));
                p.getWorld().spawnParticle(Particle.END_ROD,p.getLocation().add(0,1,0),70,1,1,1,.1);
            }
            case "phantom" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,100,0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,100,1));
                p.getWorld().spawnParticle(Particle.SOUL,p.getLocation(),60,1,1,1,.1);
            }
            case "mimic" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,100,0));
                p.getWorld().spawnParticle(Particle.CRIT,p.getLocation(),80,1,1,1,.2);
                p.sendMessage(ChatColor.GREEN+"Mimic activated! You are disguised by invisibility (armor may remain visible).");
            }
            case "soul" -> {
                for(Entity en:p.getNearbyEntities(radius,radius,radius)) if(en instanceof Player t && t!=p) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,2)); t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,1));
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,80,1));
                p.getWorld().spawnParticle(Particle.SOUL,p.getLocation(),100,2,1,2,.15);
            }
        }
        p.sendMessage(ChatColor.LIGHT_PURPLE+POWER_NAMES.get(key)+" activated!");
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player victim=e.getEntity(), killer=victim.getKiller();
        UUID vid=victim.getUniqueId(); ensurePlayer(victim);
        int left=lives.getOrDefault(vid,3)-1; lives.put(vid,left);
        String lost=selected.get(vid);
        if(lost!=null) powers.get(vid).remove(lost);
        if(killer!=null && killer!=victim) {
            ensurePlayer(killer);
            if(lost!=null) { powers.get(killer.getUniqueId()).add(lost); selected.put(killer.getUniqueId(),lost); giveCore(killer); }
            killer.sendMessage(ChatColor.GOLD+"You stole "+POWER_NAMES.getOrDefault(lost,"a power")+"!");
        }
        victim.sendMessage(ChatColor.RED+"You lost a life. Lives remaining: "+left);
        if(left<=0) {
            Bukkit.getScheduler().runTask(this,()->victim.setGameMode(GameMode.SPECTATOR));
            victim.sendMessage(ChatColor.DARK_RED+"You are eliminated (3 lives lost).");
        } else if(!powers.get(vid).isEmpty()) {
            selected.put(vid,powers.get(vid).iterator().next());
        } else {
            String fresh=new ArrayList<>(POWER_NAMES.keySet()).get(new Random().nextInt(POWER_NAMES.size()));
            powers.get(vid).add(fresh); selected.put(vid,fresh);
            victim.sendMessage(ChatColor.YELLOW+"You received a new power: "+POWER_NAMES.get(fresh));
        }
        saveData();
    }

    private int modelData(String key) { return new ArrayList<>(POWER_NAMES.keySet()).indexOf(key)+1; }
    private String normalize(String s) { return s.toLowerCase(Locale.ROOT).replace("_","").replace("-",""); }
    private void saveData() {
        for(UUID id:lives.keySet()) {
            String path=id.toString();
            getConfig().set("players."+path+".lives",lives.get(id));
            getConfig().set("players."+path+".powers",new ArrayList<>(powers.getOrDefault(id,Set.of())));
            getConfig().set("players."+path+".selected",selected.get(id));
        }
        saveConfig();
    }
    private void loadData() {
        if(!getConfig().isConfigurationSection("players")) return;
        for(String s:getConfig().getConfigurationSection("players").getKeys(false)) try {
            UUID id=UUID.fromString(s);
            lives.put(id,getConfig().getInt("players."+s+".lives",3));
            powers.put(id,new HashSet<>(getConfig().getStringList("players."+s+".powers")));
            selected.put(id,getConfig().getString("players."+s+".selected","chronos"));
        } catch(IllegalArgumentException ignored) {}
    }
}
