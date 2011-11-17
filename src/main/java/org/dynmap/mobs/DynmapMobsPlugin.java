package org.dynmap.mobs;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
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
        
        MobMapping(String id, Class cls, String lbl) {
            mobid = id;
            mobclass = cls;
            label = lbl;
        }
    };
    MobMapping mobs[] = {
            //new MobMapping("blaze", Blaze.class, "Blaze"),
            //new MobMapping("enderdragon", EnderDragon.class, "Enderdragon"),
            new MobMapping("ghast", Ghast.class, "Ghast"),
            //new MobMapping("mooshroom", Mooshroom.class, "Mooshroom"),
            new MobMapping("silverfish", Silverfish.class, "Silverfish"),
            new MobMapping("slime", Slime.class, "Slime"),
            //new MobMapping("snowgolem", SnowGolem.class, "Snow Golem"),
            new MobMapping("cavespider", CaveSpider.class, "Cave Spider"),
            new MobMapping("spider", Spider.class, "Spider"),
            //new MobMapping("spiderjockey", Spider.class, "Spider"),
            new MobMapping("wolf", Wolf.class, "Wolf"),
            new MobMapping("zombiepigman", PigZombie.class, "Zombie Pigman"),
            new MobMapping("creeper", Creeper.class, "Creeper"),
            new MobMapping("skeleton", Skeleton.class, "Skeleton"),
            new MobMapping("enderman", Enderman.class, "Enderman"),
            new MobMapping("zombie", Zombie.class, "Zombie")
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
                    if(mobs[i].enabled && mobs[i].mobclass.isInstance(le)){
                        break;
                    }
                }
                if(i >= mobs.length) continue;
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
                    m = set.createMarker("mob"+le.getEntityId(), mobs[i].label, w.getName(), x, y, z, mobs[i].icon, false);
                }
                else {  /* Else, update position if needed */
                    m.setLocation(w.getName(), x, y, z);
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
        Plugin p = this.getServer().getPluginManager().getPlugin("dynmap"); /* Find dynmap */
        if(p == null) {
            severe("Error loading dynmap API!");
            return;
        }
        api = (DynmapAPI)p; /* Get API */
        /* Now, get markers API */
        markerapi = api.getMarkerAPI();
        if(markerapi == null) {
            severe("Error loading dynmap marker API!");
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
        
        info("Version " + this.getDescription().getVersion() + " enabled");
    }

    public void onDisable() {
        if(set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        mobicons.clear();
        
    }

}
