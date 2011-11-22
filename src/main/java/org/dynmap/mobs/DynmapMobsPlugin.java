package org.dynmap.mobs;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.Marker;

public class DynmapMobsPlugin extends JavaPlugin {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final String LOG_PREFIX = "[dynmap-mobs] ";

    DynmapAPI api;
    MarkerAPI markerapi;
    FileConfiguration cfg;
    MarkerSet set;
    double res; /* Position resolution */
    long updperiod;
    int hideifundercover;
    int hideifshadow;
    
    /* Mapping of mobs to icons */
    private static class MobMapping {
        String mobid;
        boolean enabled;
        Class mobclass;
        String label;
        MarkerIcon icon;
        
        MobMapping(String id, String clsid, String lbl) {
            mobid = id;
            try {
                mobclass = Class.forName(clsid);
            } catch (ClassNotFoundException cnfx) {
                mobclass = null;
            }
            label = lbl;
        }
    };
    MobMapping mobs[] = {
            new MobMapping("blaze", "org.bukkit.entity.Blaze", "Blaze"),
            new MobMapping("enderdragon", "org.bukkit.entity.EnderDragon", "Enderdragon"),
            new MobMapping("ghast", "org.bukkit.entity.EnderDragon", "Ghast"),
            new MobMapping("cow", "org.bukkit.entity.Cow", "Cow"),
            new MobMapping("mooshroom", "org.bukkit.entity.MushroomCow", "Mooshroom"),
            new MobMapping("silverfish", "org.bukkit.entity.Silverfish", "Silverfish"),
            new MobMapping("slime", "org.bukkit.entity.Slime", "Slime"),
            new MobMapping("snowgolem", "org.bukkit.entity.Snowman", "Snow Golem"),
            new MobMapping("cavespider", "org.bukkit.entity.CaveSpider", "Cave Spider"),
            new MobMapping("spider", "org.bukkit.entity.Spider", "Spider"),
            new MobMapping("spiderjockey", "org.bukkit.entity.Spider", "Spider Jockey"), /* Must be just after "spider" */
            new MobMapping("wolf", "org.bukkit.entity.Wolf", "Wolf"),
            new MobMapping("tamedwolf", "org.bukkit.entity.Wolf", "Wolf"), /* Must be just after wolf */
            new MobMapping("zombiepigman", "org.bukkit.entity.PigZombie", "Zombie Pigman"),
            new MobMapping("creeper", "org.bukkit.entity.Creeper", "Creeper"),
            new MobMapping("skeleton", "org.bukkit.entity.Skeleton", "Skeleton"),
            new MobMapping("enderman", "org.bukkit.entity.Enderman", "Enderman"),
            new MobMapping("zombie", "org.bukkit.entity.Zombie", "Zombie"),
            new MobMapping("giant", "org.bukkit.entity.Giant", "Giant"),
            new MobMapping("chicken", "org.bukkit.entity.Chicken", "Chicken"),
            new MobMapping("pig", "org.bukkit.entity.Pig", "Pig"),
            new MobMapping("sheep", "org.bukkit.entity.Sheep", "Sheep"),
            new MobMapping("squid", "org.bukkit.entity.Squid", "Squid"),
            new MobMapping("villager", "org.bukkit.entity.Villager", "Villager")
    };
    
    public static void info(String msg) {
        log.log(Level.INFO, LOG_PREFIX + msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, LOG_PREFIX + msg);
    }

    private class MobUpdate implements Runnable {
        public void run() {
            updateMobs();
        }
    }
    
    private Map<Integer, Marker> mobicons = new HashMap<Integer, Marker>();
    
