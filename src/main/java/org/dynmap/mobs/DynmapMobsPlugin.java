package org.dynmap.mobs;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Horse.Variant;
import org.bukkit.entity.Skeleton.SkeletonType;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.Marker;

public class DynmapMobsPlugin extends JavaPlugin {
    private static Logger log;
    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    FileConfiguration cfg;
    MarkerSet set;
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
    
    HashMap<String, Integer> lookup_cache = new HashMap<String, Integer>();
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
    MobMapping mobs[];

    private MobMapping configmobs[] = {
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
            new MobMapping("flamewraith", "org.bukkit.entity.Monster", "Wraith", "net.minecraft.server.MoCEntityFlameWraith"),
            new MobMapping("wraith", "org.bukkit.entity.Monster", "Wraith", "net.minecraft.server.MoCEntityWraith"),
            new MobMapping("bunny", "org.bukkit.entity.Animals", "Bunny", "net.minecraft.server.MoCEntityBunny"),
            new MobMapping("bird", "org.bukkit.entity.Animals", "Bird", "net.minecraft.server.MoCEntityBird"),
            new MobMapping("fox", "org.bukkit.entity.Animals", "Bird", "net.minecraft.server.MoCEntityFox"),
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
            new MobMapping("ostrich", "org.bukkit.entity.Animals", "Ostrich", "net.minecraft.server.MoCEntityOstrich"),
            // Standard hostile
            new MobMapping("elderguardian", "org.bukkit.entity.ElderGuardian", "Elder Guardian"),
            new MobMapping("witherskeleton", "org.bukkit.entity.WitherSkeleton", "Wither Skeleton"),
            new MobMapping("stray", "org.bukkit.entity.Stray", "Stray"),
            new MobMapping("husk", "org.bukkit.entity.Husk", "Husk"),
            new MobMapping("zombievillager", "org.bukkit.entity.ZombieVillager", "Zombie Villager"),
            new MobMapping("evoker", "org.bukkit.entity.Evoker", "Evoker"),
            new MobMapping("vex", "org.bukkit.entity.Vex", "Vex"),
            new MobMapping("vindicator", "org.bukkit.entity.Vindicator", "Vindicator"),
            new MobMapping("illusioner", "org.bukkit.entity.Illusioner", "Illusioner"),
            new MobMapping("creeper", "org.bukkit.entity.Creeper", "Creeper"),
            new MobMapping("skeleton", "org.bukkit.entity.Skeleton", "Skeleton"),
            new MobMapping("giant", "org.bukkit.entity.Giant", "Giant"),
            new MobMapping("ghast", "org.bukkit.entity.Ghast", "Ghast"),
            new MobMapping("zombiepigman", "org.bukkit.entity.PigZombie", "Zombie Pigman"),
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
            new MobMapping("cat", "org.bukkit.entity.Ocelot", "Cat"), /* Must be just after ocelot */
            new MobMapping("golem", "org.bukkit.entity.IronGolem", "Iron Golem"),
            new MobMapping("vanillahorse", "org.bukkit.entity.Horse", "Horse"),
            new MobMapping("rabbit", "org.bukkit.entity.Rabbit", "Rabbit"),
            new MobMapping("vanillapolarbear", "org.bukkit.entity.PolarBear", "Polar Bear"),
            new MobMapping("llama", "org.bukkit.entity.Llama", "Llama"),
            new MobMapping("parrot", "org.bukkit.entity.Parrot", "Parrot"),
            new MobMapping("villager", "org.bukkit.entity.Villager", "Villager")
    };
    private MobMapping configvehicles[] = {
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
    MobMapping vehicles[];
    
    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }
    
    private class MobUpdate implements Runnable {
        Map<Integer,Marker> newmap = new HashMap<Integer,Marker>(); /* Build new map */
        ArrayList<World> worldsToDo = null;
        List<LivingEntity> mobsToDo = null;
        int mobIndex = 0;
        World curWorld = null;
        
