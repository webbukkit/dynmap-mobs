package org.dynmap.mobs;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DynmapMobsPlugin extends JavaPlugin {
    private static Logger log;
    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    FileConfiguration cfg;
    MarkerSet mset;
    MarkerSet hset;
    MarkerSet pset;
    MarkerSet vset;
    double res; /* Position resolution */
    long updperiod;
    long vupdperiod;
    int hideifundercover;
    int hideifshadow;
    boolean tinyicons;
    boolean nolabels;
    boolean vtinyicons;
    boolean vnolabels;
    boolean inc_coord;
    boolean vinc_coord;
    boolean stop;
    boolean reload = false;
    static String obcpackage;
    static String nmspackage;
    Method gethandle;
    
    int updates_per_tick = 20;
    int vupdates_per_tick = 20;
    
    HashMap<String, Integer> mlookup_cache = new HashMap<String, Integer>();
    HashMap<String, Integer> hlookup_cache = new HashMap<String, Integer>();
    HashMap<String, Integer> plookup_cache = new HashMap<String, Integer>();
    HashMap<String, Integer> vlookup_cache = new HashMap<String, Integer>();
    
    @Override
    public void onLoad() {
        log = this.getLogger();
    }
    
    public static String mapClassName(String n) {
        if(n.startsWith("org.bukkit.craftbukkit")) {
            n = getOBCPackage() + n.substring("org.bukkit.craftbukkit".length());
        }
        else if(n.startsWith("net.minecraft.server")) {
            n = getNMSPackage() + n.substring("net.minecraft.server".length());
        }
        return n;
    }

    /* Mapping of mobs to icons */
    private static class MobMapping {
        String mobid;
        boolean enabled;
        Class<Entity> mobclass;
        Class<?> entclass;
        String cls_id;
        String entclsid;
        String label;
        MarkerIcon icon;
        
        MobMapping(String id, String clsid, String lbl) {
            this(id, clsid, lbl, null);
        }
        @SuppressWarnings("unchecked")
        MobMapping(String id, String clsid, String lbl, String entclsid) {
            mobid = id;
            label = lbl;
            cls_id = clsid;
        }
        public void init() {
            try {
                mobclass = (Class<Entity>) Class.forName(mapClassName(cls_id));
            } catch (ClassNotFoundException cnfx) {
                mobclass = null;
            }
            try {
                this.entclsid = entclsid;
                if(entclsid != null) {
                    entclass = (Class<?>) Class.forName(mapClassName(entclsid));
                }
            } catch (ClassNotFoundException cnfx) {
                entclass = null;
            }
        }
    };

    MobMapping mocreat_mobs[];
    MobMapping hostile_mobs[];
    MobMapping passive_mobs[];
    MobMapping vehicles[];

    private Map<Integer, Marker> mocreat_mobicons = new HashMap<Integer, Marker>();
    private Map<Integer, Marker> hostile_mobicons = new HashMap<Integer, Marker>();
    private Map<Integer, Marker> passive_mobicons = new HashMap<Integer, Marker>();
    private Map<Integer, Marker> vehicleicons = new HashMap<Integer, Marker>();

    private MobMapping config_mocreat_mobs[] = {
        // Mo'Creatures
        new MobMapping("horse", "org.bukkit.entity.Animals", "Horse", "net.minecraft.server.MoCEntityHorse"),
        new MobMapping("fireogre", "org.bukkit.entity.Monster", "Fire Ogre", "net.minecraft.server.MoCEntityFireOgre"),
        new MobMapping("caveogre", "org.bukkit.entity.Monster", "Cave Ogre", "net.minecraft.server.MoCEntityCaveOgre"),
        new MobMapping("ogre", "org.bukkit.entity.Monster", "Ogre", "net.minecraft.server.MoCEntityOgre"),
        new MobMapping("boar", "org.bukkit.entity.Pig", "Boar", "net.minecraft.server.MoCEntityBoar"),
        new MobMapping("polarbear", "org.bukkit.entity.Animals", "Polar Bear", "net.minecraft.server.MoCEntityPolarBear"),
        new MobMapping("bear", "org.bukkit.entity.Animals", "Bear", "net.minecraft.server.MoCEntityBear"),
        new MobMapping("duck", "org.bukkit.entity.Chicken", "Duck", "net.minecraft.server.MoCEntityDuck"),
        new MobMapping("bigcat", "org.bukkit.entity.Animals", "Big Cat", "net.minecraft.server.MoCEntityBigCat"),
        new MobMapping("deer", "org.bukkit.entity.Animals", "Deer", "net.minecraft.server.MoCEntityDeer"),
        new MobMapping("wildwolf", "org.bukkit.entity.Monster", "Wild Wolf", "net.minecraft.server.MoCEntityWWolf"),
        new MobMapping("flamewraith", "org.bukkit.entity.Monster", "Flame Wraith", "net.minecraft.server.MoCEntityFlameWraith"),
        new MobMapping("wraith", "org.bukkit.entity.Monster", "Wraith", "net.minecraft.server.MoCEntityWraith"),
        new MobMapping("bunny", "org.bukkit.entity.Animals", "Bunny", "net.minecraft.server.MoCEntityBunny"),
        new MobMapping("bird", "org.bukkit.entity.Animals", "Bird", "net.minecraft.server.MoCEntityBird"),
        new MobMapping("fox", "org.bukkit.entity.Animals", "Fox", "net.minecraft.server.MoCEntityFox"),
        new MobMapping("werewolf", "org.bukkit.entity.Monster", "Werewolf", "net.minecraft.server.MoCEntityWerewolf"),
        new MobMapping("shark", "org.bukkit.entity.WaterMob", "Shark", "net.minecraft.server.MoCEntityShark"),
        new MobMapping("dolphin", "org.bukkit.entity.WaterMob", "Shark", "net.minecraft.server.MoCEntityDolphin"),
        new MobMapping("fishy", "org.bukkit.entity.WaterMob", "Fishy", "net.minecraft.server.MoCEntityFishy"),
        new MobMapping("kitty", "org.bukkit.entity.Animals", "Kitty", "net.minecraft.server.MoCEntityKitty"),
        new MobMapping("hellrat", "org.bukkit.entity.Monster", "Hell Rat", "net.minecraft.server.MoCEntityHellRat"),
        new MobMapping("rat", "org.bukkit.entity.Monster", "Rat", "net.minecraft.server.MoCEntityRat"),
        new MobMapping("mouse", "org.bukkit.entity.Animals", "Mouse", "net.minecraft.server.MoCEntityMouse"),
        new MobMapping("scorpion", "org.bukkit.entity.Monster", "Scorpion", "net.minecraft.server.MoCEntityScorpion"),
        new MobMapping("turtle", "org.bukkit.entity.Animals", "Turtle", "net.minecraft.server.MoCEntityTurtle"),
        new MobMapping("crocodile", "org.bukkit.entity.Animals", "Crocodile", "net.minecraft.server.MoCEntityCrocodile"),
        new MobMapping("ray", "org.bukkit.entity.WaterMob", "Ray", "net.minecraft.server.MoCEntityRay"),
        new MobMapping("jellyfish", "org.bukkit.entity.WaterMob", "Jelly Fish", "net.minecraft.server.MoCEntityJellyFish"),
        new MobMapping("goat", "org.bukkit.entity.Animals", "Goat", "net.minecraft.server.MoCEntityGoat"),
        new MobMapping("snake", "org.bukkit.entity.Animals", "Snake", "net.minecraft.server.MoCEntitySnake"),
        new MobMapping("ostrich", "org.bukkit.entity.Animals", "Ostrich", "net.minecraft.server.MoCEntityOstrich")
    };

    private MobMapping config_hostile_mobs[] = {
        // Standard hostile
        new MobMapping("elderguardian", "org.bukkit.entity.ElderGuardian", "Elder Guardian"),
        new MobMapping("witherskeleton", "org.bukkit.entity.WitherSkeleton", "Wither Skeleton"),
        new MobMapping("stray", "org.bukkit.entity.Stray", "Stray"),
        new MobMapping("husk", "org.bukkit.entity.Husk", "Husk"),
        new MobMapping("zombievillager", "org.bukkit.entity.ZombieVillager", "Zombie Villager"),
        new MobMapping("evoker", "org.bukkit.entity.Evoker", "Evoker"),
        new MobMapping("vex", "org.bukkit.entity.Vex", "Vex"),
        new MobMapping("vindicator", "org.bukkit.entity.Vindicator", "Vindicator"),
        new MobMapping("creeper", "org.bukkit.entity.Creeper", "Creeper"),
        new MobMapping("skeleton", "org.bukkit.entity.Skeleton", "Skeleton"),
        new MobMapping("giant", "org.bukkit.entity.Giant", "Giant"),
        new MobMapping("ghast", "org.bukkit.entity.Ghast", "Ghast"),
        new MobMapping("drowned", "org.bukkit.entity.Drowned", "Drowned"),
        new MobMapping("phantom", "org.bukkit.entity.Phantom", "Phantom"),
        new MobMapping("zombiepigman", "org.bukkit.entity.PigZombie", "Zombified Piglin"),
        new MobMapping("zombie", "org.bukkit.entity.Zombie", "Zombie"), /* Must be last zombie type */
        new MobMapping("enderman", "org.bukkit.entity.Enderman", "Enderman"),
        new MobMapping("cavespider", "org.bukkit.entity.CaveSpider", "Cave Spider"),
        new MobMapping("spider", "org.bukkit.entity.Spider", "Spider"), /* Must be last spider type */
        new MobMapping("spiderjockey", "org.bukkit.entity.Spider", "Spider Jockey"), /* Must be just after spider */
        new MobMapping("silverfish", "org.bukkit.entity.Silverfish", "Silverfish"),
        new MobMapping("blaze", "org.bukkit.entity.Blaze", "Blaze"),
        new MobMapping("magmacube", "org.bukkit.entity.MagmaCube", "Magma Cube"),
        new MobMapping("slime", "org.bukkit.entity.Slime", "Slime"), /* Must be last slime type */
        new MobMapping("enderdragon", "org.bukkit.entity.EnderDragon", "Ender Dragon"),
        new MobMapping("wither", "org.bukkit.entity.Wither", "Wither"),
        new MobMapping("witch", "org.bukkit.entity.Witch", "Witch"),
        new MobMapping("endermite", "org.bukkit.entity.Endermite", "Endermite"),
        new MobMapping("guardian", "org.bukkit.entity.Guardian", "Guardian"),
        new MobMapping("shulker", "org.bukkit.entity.Shulker", "Shulker"),
        new MobMapping("ravager", "org.bukkit.entity.Ravager", "Ravager"),
        new MobMapping("illusioner", "org.bukkit.entity.Illusioner", "Illusioner"),
        new MobMapping("pillager", "org.bukkit.entity.Pillager", "Pillager"),
        new MobMapping("piglin", "org.bukkit.entity.Piglin", "Piglin"),
        new MobMapping("hoglin", "org.bukkit.entity.Hoglin", "Hoglin"),
        new MobMapping("zoglin", "org.bukkit.entity.Zoglin", "Zoglin")
    };

    private MobMapping config_passive_mobs[] = {
        // Standard passive
        new MobMapping("skeletonhorse", "org.bukkit.entity.SkeletonHorse", "Skeleton Horse"),
        new MobMapping("zombiehorse", "org.bukkit.entity.ZombieHorse", "Zombie Horse"),
        new MobMapping("donkey", "org.bukkit.entity.Donkey", "Donkey"),
        new MobMapping("mule", "org.bukkit.entity.Mule", "Mule"),
        new MobMapping("bat", "org.bukkit.entity.Bat", "Bat"),
        new MobMapping("pig", "org.bukkit.entity.Pig", "Pig"),
        new MobMapping("sheep", "org.bukkit.entity.Sheep", "Sheep"),
        new MobMapping("cow", "org.bukkit.entity.Cow", "Cow"),
        new MobMapping("chicken", "org.bukkit.entity.Chicken", "Chicken"),
        new MobMapping("chickenjockey", "org.bukkit.entity.Chicken", "Chicken Jockey"), /* Must be just after chicken */
        new MobMapping("squid", "org.bukkit.entity.Squid", "Squid"),
        new MobMapping("wolf", "org.bukkit.entity.Wolf", "Wolf"),
        new MobMapping("tamedwolf", "org.bukkit.entity.Wolf", "Wolf"), /* Must be just after wolf */
        new MobMapping("mooshroom", "org.bukkit.entity.MushroomCow", "Mooshroom"),
        new MobMapping("snowgolem", "org.bukkit.entity.Snowman", "Snow Golem"),
        new MobMapping("ocelot", "org.bukkit.entity.Ocelot", "Ocelot"),
        new MobMapping("cat", "org.bukkit.entity.Cat", "Cat"),
        new MobMapping("golem", "org.bukkit.entity.IronGolem", "Iron Golem"),
        new MobMapping("vanillahorse", "org.bukkit.entity.Horse", "Horse"),
        new MobMapping("rabbit", "org.bukkit.entity.Rabbit", "Rabbit"),
        new MobMapping("vanillapolarbear", "org.bukkit.entity.PolarBear", "Polar Bear"),
        new MobMapping("llama", "org.bukkit.entity.Llama", "Llama"),
        new MobMapping("traderllama", "org.bukkit.entity.TraderLlama", "Trader Llama"),
        new MobMapping("wandering_trader", "org.bukkit.entity.WanderingTrader", "Wandering Trader"),
        new MobMapping("villager", "org.bukkit.entity.Villager", "Villager"),
        new MobMapping("vanilladolphin", "org.bukkit.entity.Dolphin", "Dolphin"),
        new MobMapping("cod", "org.bukkit.entity.Cod", "Cod"),
        new MobMapping("salmon", "org.bukkit.entity.Salmon", "Salmon"),
        new MobMapping("pufferfish", "org.bukkit.entity.PufferFish", "Pufferfish"),
        new MobMapping("tropicalfish", "org.bukkit.entity.TropicalFish", "Tropical Fish"),
        new MobMapping("vanillaturtle", "org.bukkit.entity.Turtle", "Turtle"),
        new MobMapping("parrot", "org.bukkit.entity.Parrot", "Parrot"),
        new MobMapping("panda", "org.bukkit.entity.Panda", "Panda"),
        new MobMapping("vanillafox", "org.bukkit.entity.Fox", "Fox" ),
        new MobMapping("bee", "org.bukkit.entity.Bee", "Bee" ),
        new MobMapping("strider", "org.bukkit.entity.Strider", "Strider")
    };

    private MobMapping config_vehicles[] = {
            // Command Minecart
            new MobMapping("command-minecart", "org.bukkit.entity.minecart.CommandMinecart", "Command Minecart"),
            // Explosive Minecart
            new MobMapping("explosive-minecart", "org.bukkit.entity.minecart.ExplosiveMinecart", "Explosive Minecart"),
            // Hopper Minecart
            new MobMapping("hopper-minecart", "org.bukkit.entity.minecart.HopperMinecart", "Hopper Minecart"),
            // Powered Minecart
            new MobMapping("powered-minecart", "org.bukkit.entity.minecart.PoweredMinecart", "Powered Minecart"),
            // Rideable Minecart
            new MobMapping("minecart", "org.bukkit.entity.minecart.RideableMinecart", "Minecart"),
            // Spawner Minecart
            new MobMapping("spawner-minecart", "org.bukkit.entity.minecart.SpawnerMinecart", "Spawner Minecart"),
            // Storage Minecart
            new MobMapping("storage-minecart", "org.bukkit.entity.minecart.StorageMinecart", "Storage Minecart"),
            // Boat
            new MobMapping("boat", "org.bukkit.entity.Boat", "Boat")
    };
        
    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }
    
    private class MoCreatMobUpdate implements Runnable {
        Map<Integer,Marker> newmap = new HashMap<Integer,Marker>(); /* Build new map */
        ArrayList<World> worldsToDo = null;
        List<LivingEntity> mobsToDo = null;
        int mobIndex = 0;
        World curWorld = null;
        
        public void run() {
            if(stop || mocreat_mobs == null || mocreat_mobs.length == 0 || mset == null ) {
                return;
            }
            // If needed, prime world list
            if (worldsToDo == null) {
                worldsToDo = new ArrayList<World>(getServer().getWorlds());
            }
            while (mobsToDo == null) {
                if (worldsToDo.isEmpty()) {
                    // Now, review old map - anything left is gone
                    for(Marker oldm : mocreat_mobicons.values()) {
                        oldm.deleteMarker();
                    }
                    // And replace with new map
                    mocreat_mobicons = newmap;        
                    // Schedule next run
                    getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, new MoCreatMobUpdate(), updperiod);
                    return;
                }
                else {
                    curWorld = worldsToDo.remove(0); // Get next world
                    mobsToDo = curWorld.getLivingEntities();     // Get living entities
                    mobIndex = 0;
                    if ((mobsToDo != null) && mobsToDo.isEmpty()) {
                        mobsToDo = null;
                    }
                }
            }
            // Process up to limit per tick
            for (int cnt = 0; cnt < updates_per_tick; cnt++) {
                if (mobIndex >= mobsToDo.size()) {
                    mobsToDo = null;
                    break;
                }
                // Get next entity
                LivingEntity le = mobsToDo.get(mobIndex);
                mobIndex++;
                
                int i;
                
                /* See if entity is mob we care about */
                String clsid = null;
                if(gethandle != null) {
                    try {
                        clsid = gethandle.invoke(le).getClass().getName();
                    } catch (Exception x) {
                    }
                }
                
                if(clsid == null)
                    clsid = le.getClass().getName();
                Integer idx = mlookup_cache.get(clsid);
                if(idx == null) {
                    for(i = 0; i < mocreat_mobs.length; i++) {
                        if((mocreat_mobs[i].mobclass != null) && mocreat_mobs[i].mobclass.isInstance(le)){
                            if (mocreat_mobs[i].entclsid == null) {
                                break;
                            }
                            else if(gethandle != null) {
                                Object obcentity = null;
                                try {
                                    obcentity = gethandle.invoke(le);
                                } catch (Exception x) {
                                }
                                if ((mocreat_mobs[i].entclass != null) && (obcentity != null) && (mocreat_mobs[i].entclass.isInstance(obcentity))) {
                                    break;
                                }
                            }
                        }
                    }
                    mlookup_cache.put(clsid, i);
                }
                else {
                    i = idx;
                }
                if(i >= mocreat_mobs.length) {
                    continue;
                }

                String label = null;

                if(i >= mocreat_mobs.length) {
                    continue;
                }
                if(label == null) {
                    label = mocreat_mobs[i].label;
                }

                if (le.getCustomName() != null) {
                    label = le.getCustomName() + " (" + label + ")";
                }

                Location loc = le.getLocation();
                Block blk = null;
                if(hideifshadow < 15) {
                    blk = loc.getBlock();
                    if(blk.getLightLevel() <= hideifshadow) {
                        continue;
                    }
                }
                if(hideifundercover < 15) {
                    if(blk == null) blk = loc.getBlock();
                    if(blk.getLightFromSky() <= hideifundercover) {
                        continue;
                    }
                }
                /* See if we already have marker */
                double x = Math.round(loc.getX() / res) * res;
                double y = Math.round(loc.getY() / res) * res;
                double z = Math.round(loc.getZ() / res) * res;
                Marker m = mocreat_mobicons.remove(le.getEntityId());
                if(nolabels) {
                    label = "";
                }
                else if(inc_coord) {
                    label = label + " [" + (int)x + "," + (int)y + "," + (int)z + "]";
                }
                if(m == null) { /* Not found?  Need new one */
                    m = mset.createMarker("mocreat_mob"+le.getEntityId(), label, curWorld.getName(), x, y, z, mocreat_mobs[i].icon, false);
                }
                else {  /* Else, update position if needed */
                    m.setLocation(curWorld.getName(), x, y, z);
                    m.setLabel(label);
                    m.setMarkerIcon(mocreat_mobs[i].icon);
                }
                if (m != null) {
                    newmap.put(le.getEntityId(), m);    /* Add to new map */
                }
            }
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, this, 1);
        }
    }

    private class HostileMobUpdate implements Runnable {
        Map<Integer,Marker> newmap = new HashMap<Integer,Marker>(); /* Build new map */
        ArrayList<World> worldsToDo = null;
        List<LivingEntity> mobsToDo = null;
        int mobIndex = 0;
        World curWorld = null;
        
        public void run() {
            if(stop || hostile_mobs == null || hostile_mobs.length == 0 || hset == null ) {
                return;
            }
            // If needed, prime world list
            if (worldsToDo == null) {
                worldsToDo = new ArrayList<World>(getServer().getWorlds());
            }
            while (mobsToDo == null) {
                if (worldsToDo.isEmpty()) {
                    // Now, review old map - anything left is gone
                    for(Marker oldm : hostile_mobicons.values()) {
                        oldm.deleteMarker();
                    }
                    // And replace with new map
                    hostile_mobicons = newmap;        
                    // Schedule next run
                    getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, new HostileMobUpdate(), updperiod);
                    return;
                }
                else {
                    curWorld = worldsToDo.remove(0); // Get next world
                    mobsToDo = curWorld.getLivingEntities();     // Get living entities
                    mobIndex = 0;
                    if ((mobsToDo != null) && mobsToDo.isEmpty()) {
                        mobsToDo = null;
                    }
                }
            }
            // Process up to limit per tick
            for (int cnt = 0; cnt < updates_per_tick; cnt++) {
                if (mobIndex >= mobsToDo.size()) {
                    mobsToDo = null;
                    break;
                }
                // Get next entity
                LivingEntity le = mobsToDo.get(mobIndex);
                mobIndex++;
                
                int i;
                
                /* See if entity is mob we care about */
                String clsid = null;
                if(gethandle != null) {
                    try {
                        clsid = gethandle.invoke(le).getClass().getName();
                    } catch (Exception x) {
                    }
                }
                
                if(clsid == null)
                    clsid = le.getClass().getName();
                Integer idx = hlookup_cache.get(clsid);
                if(idx == null) {
                    for(i = 0; i < hostile_mobs.length; i++) {
                        if((hostile_mobs[i].mobclass != null) && hostile_mobs[i].mobclass.isInstance(le)){
                            if (hostile_mobs[i].entclsid == null) {
                                break;
                            }
                            else if(gethandle != null) {
                                Object obcentity = null;
                                try {
                                    obcentity = gethandle.invoke(le);
                                } catch (Exception x) {
                                }
                                if ((hostile_mobs[i].entclass != null) && (obcentity != null) && (hostile_mobs[i].entclass.isInstance(obcentity))) {
                                    break;
                                }
                            }
                        }
                    }
                    hlookup_cache.put(clsid, i);
                }
                else {
                    i = idx;
                }
                if(i >= hostile_mobs.length) {
                    continue;
                }

                String label = null;
                if(hostile_mobs[i].mobid.equals("spider")) {    /* Check for jockey */
                    if(le.getPassenger() != null) { /* Has passenger? */
                        i = findNext(i, "spiderjockey", hostile_mobs);    /* Make jockey */
                    }
                }

                if(i >= hostile_mobs.length) {
                    continue;
                }
                if(label == null) {
                    label = hostile_mobs[i].label;
                }

                if (le.getCustomName() != null) {
                    label = le.getCustomName() + " (" + label + ")";
                }

                Location loc = le.getLocation();
                Block blk = null;
                if(hideifshadow < 15) {
                    blk = loc.getBlock();
                    if(blk.getLightLevel() <= hideifshadow) {
                        continue;
                    }
                }
                if(hideifundercover < 15) {
                    if(blk == null) blk = loc.getBlock();
                    if(blk.getLightFromSky() <= hideifundercover) {
                        continue;
                    }
                }
                /* See if we already have marker */
                double x = Math.round(loc.getX() / res) * res;
                double y = Math.round(loc.getY() / res) * res;
                double z = Math.round(loc.getZ() / res) * res;
                Marker m = hostile_mobicons.remove(le.getEntityId());
                if(nolabels) {
                    label = "";
                }
                else if(inc_coord) {
                    label = label + " [" + (int)x + "," + (int)y + "," + (int)z + "]";
                }
                if(m == null) { /* Not found?  Need new one */
                    m = hset.createMarker("hostile_mob"+le.getEntityId(), label, curWorld.getName(), x, y, z, hostile_mobs[i].icon, false);
                }
                else {  /* Else, update position if needed */
                    m.setLocation(curWorld.getName(), x, y, z);
                    m.setLabel(label);
                    m.setMarkerIcon(hostile_mobs[i].icon);
                }
                if (m != null) {
                    newmap.put(le.getEntityId(), m);    /* Add to new map */
                }
            }
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, this, 1);
        }
    }

    private class PassiveMobUpdate implements Runnable {
        Map<Integer,Marker> newmap = new HashMap<Integer,Marker>(); /* Build new map */
        ArrayList<World> worldsToDo = null;
        List<LivingEntity> mobsToDo = null;
        int mobIndex = 0;
        World curWorld = null;
        
        public void run() {
            if(stop || passive_mobs == null || passive_mobs.length == 0 || pset == null ) {
                return;
            }
            // If needed, prime world list
            if (worldsToDo == null) {
                worldsToDo = new ArrayList<World>(getServer().getWorlds());
            }
            while (mobsToDo == null) {
                if (worldsToDo.isEmpty()) {
                    // Now, review old map - anything left is gone
                    for(Marker oldm : passive_mobicons.values()) {
                        oldm.deleteMarker();
                    }
                    // And replace with new map
                    passive_mobicons = newmap;        
                    // Schedule next run
                    getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, new PassiveMobUpdate(), updperiod);
                    return;
                }
                else {
                    curWorld = worldsToDo.remove(0); // Get next world
                    mobsToDo = curWorld.getLivingEntities();     // Get living entities
                    mobIndex = 0;
                    if ((mobsToDo != null) && mobsToDo.isEmpty()) {
                        mobsToDo = null;
                    }
                }
            }
            // Process up to limit per tick
            for (int cnt = 0; cnt < updates_per_tick; cnt++) {
                if (mobIndex >= mobsToDo.size()) {
                    mobsToDo = null;
                    break;
                }
                // Get next entity
                LivingEntity le = mobsToDo.get(mobIndex);
                mobIndex++;
                
                int i;
                
                /* See if entity is mob we care about */
                String clsid = null;
                if(gethandle != null) {
                    try {
                        clsid = gethandle.invoke(le).getClass().getName();
                    } catch (Exception x) {
                    }
                }
                
                if(clsid == null)
                    clsid = le.getClass().getName();
                Integer idx = plookup_cache.get(clsid);
                if(idx == null) {
                    for(i = 0; i < passive_mobs.length; i++) {
                        if((passive_mobs[i].mobclass != null) && passive_mobs[i].mobclass.isInstance(le)){
                            if (passive_mobs[i].entclsid == null) {
                                break;
                            }
                            else if(gethandle != null) {
                                Object obcentity = null;
                                try {
                                    obcentity = gethandle.invoke(le);
                                } catch (Exception x) {
                                }
                                if ((passive_mobs[i].entclass != null) && (obcentity != null) && (passive_mobs[i].entclass.isInstance(obcentity))) {
                                    break;
                                }
                            }
                        }
                    }
                    plookup_cache.put(clsid, i);
                }
                else {
                    i = idx;
                }
                if(i >= passive_mobs.length) {
                    continue;
                }

                String label = null;
                if(passive_mobs[i].mobid.equals("chicken")) {    /* Check for jockey */
                    if(le.getPassenger() != null) { /* Has passenger? */
                        i = findNext(i, "chickenjockey", passive_mobs);    /* Make jockey , passive_mobs*/
                    }
                }
                else if(passive_mobs[i].mobid.equals("wolf")) { /* Check for tamed wolf */
                    Wolf wolf = (Wolf)le;
                    if(wolf.isTamed()) {
                        i = findNext(i, "tamedwolf", passive_mobs);
                        AnimalTamer t = wolf.getOwner();
                        if((t != null) && (t instanceof OfflinePlayer)) {
                            label = "Wolf (" + ((OfflinePlayer)t).getName() + ")";
                        }
                    }
                }
                else if(passive_mobs[i].mobid.equals("cat")) { /* Check for tamed cat */
                    Cat cat = (Cat)le;
                    if(cat.isTamed()) {
                        i = findNext(i, "cat", passive_mobs);
                        AnimalTamer t = cat.getOwner();
                        if((t != null) && (t instanceof OfflinePlayer)) {
                            label = "Cat (" + ((OfflinePlayer)t).getName() + ")";
                        }
                    }
                }
                else if(passive_mobs[i].mobid.equals("vanillahorse")) { /* Check for tamed horse */
                    Horse horse = (Horse)le;
                    if(horse.isTamed()) {
                        i = findNext(i, "vanillahorse", passive_mobs);
                        AnimalTamer t = horse.getOwner();
                        if((t != null) && (t instanceof OfflinePlayer)) {
                            label = "Horse (" + ((OfflinePlayer)t).getName() + ")";
                        }
                    }
                }
                else if(passive_mobs[i].mobid.equals("traderllama")) { /* Check for tamed traderllama */
                    TraderLlama traderllama = (TraderLlama)le;
                    if(traderllama.isTamed()) {
                        i = findNext(i, "traderllama", passive_mobs);
                        AnimalTamer t = traderllama.getOwner();
                        if((t != null) && (t instanceof OfflinePlayer)) {
                            label = "TraderLlama (" + ((OfflinePlayer)t).getName() + ")";
                        }
                    }
                }
                else if(passive_mobs[i].mobid.equals("llama")) { /* Check for tamed llama */
                    Llama llama = (Llama)le;
                    if(llama.isTamed()) {
                        i = findNext(i, "llama", passive_mobs);
                        AnimalTamer t = llama.getOwner();
                        if((t != null) && (t instanceof OfflinePlayer)) {
                            label = "Llama (" + ((OfflinePlayer)t).getName() + ")";
                        }
                    }
                }
                else if(passive_mobs[i].mobid.equals("parrot")) { /* Check for tamed parrot */
                    Parrot parrot = (Parrot)le;
                    if(parrot.isTamed()) {
                        i = findNext(i, "parrot", passive_mobs);
                        AnimalTamer t = parrot.getOwner();
                        if((t != null) && (t instanceof OfflinePlayer)) {
                            label = "Parrot (" + ((OfflinePlayer)t).getName() + ")";
                        }
                    }
                }
                else if(passive_mobs[i].mobid.equals("villager")) {
                    Villager v = (Villager)le;
                    Profession p = v.getProfession();
                    if(p != null) {
                        switch(p) {
                            case NONE:
                                label = "Villager";
                                break;
                            case ARMORER:
                                label = "Armorer";
                                break;
                            case BUTCHER:
                                label = "Butcher";
                                break;
                            case CARTOGRAPHER:
                                label = "Cartographer";
                                break;
                            case CLERIC:
                                label = "Cleric";
                                break;
                            case FARMER:
                                label = "Farmer";
                                break;
                            case FISHERMAN:
                                label = "Fisherman";
                                break;
                            case FLETCHER:
                                label = "Fletcher";
                                break;
                            case LEATHERWORKER:
                                label = "Leatherworker";
                                break;
                            case LIBRARIAN:
                                label = "Librarian";
                                break;
                            case MASON:
                                label = "Mason";
                                break;
                            case NITWIT:
                                label = "Nitwit";
                                break;
                            case SHEPHERD:
                                label = "Shepherd";
                                break;
                            case TOOLSMITH:
                                label = "Toolsmith";
                                break;
                            case WEAPONSMITH:
                                label = "Weaponsmith";
                                break;
                        }
                    }
                }                
                else if(passive_mobs[i].mobid.equals("vanillahorse")
                	 || passive_mobs[i].mobid.equals("llama")
                	 || passive_mobs[i].mobid.equals("traderllama")
                	 || passive_mobs[i].mobid.equals("donkey")
                	 || passive_mobs[i].mobid.equals("mule")
                	 || passive_mobs[i].mobid.equals("zombiehorse")
                	 || passive_mobs[i].mobid.equals("skeletonhorse")) {    /* Check for rider */
                    if(le.getPassenger() != null) { /* Has passenger? */
                        Entity e = le.getPassenger();
                        if (e instanceof Player) {
                            label = label + " (" + ((Player)e).getName() + ")";
                        }
                    }
                }

                if(i >= passive_mobs.length) {
                    continue;
                }
                if(label == null) {
                    label = passive_mobs[i].label;
                }

                if (le.getCustomName() != null) {
                    label = le.getCustomName() + " (" + label + ")";
                }

                Location loc = le.getLocation();
                Block blk = null;
                if(hideifshadow < 15) {
                    blk = loc.getBlock();
                    if(blk.getLightLevel() <= hideifshadow) {
                        continue;
                    }
                }
                if(hideifundercover < 15) {
                    if(blk == null) blk = loc.getBlock();
                    if(blk.getLightFromSky() <= hideifundercover) {
                        continue;
                    }
                }
                /* See if we already have marker */
                double x = Math.round(loc.getX() / res) * res;
                double y = Math.round(loc.getY() / res) * res;
                double z = Math.round(loc.getZ() / res) * res;
                Marker m = passive_mobicons.remove(le.getEntityId());
                if(nolabels) {
                    label = "";
                }
                else if(inc_coord) {
                    label = label + " [" + (int)x + "," + (int)y + "," + (int)z + "]";
                }
                if(m == null) { /* Not found?  Need new one */
                    m = pset.createMarker("passive_mob"+le.getEntityId(), label, curWorld.getName(), x, y, z, passive_mobs[i].icon, false);
                }
                else {  /* Else, update position if needed */
                    m.setLocation(curWorld.getName(), x, y, z);
                    m.setLabel(label);
                    m.setMarkerIcon(passive_mobs[i].icon);
                }
                if (m != null) {
                    newmap.put(le.getEntityId(), m);    /* Add to new map */
                }
            }
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, this, 1);
        }
    }

    private class VehicleUpdate implements Runnable {
        Map<Integer,Marker> newmap = new HashMap<Integer,Marker>(); /* Build new map */
        ArrayList<World> worldsToDo = null;
        List<Entity> vehiclesToDo = null;
        int vehiclesIndex = 0;
        World curWorld = null;

        public void run() {
            if(stop || (vehicles == null) || (vehicles.length == 0) || (vset == null)) {
                return;
            }
            // If needed, prime world list
            if (worldsToDo == null) {
                worldsToDo = new ArrayList<World>(getServer().getWorlds());
            }
            while (vehiclesToDo == null) {
                if (worldsToDo.isEmpty()) {
                    // Now, review old map - anything left is gone
                    for(Marker oldm : vehicleicons.values()) {
                        oldm.deleteMarker();
                    }
                    // And replace with new map
                    vehicleicons = newmap;        
                    // Schedule next run
                    getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, new VehicleUpdate(), vupdperiod);
                    return;
                }
                else {
                    curWorld = worldsToDo.remove(0); // Get next world
                    vehiclesToDo = new ArrayList<Entity>(curWorld.getEntitiesByClasses(org.bukkit.entity.Vehicle.class)); // Get vehicles
                    vehiclesIndex = 0;
                    if ((vehiclesToDo != null) && vehiclesToDo.isEmpty()) {
                        vehiclesToDo = null;
                    }
                }
            }
            // Process up to limit per tick
            for (int cnt = 0; cnt < vupdates_per_tick; cnt++) {
                if (vehiclesIndex >= vehiclesToDo.size()) {
                    vehiclesToDo = null;
                    break;
                }
                // Get next entity
                Entity le = vehiclesToDo.get(vehiclesIndex);
                vehiclesIndex++;
                
                int i;
                /* See if entity is vehicle we care about */
                String clsid = null;
                if(gethandle != null) {
                    try {
                        clsid = gethandle.invoke(le).getClass().getName();
                    } catch (Exception x) {
                    }
                }
                if(clsid == null)
                    clsid = le.getClass().getName();
                Integer idx = vlookup_cache.get(clsid);
                if(idx == null) {
                    for(i = 0; i < vehicles.length; i++) {
                        if((vehicles[i].mobclass != null) && vehicles[i].mobclass.isInstance(le)){
                            if (vehicles[i].entclsid == null) {
                                break;
                            }
                            else if(gethandle != null) {
                                Object obcentity = null;
                                try {
                                    obcentity = gethandle.invoke(le);
                                } catch (Exception x) {
                                }
                                if ((vehicles[i].entclass != null) && (obcentity != null) && (vehicles[i].entclass.isInstance(obcentity))) {
                                    break;
                                }
                            }
                        }
                    }
                    vlookup_cache.put(clsid,  i);
                }
                else {
                    i = idx;
                }
                if(i >= vehicles.length) {
                    continue;
                }
                String label = null;
                if(i >= vehicles.length) {
                    continue;
                }
                if(label == null) {
                    label = vehicles[i].label;
                }
                Location loc = le.getLocation();
                if(curWorld.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4) == false) {
                    continue;
                }
                Block blk = null;
                if(hideifshadow < 15) {
                    blk = loc.getBlock();
                    if(blk.getLightLevel() <= hideifshadow) {
                        continue;
                    }
                }
                if(hideifundercover < 15) {
                    if(blk == null) blk = loc.getBlock();
                    if(blk.getLightFromSky() <= hideifundercover) {
                        continue;
                    }
                }
                /* See if we already have marker */
                double x = Math.round(loc.getX() / res) * res;
                double y = Math.round(loc.getY() / res) * res;
                double z = Math.round(loc.getZ() / res) * res;
                Marker m = vehicleicons.remove(le.getEntityId());
                if(vnolabels)
                    label = "";
                else if(vinc_coord) {
                    label = label + " [" + (int)x + "," + (int)y + "," + (int)z + "]";
                }
                if(m == null) { /* Not found?  Need new one */
                    m = vset.createMarker("vehicle"+le.getEntityId(), label, curWorld.getName(), x, y, z, vehicles[i].icon, false);
                }
                else {  /* Else, update position if needed */
                    m.setLocation(curWorld.getName(), x, y, z);
                    m.setLabel(label);
                    m.setMarkerIcon(vehicles[i].icon);
                }
                newmap.put(le.getEntityId(), m);    /* Add to new map */
            }
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, this, 1);
        }
    }



    private int findNext(int idx, String mobid, MobMapping[] mobs) {
        idx++;
        if ((idx < mobs.length) && mobs[idx].mobid.equals(mobid)) {
            return idx;
        }
        else {
            return mobs.length;
        }
    }

    private class OurServerListener implements Listener {
        @EventHandler(priority=EventPriority.MONITOR)
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if(name.equals("dynmap")) {
                activate();
            }
        }
    }
    
    public void onEnable() {
        info("initializing");
        PluginManager pm = getServer().getPluginManager();
        /* Get dynmap */
        dynmap = pm.getPlugin("dynmap");
        if(dynmap == null) {
            severe("Cannot find dynmap!");
            return;
        }
        api = (DynmapAPI)dynmap; /* Get API */

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);        

        /* If enabled, activate */
        if(dynmap.isEnabled())
            activate();
        
        try {
            MetricsLite ml = new MetricsLite(this);
            ml.start();
        } catch (IOException iox) {
        }
    }

    private static String getNMSPackage() {
        if (nmspackage == null) {
            Server srv = Bukkit.getServer();
            /* Get getHandle() method */
            try {
                Method m = srv.getClass().getMethod("getHandle");
                Object scm = m.invoke(srv); /* And use it to get SCM (nms object) */
                nmspackage = scm.getClass().getPackage().getName();
            } catch (Exception x) {
                nmspackage = "net.minecraft.server";
            }
        }
        return nmspackage;
    }
    private static String getOBCPackage() {
        if (obcpackage == null) {
            obcpackage = Bukkit.getServer().getClass().getPackage().getName();
        }
        return obcpackage;
    }

    private void activate() {
        /* look up the getHandle method for CraftEntity */
        try {
            Class<?> cls = Class.forName(mapClassName("org.bukkit.craftbukkit.entity.CraftEntity"));
            gethandle = cls.getMethod("getHandle");
        } catch (ClassNotFoundException cnfx) {
        } catch (NoSuchMethodException e) {
        } catch (SecurityException e) {
        }
        if(gethandle == null) {
            severe("Unable to locate CraftEntity.getHandle() - cannot process most Mo'Creatures mobs");
        }
        
        /* Now, get markers API */
        markerapi = api.getMarkerAPI();
        if(markerapi == null) {
            severe("Error loading Dynmap marker API!");
            return;
        }
        /* Load configuration */
        if(reload) {
            reloadConfig();
            if(mset != null)  {
                mset.deleteMarkerSet();
                mset = null;
            }
            if(hset != null)  {
                hset.deleteMarkerSet();
                hset = null;
            }
            if(pset != null)  {
                pset.deleteMarkerSet();
                pset = null;
            }
            if(vset != null)  {
                vset.deleteMarkerSet();
                vset = null;
            }
            mocreat_mobicons.clear();
            hostile_mobicons.clear();
            passive_mobicons.clear();
            vehicleicons.clear();
            mlookup_cache.clear();
            hlookup_cache.clear();
            plookup_cache.clear();
            vlookup_cache.clear();
        }
        else {
            reload = true;
        }
        this.saveDefaultConfig();
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */
        
        /* Now, check which mo'creatures mobs are enabled */
        Set<Class<Entity>> clsset = new HashSet<Class<Entity>>();
        int cnt = 0;
        for(int i = 0; i < config_mocreat_mobs.length; i++) {
            config_mocreat_mobs[i].init();
            config_mocreat_mobs[i].enabled = cfg.getBoolean("mocreat_mobs." + config_mocreat_mobs[i].mobid, false);
            config_mocreat_mobs[i].icon = markerapi.getMarkerIcon("mocreat_mobs." + config_mocreat_mobs[i].mobid);
            InputStream in = null;
            if(tinyicons)
                in = getClass().getResourceAsStream("/8x8/" + config_mocreat_mobs[i].mobid + ".png");
            if(in == null)
                in = getClass().getResourceAsStream("/" + config_mocreat_mobs[i].mobid + ".png");
            if(in != null) {
                if(config_mocreat_mobs[i].icon == null)
                    config_mocreat_mobs[i].icon = markerapi.createMarkerIcon("mocreat_mobs." + config_mocreat_mobs[i].mobid, config_mocreat_mobs[i].label, in);
                else    /* Update image */
                    config_mocreat_mobs[i].icon.setMarkerIconImage(in);
            }
            if(config_mocreat_mobs[i].icon == null) {
                config_mocreat_mobs[i].icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
            if(config_mocreat_mobs[i].enabled) {
                cnt++;
            }
        }
        /* Make list of just enabled mobs */
        mocreat_mobs = new MobMapping[cnt];
        for(int i = 0, j = 0; i < config_mocreat_mobs.length; i++) {
            if(config_mocreat_mobs[i].enabled) {
                mocreat_mobs[j] = config_mocreat_mobs[i];
                j++;
                clsset.add(config_mocreat_mobs[i].mobclass);
            }
        }

        hideifshadow = cfg.getInt("update.hideifshadow", 15);
        hideifundercover = cfg.getInt("update.hideifundercover", 15);
        /* Now, add marker set for mobs (make it transient) */
        if(mocreat_mobs.length > 0) {
            mset = markerapi.getMarkerSet("mocreat_mobs.markerset");
            if(mset == null)
                mset = markerapi.createMarkerSet("mocreat_mobs.markerset", cfg.getString("mocreatlayer.name", "Mo'Creatures Mobs"), null, false);
            else
                mset.setMarkerSetLabel(cfg.getString("mocreatlayer.name", "Mo'Creatures Mobs"));
            if(mset == null) {
                severe("Error creating marker set");
                return;
            }
            mset.setLayerPriority(cfg.getInt("mocreatlayer.layerprio", 10));
            mset.setHideByDefault(cfg.getBoolean("mocreatlayer.hidebydefault", false));
            int minzoom = cfg.getInt("mocreatlayer.minzoom", 0);
            if(minzoom > 0) /* Don't call if non-default - lets us work with pre-0.28 dynmap */
                mset.setMinZoom(minzoom);
            tinyicons = cfg.getBoolean("mocreatlayer.tinyicons", false);
            nolabels = cfg.getBoolean("mocreatlayer.nolabels", false);
            inc_coord = cfg.getBoolean("mocreatlayer.inc-coord", false);
            /* Get position resolution */
            res = cfg.getDouble("update.resolution", 1.0);
            /* Set up update job - based on period */
            double per = cfg.getDouble("update.period", 5.0);
            if(per < 2.0) per = 2.0;
            updperiod = (long)(per*20.0);
            updates_per_tick = cfg.getInt("update.mobs-per-tick", 20);
            stop = false;
            getServer().getScheduler().scheduleSyncDelayedTask(this, new MoCreatMobUpdate(), updperiod);
            info("Enable layer for mo'creatures mobs");
        }
        else {
            info("Layer for mo'creatures mobs disabled");
        }



        /* Now, check which hostile mobs are enabled */
        clsset = new HashSet<Class<Entity>>();
        cnt = 0;
        for(int i = 0; i < config_hostile_mobs.length; i++) {
            config_hostile_mobs[i].init();
            config_hostile_mobs[i].enabled = cfg.getBoolean("hostile_mobs." + config_hostile_mobs[i].mobid, false);
            config_hostile_mobs[i].icon = markerapi.getMarkerIcon("hostile_mobs." + config_hostile_mobs[i].mobid);
            InputStream in = null;
            if(tinyicons)
                in = getClass().getResourceAsStream("/8x8/" + config_hostile_mobs[i].mobid + ".png");
            if(in == null)
                in = getClass().getResourceAsStream("/" + config_hostile_mobs[i].mobid + ".png");
            if(in != null) {
                if(config_hostile_mobs[i].icon == null)
                    config_hostile_mobs[i].icon = markerapi.createMarkerIcon("hostile_mobs." + config_hostile_mobs[i].mobid, config_hostile_mobs[i].label, in);
                else    /* Update image */
                    config_hostile_mobs[i].icon.setMarkerIconImage(in);
            }
            if(config_hostile_mobs[i].icon == null) {
                config_hostile_mobs[i].icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
            if(config_hostile_mobs[i].enabled) {
                cnt++;
            }
        }
        /* Make list of just enabled mobs */
        hostile_mobs = new MobMapping[cnt];
        for(int i = 0, j = 0; i < config_hostile_mobs.length; i++) {
            if(config_hostile_mobs[i].enabled) {
                hostile_mobs[j] = config_hostile_mobs[i];
                j++;
                clsset.add(config_hostile_mobs[i].mobclass);
            }
        }

        hideifshadow = cfg.getInt("update.hideifshadow", 15);
        hideifundercover = cfg.getInt("update.hideifundercover", 15);
        /* Now, add marker set for mobs (make it transient) */
        if(hostile_mobs.length > 0) {
            hset = markerapi.getMarkerSet("hostile_mobs.markerset");
            if(hset == null)
                hset = markerapi.createMarkerSet("hostile_mobs.markerset", cfg.getString("hostilelayer.name", "Mo'Creatures Mobs"), null, false);
            else
                hset.setMarkerSetLabel(cfg.getString("hostilelayer.name", "Mo'Creatures Mobs"));
            if(hset == null) {
                severe("Error creating marker set");
                return;
            }
            hset.setLayerPriority(cfg.getInt("hostilelayer.layerprio", 10));
            hset.setHideByDefault(cfg.getBoolean("hostilelayer.hidebydefault", false));
            int minzoom = cfg.getInt("hostilelayer.minzoom", 0);
            if(minzoom > 0) /* Don't call if non-default - lets us work with pre-0.28 dynmap */
                hset.setMinZoom(minzoom);
            tinyicons = cfg.getBoolean("hostilelayer.tinyicons", false);
            nolabels = cfg.getBoolean("hostilelayer.nolabels", false);
            inc_coord = cfg.getBoolean("hostilelayer.inc-coord", false);
            /* Get position resolution */
            res = cfg.getDouble("update.resolution", 1.0);
            /* Set up update job - based on period */
            double per = cfg.getDouble("update.period", 5.0);
            if(per < 2.0) per = 2.0;
            updperiod = (long)(per*20.0);
            updates_per_tick = cfg.getInt("update.mobs-per-tick", 20);
            stop = false;
            getServer().getScheduler().scheduleSyncDelayedTask(this, new HostileMobUpdate(), updperiod);
            info("Enable layer for hostile mobs");
        }
        else {
            info("Layer for hostile mobs disabled");
        }



        /* Now, check which passive mobs are enabled */
        clsset = new HashSet<Class<Entity>>();
        cnt = 0;
        for(int i = 0; i < config_passive_mobs.length; i++) {
            config_passive_mobs[i].init();
            config_passive_mobs[i].enabled = cfg.getBoolean("passive_mobs." + config_passive_mobs[i].mobid, false);
            config_passive_mobs[i].icon = markerapi.getMarkerIcon("passive_mobs." + config_passive_mobs[i].mobid);
            InputStream in = null;
            if(tinyicons)
                in = getClass().getResourceAsStream("/8x8/" + config_passive_mobs[i].mobid + ".png");
            if(in == null)
                in = getClass().getResourceAsStream("/" + config_passive_mobs[i].mobid + ".png");
            if(in != null) {
                if(config_passive_mobs[i].icon == null)
                    config_passive_mobs[i].icon = markerapi.createMarkerIcon("passive_mobs." + config_passive_mobs[i].mobid, config_passive_mobs[i].label, in);
                else    /* Update image */
                    config_passive_mobs[i].icon.setMarkerIconImage(in);
            }
            if(config_passive_mobs[i].icon == null) {
                config_passive_mobs[i].icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
            if(config_passive_mobs[i].enabled) {
                cnt++;
            }
        }
        /* Make list of just enabled mobs */
        passive_mobs = new MobMapping[cnt];
        for(int i = 0, j = 0; i < config_passive_mobs.length; i++) {
            if(config_passive_mobs[i].enabled) {
                passive_mobs[j] = config_passive_mobs[i];
                j++;
                clsset.add(config_passive_mobs[i].mobclass);
            }
        }

        hideifshadow = cfg.getInt("update.hideifshadow", 15);
        hideifundercover = cfg.getInt("update.hideifundercover", 15);
        /* Now, add marker set for mobs (make it transient) */
        if(passive_mobs.length > 0) {
            pset = markerapi.getMarkerSet("passive_mobs.markerset");
            if(pset == null)
                pset = markerapi.createMarkerSet("passive_mobs.markerset", cfg.getString("passivelayer.name", "Mo'Creatures Mobs"), null, false);
            else
                pset.setMarkerSetLabel(cfg.getString("passivelayer.name", "Mo'Creatures Mobs"));
            if(pset == null) {
                severe("Error creating marker set");
                return;
            }
            pset.setLayerPriority(cfg.getInt("passivelayer.layerprio", 10));
            pset.setHideByDefault(cfg.getBoolean("passivelayer.hidebydefault", false));
            int minzoom = cfg.getInt("passivelayer.minzoom", 0);
            if(minzoom > 0) /* Don't call if non-default - lets us work with pre-0.28 dynmap */
                pset.setMinZoom(minzoom);
            tinyicons = cfg.getBoolean("passivelayer.tinyicons", false);
            nolabels = cfg.getBoolean("passivelayer.nolabels", false);
            inc_coord = cfg.getBoolean("passivelayer.inc-coord", false);
            /* Get position resolution */
            res = cfg.getDouble("update.resolution", 1.0);
            /* Set up update job - based on period */
            double per = cfg.getDouble("update.period", 5.0);
            if(per < 2.0) per = 2.0;
            updperiod = (long)(per*20.0);
            updates_per_tick = cfg.getInt("update.mobs-per-tick", 20);
            stop = false;
            getServer().getScheduler().scheduleSyncDelayedTask(this, new PassiveMobUpdate(), updperiod);
            info("Enable layer for passive mobs");
        }
        else {
            info("Layer for passive mobs disabled");
        }


        /* Now, check which vehicles are enabled */
        clsset = new HashSet<Class<Entity>>();
        cnt = 0;
        for(int i = 0; i < config_vehicles.length; i++) {
            config_vehicles[i].init();
            config_vehicles[i].enabled = cfg.getBoolean("vehicles." + config_vehicles[i].mobid, false);
            config_vehicles[i].icon = markerapi.getMarkerIcon("vehicles." + config_vehicles[i].mobid);
            InputStream in = null;
            if(tinyicons)
                in = getClass().getResourceAsStream("/8x8/" + config_vehicles[i].mobid + ".png");
            if(in == null)
                in = getClass().getResourceAsStream("/" + config_vehicles[i].mobid + ".png");
            if(in != null) {
                if(config_vehicles[i].icon == null)
                    config_vehicles[i].icon = markerapi.createMarkerIcon("vehicles." + config_vehicles[i].mobid, config_vehicles[i].label, in);
                else    /* Update image */
                    config_vehicles[i].icon.setMarkerIconImage(in);
            }
            if(config_vehicles[i].icon == null) {
                config_vehicles[i].icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
            if(config_vehicles[i].enabled) {
                cnt++;
            }
        }
        /* Make list of just enabled vehicles */
        vehicles = new MobMapping[cnt];
        for(int i = 0, j = 0; i < config_vehicles.length; i++) {
            if(config_vehicles[i].enabled) {
                vehicles[j] = config_vehicles[i];
                j++;
                clsset.add(config_vehicles[i].mobclass);
            }
        }
        /* Now, add marker set for vehicles (make it transient) */
        if(vehicles.length > 0) {
            vset = markerapi.getMarkerSet("vehicles.markerset");
            if(vset == null)
                vset = markerapi.createMarkerSet("vehicles.markerset", cfg.getString("vehiclelayer.name", "Vehicles"), null, false);
            else
                vset.setMarkerSetLabel(cfg.getString("vehiclelayer.name", "Vehicles"));
            if(vset == null) {
                severe("Error creating marker set");
                return;
            }
            vset.setLayerPriority(cfg.getInt("vehiclelayer.layerprio", 10));
            vset.setHideByDefault(cfg.getBoolean("vehiclelayer.hidebydefault", false));
            int minzoom = cfg.getInt("vehiclelayer.minzoom", 0);
            if(minzoom > 0) /* Don't call if non-default - lets us work with pre-0.28 dynmap */
                vset.setMinZoom(minzoom);
            vtinyicons = cfg.getBoolean("vehiclelayer.tinyicons", false);
            vnolabels = cfg.getBoolean("vehiclelayer.nolabels", false);
            vinc_coord = cfg.getBoolean("vehiclelayer.inc-coord", false);
            /* Get position resolution */
            res = cfg.getDouble("update.resolution", 1.0);
            /* Set up update job - based on period */
            double per = cfg.getDouble("update.vehicleperiod", 5.0);
            if(per < 2.0) per = 2.0;
            vupdperiod = (long)(per*20.0);
            vupdates_per_tick = cfg.getInt("update.vehicles-per-tick", 20);
            stop = false;
            getServer().getScheduler().scheduleSyncDelayedTask(this, new VehicleUpdate(), vupdperiod / 3);
            info("Enable layer for vehicles");
        }
        else {
            info("Layer for vehicles disabled");
        }

        info("version " + this.getDescription().getVersion() + " is activated");
    }

    public void onDisable() {
        if(mset != null) {
            mset.deleteMarkerSet();
            mset = null;
        }
        if(hset != null) {
            hset.deleteMarkerSet();
            hset = null;
        }
        if(pset != null) {
            pset.deleteMarkerSet();
            pset = null;
        }
        if(vset != null) {
            vset.deleteMarkerSet();
            vset = null;
        }
        mocreat_mobicons.clear();
        hostile_mobicons.clear();
        passive_mobicons.clear();
        vehicleicons.clear();
        mlookup_cache.clear();
        hlookup_cache.clear();
        plookup_cache.clear();
        vlookup_cache.clear();
        stop = true;
    }

}