    /* Update mob population and position */
    private void updateMobs() {
        Map<Integer,Marker> newmap = new HashMap<Integer,Marker>(); /* Build new map */
        
        for(World w : getServer().getWorlds()) {
            for(LivingEntity le : w.getLivingEntities()) {
                int i;
                
                /* See if entity is mob we care about */
                for(i = 0; i < mobs.length; i++) {
                    if((mobs[i].mobclass != null) && mobs[i].mobclass.isInstance(le)){
                        break;
                    }
                }
                if(i >= mobs.length) continue;
                String label = mobs[i].label;
                if(mobs[i].mobid.equals("spider")) {    /* Check for jockey */
                    if(le.getPassenger() != null) { /* Has passenger? */
                        i++;    /* Make jockey */
                        label = mobs[i].label;
                    }
                }
                else if(mobs[i].mobid.equals("wolf")) { /* Check for tamed wolf */
                    Wolf wolf = (Wolf)le;
                    if(wolf.isTamed()) {
                        i++;
                        AnimalTamer t = wolf.getOwner();
                        if((t != null) && (t instanceof OfflinePlayer)) {
                            label = "Wolf (" + ((OfflinePlayer)t).getName() + ")";
                        }
                    }
                }
                if(mobs[i].enabled == false)
                    continue;
                
                Location loc = le.getLocation();

                if(hideifshadow < 15) {
                    if(loc.getBlock().getLightLevel() <= hideifshadow) {
                        continue;
                    }
                }
                if(hideifundercover < 15) {
                    /*TODO: when pull accepted for getSkyLightLevel(), switch to that */
                    if(loc.getWorld().getHighestBlockYAt(loc) > loc.getBlockY()) {
                        continue;
                    }
                }
                
                /* See if we already have marker */
                double x = Math.round(loc.getX() / res) * res;
                double y = Math.round(loc.getY() / res) * res;
                double z = Math.round(loc.getZ() / res) * res;
                Marker m = mobicons.remove(le.getEntityId());
                if(m == null) { /* Not found?  Need new one */
                    m = set.createMarker("mob"+le.getEntityId(), label, w.getName(), x, y, z, mobs[i].icon, false);
                }
                else {  /* Else, update position if needed */
                    m.setLocation(w.getName(), x, y, z);
                    m.setLabel(label);
                    m.setMarkerIcon(mobs[i].icon);
                }
                newmap.put(le.getEntityId(), m);    /* Add to new map */
            }
        }
        /* Now, review old map - anything left is gone */
        for(Marker oldm : mobicons.values()) {
            oldm.deleteMarker();
        }
        /* And replace with new map */
        mobicons = newmap;
        
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MobUpdate(), updperiod);
        
    }
    
    public void onEnable() {
    	info("initializing");
        Plugin p = this.getServer().getPluginManager().getPlugin("dynmap"); /* Find dynmap */
        if(p == null) {
            severe("Error loading Dynmap!");
            return;
        }
        if(!p.isEnabled()) {	/* Make sure it's enabled before us */
        	getServer().getPluginManager().enablePlugin(p);
        	if(!p.isEnabled()) {
        		severe("Failed to enable Dynmap!");
        		return;
        	}
        }
        api = (DynmapAPI)p; /* Get API */
        /* Now, get markers API */
        markerapi = api.getMarkerAPI();
        if(markerapi == null) {
            severe("Error loading Dynmap marker API!");
            return;
        }
        /* Load configuration */
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */
        
        /* Now, add marker set for mobs (make it transient) */
        set = markerapi.createMarkerSet("mobs.markerset", cfg.getString("layer.name", "Mobs"), null, false);
        if(set == null) {
            severe("Error creating marker set");
            return;
        }
        set.setLayerPriority(cfg.getInt("layer.layerprio", 10));
        set.setHideByDefault(cfg.getBoolean("layer.hidebydefault", false));
        /* Get position resolution */
        res = cfg.getDouble("update.resolution", 1.0);

        /* Now, check which mobs are enabled */
        for(int i = 0; i < mobs.length; i++) {
            mobs[i].enabled = cfg.getBoolean("mobs." + mobs[i].mobid, false);
            mobs[i].icon = markerapi.getMarkerIcon("mobs." + mobs[i].mobid);
            if(mobs[i].icon == null) {
                InputStream in = getClass().getResourceAsStream("/" + mobs[i].mobid + ".png");
                if(in != null)
                    mobs[i].icon = markerapi.createMarkerIcon("mobs." + mobs[i].mobid, mobs[i].label, in);
            }
            if(mobs[i].icon == null) {
                mobs[i].icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
        }
        hideifshadow = cfg.getInt("update.hideifshadow", 15);
        hideifundercover = cfg.getInt("update.hideifundercover", 15);
        
        /* Set up update job - based on periond */
        double per = cfg.getDouble("update.period", 5.0);
        if(per < 2.0) per = 2.0;
        updperiod = (long)(per*20.0);
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MobUpdate(), updperiod);
        
        info("version " + this.getDescription().getVersion() + " is enabled");
    }

    public void onDisable() {
        if(set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        mobicons.clear();
        
    }

}