        public void run() {
            if(stop || (mobs == null) || (mobs.length == 0) || (set == null)) {
                return;
            }
            // If needed, prime world list
            if (worldsToDo == null) {
                worldsToDo = new ArrayList<World>(getServer().getWorlds());
            }
            while (mobsToDo == null) {
                if (worldsToDo.isEmpty()) {
                    // Now, review old map - anything left is gone
                    for(Marker oldm : mobicons.values()) {
                        oldm.deleteMarker();
                    }
                    // And replace with new map
                    mobicons = newmap;        
                    // Schedule next run
                    getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, new MobUpdate(), updperiod);
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
                Integer idx = lookup_cache.get(clsid);
                if(idx == null) {
                    for(i = 0; i < mobs.length; i++) {
                        if((mobs[i].mobclass != null) && mobs[i].mobclass.isInstance(le)){
                            if (mobs[i].entclsid == null) {
                                break;
                            }
                            else if(gethandle != null) {
                                Object obcentity = null;
                                try {
                                    obcentity = gethandle.invoke(le);
                                } catch (Exception x) {
                                }
                                if ((mobs[i].entclass != null) && (obcentity != null) && (mobs[i].entclass.isInstance(obcentity))) {
                                    break;
                                }
                            }
                        }
                    }
                    lookup_cache.put(clsid, i);
                }
                else {
                    i = idx;
                }
                if(i >= mobs.length) {
                    continue;
                }

                String label = null;
                if(mobs[i].mobid.equals("spider")) {    /* Check for jockey */
                    if(le.getPassenger() != null) { /* Has passenger? */
                        i = findNext(i, "spiderjockey");    /* Make jockey */
                    }
                }
                else if(mobs[i].mobid.equals("chicken")) {    /* Check for jockey */
                    if(le.getPassenger() != null) { /* Has passenger? */
                        i = findNext(i, "chickenjockey");    /* Make jockey */
                    }
                }
                else if(mobs[i].mobid.equals("wolf")) { /* Check for tamed wolf */
                    Wolf wolf = (Wolf)le;
                    if(wolf.isTamed()) {
                        i = findNext(i, "tamedwolf");
                        AnimalTamer t = wolf.getOwner();
                        if((t != null) && (t instanceof OfflinePlayer)) {
                            label = "Wolf (" + ((OfflinePlayer)t).getName() + ")";
                        }
                    }
                }
                else if(mobs[i].mobid.equals("ocelot")) { /* Check for tamed ocelot */
                    Ocelot cat = (Ocelot)le;
                    if(cat.isTamed()) {
                        i = findNext(i, "cat");
                        AnimalTamer t = cat.getOwner();
                        if((t != null) && (t instanceof OfflinePlayer)) {
                            label = "Cat (" + ((OfflinePlayer)t).getName() + ")";
                        }
                    }
                }
                else if(mobs[i].mobid.equals("villager")) {
                    Villager v = (Villager)le;
                    Profession p = v.getProfession();
                    if(p != null) {
                        switch(p) {
                            case BLACKSMITH:
                                label = "Blacksmith";
                                break;
                            case BUTCHER:
                                label = "Butcher";
                                break;
                            case FARMER:
                                label = "Farmer";
                                break;
                            case LIBRARIAN:
                                label = "Librarian";
                                break;
                            case NITWIT:
                                label = "Nitwit";
                                break;
                            case PRIEST:
                                label = "Priest";
                                break;
                        }
                    }
                }                
                else if(mobs[i].mobid.equals("vanillahorse") || mobs[i].mobid.equals("donkey") || mobs[i].mobid.equals("mule") || mobs[i].mobid.equals("zombiehorse") || mobs[i].mobid.equals("skeletonhorse")) {    /* Check for rider */
                    if(le.getPassenger() != null) { /* Has passenger? */
                        Entity e = le.getPassenger();
                        if (e instanceof Player) {
                            label = label + " (" + ((Player)e).getName() + ")";
                        }
                    }
                }
                if(i >= mobs.length) {
                    continue;
                }
                if(label == null) {
                    label = mobs[i].label;
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
                Marker m = mobicons.remove(le.getEntityId());
                if(nolabels) {
                    label = "";
                }
                else if(inc_coord) {
                    label = label + " [" + (int)x + "," + (int)y + "," + (int)z + "]";
                }
                if(m == null) { /* Not found?  Need new one */
                    m = set.createMarker("mob"+le.getEntityId(), label, curWorld.getName(), x, y, z, mobs[i].icon, false);
                }
                else {  /* Else, update position if needed */
                    m.setLocation(curWorld.getName(), x, y, z);
                    m.setLabel(label);
                    m.setMarkerIcon(mobs[i].icon);
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

    private Map<Integer, Marker> mobicons = new HashMap<Integer, Marker>();
    private Map<Integer, Marker> vehicleicons = new HashMap<Integer, Marker>();
    
    private int findNext(int idx, String mobid) {
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
            if(set != null)  {
                set.deleteMarkerSet();
                set = null;
            }
            if(vset != null)  {
                vset.deleteMarkerSet();
                vset = null;
            }
            mobicons.clear();
            vehicleicons.clear();
            lookup_cache.clear();
        }
        else {
            reload = true;
        }
        this.saveDefaultConfig();
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */
        
        /* Now, check which mobs are enabled */
        Set<Class<Entity>> clsset = new HashSet<Class<Entity>>();
        int cnt = 0;
        for(int i = 0; i < configmobs.length; i++) {
            configmobs[i].init();
            configmobs[i].enabled = cfg.getBoolean("mobs." + configmobs[i].mobid, false);
            configmobs[i].icon = markerapi.getMarkerIcon("mobs." + configmobs[i].mobid);
            InputStream in = null;
            if(tinyicons)
                in = getClass().getResourceAsStream("/8x8/" + configmobs[i].mobid + ".png");
            if(in == null)
                in = getClass().getResourceAsStream("/" + configmobs[i].mobid + ".png");
            if(in != null) {
                if(configmobs[i].icon == null)
                    configmobs[i].icon = markerapi.createMarkerIcon("mobs." + configmobs[i].mobid, configmobs[i].label, in);
                else    /* Update image */
                    configmobs[i].icon.setMarkerIconImage(in);
            }
            if(configmobs[i].icon == null) {
                configmobs[i].icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
            if(configmobs[i].enabled) {
                cnt++;
            }
        }
        /* Make list of just enabled mobs */
        mobs = new MobMapping[cnt];
        for(int i = 0, j = 0; i < configmobs.length; i++) {
            if(configmobs[i].enabled) {
                mobs[j] = configmobs[i];
                j++;
                clsset.add(configmobs[i].mobclass);
            }
        }

        hideifshadow = cfg.getInt("update.hideifshadow", 15);
        hideifundercover = cfg.getInt("update.hideifundercover", 15);
        /* Now, add marker set for mobs (make it transient) */
        if(mobs.length > 0) {
            set = markerapi.getMarkerSet("mobs.markerset");
            if(set == null)
                set = markerapi.createMarkerSet("mobs.markerset", cfg.getString("layer.name", "Mobs"), null, false);
            else
                set.setMarkerSetLabel(cfg.getString("layer.name", "Mobs"));
            if(set == null) {
                severe("Error creating marker set");
                return;
            }
            set.setLayerPriority(cfg.getInt("layer.layerprio", 10));
            set.setHideByDefault(cfg.getBoolean("layer.hidebydefault", false));
            int minzoom = cfg.getInt("layer.minzoom", 0);
            if(minzoom > 0) /* Don't call if non-default - lets us work with pre-0.28 dynmap */
                set.setMinZoom(minzoom);
            tinyicons = cfg.getBoolean("layer.tinyicons", false);
            nolabels = cfg.getBoolean("layer.nolabels", false);
            inc_coord = cfg.getBoolean("layer.inc-coord", false);
            /* Get position resolution */
            res = cfg.getDouble("update.resolution", 1.0);
            /* Set up update job - based on period */
            double per = cfg.getDouble("update.period", 5.0);
            if(per < 2.0) per = 2.0;
            updperiod = (long)(per*20.0);
            updates_per_tick = cfg.getInt("update.mobs-per-tick", 20);
            stop = false;
            getServer().getScheduler().scheduleSyncDelayedTask(this, new MobUpdate(), updperiod);
            info("Enable layer for mobs");
        }
        else {
            info("Layer for mobs disabled");
        }

        /* Now, check which vehicles are enabled */
        clsset = new HashSet<Class<Entity>>();
        cnt = 0;
        for(int i = 0; i < configvehicles.length; i++) {
            configvehicles[i].init();
            configvehicles[i].enabled = cfg.getBoolean("vehicles." + configvehicles[i].mobid, false);
            configvehicles[i].icon = markerapi.getMarkerIcon("vehicles." + configvehicles[i].mobid);
            InputStream in = null;
            if(tinyicons)
                in = getClass().getResourceAsStream("/8x8/" + configvehicles[i].mobid + ".png");
            if(in == null)
                in = getClass().getResourceAsStream("/" + configvehicles[i].mobid + ".png");
            if(in != null) {
                if(configvehicles[i].icon == null)
                    configvehicles[i].icon = markerapi.createMarkerIcon("vehicles." + configvehicles[i].mobid, configvehicles[i].label, in);
                else    /* Update image */
                    configvehicles[i].icon.setMarkerIconImage(in);
            }
            if(configvehicles[i].icon == null) {
                configvehicles[i].icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
            if(configvehicles[i].enabled) {
                cnt++;
            }
        }
        /* Make list of just enabled vehicles */
        vehicles = new MobMapping[cnt];
        for(int i = 0, j = 0; i < configvehicles.length; i++) {
            if(configvehicles[i].enabled) {
                vehicles[j] = configvehicles[i];
                j++;
                clsset.add(configvehicles[i].mobclass);
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
        if(set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        if(vset != null) {
            vset.deleteMarkerSet();
            vset = null;
        }
        mobicons.clear();
        vehicleicons.clear();
        lookup_cache.clear();
        stop = true;
    }

}
