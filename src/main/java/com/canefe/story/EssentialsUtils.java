package com.canefe.story;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Set;

/**
 *
 * EssentialsUtils v1.0
 *
 * @author seemethere
 *
 * This class returns all data associated with Essentials userdata & warps.
 *  This class was created because it is a lightweight and easy to understand alternative to
 *  including the Essentials API in your plugins
 * NOTE: This class also assumes you already have Essentials and does not check for that
 *
 * Last Update: 6/25/2013
 */
public class EssentialsUtils {

    private static File getUserDataFile(String player) throws FileNotFoundException{
        return new File(Story.instance.getDataFolder(), "../Essentials/userdata/" + Bukkit.getPlayer(player).getUniqueId() + ".yml");
    }

    private static YamlConfiguration getUserYaml(String player) {
        player = player.toLowerCase();
        File userDataFile;
        try {
            userDataFile = getUserDataFile(player);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return YamlConfiguration.loadConfiguration(userDataFile);
    }

    public static Long getLastLoginTime(String player) {
        return getUserYaml(player).getLong("timestamps.login");
    }

    public static Long getLastLogoutTime(String player) {
        return getUserYaml(player).getLong("timestamps.logout");
    }

    public static Long getLastTeleportTime(String player) {
        return getUserYaml(player).getLong("timestamps.lastteleport");
    }

    public static String getNickname(String player) {
        YamlConfiguration userYaml = getUserYaml(player);
        if (userYaml == null) {
            return player;
        }
        String nickname = userYaml.getString("nickname");
        return nickname != null ? nickname : player;
    }

    public static double getMoney(String player) {
        return getUserYaml(player).getDouble("money");
    }

    // NOTE: This does not check if they are actually banned
    public static String getBanReason(String player) {
        return getUserYaml(player).getString("ban.reason");
    }

    public static boolean hasSocialSpy(String player) {
        return getUserYaml(player).getBoolean("socialspy");
    }

    public static boolean isAFK(String player) {
        return getUserYaml(player).getBoolean("afk");
    }

    public static Set<String> getHomeNames(String player) {
        return getUserYaml(player).getConfigurationSection("homes").getKeys(false);
    }

    public static String getIP(String player) {
        return getUserYaml(player).getString("ipAddress");
    }

    public static Location getHomeLocation(String player, String home) {
        YamlConfiguration userYaml = getUserYaml(player);
        if (userYaml.getConfigurationSection("homes") == null) {
            return null;
        }
        return getLocation(userYaml, "homes." + home + ".");
    }

    public static Location getLastLocation(String player) {
        YamlConfiguration userYaml = getUserYaml(player);
        return getLocation(userYaml, "lastlocation.");
    }

    public static Location getWarp(String warp) {
        File warpFile = new File(Story.instance.getDataFolder(), "../Essentials/warps/" + warp.toLowerCase() + ".yml");
        YamlConfiguration warpYML = YamlConfiguration.loadConfiguration(warpFile);
        return getLocation(warpYML, "");
    }

    private static Location getLocation(YamlConfiguration yaml, String header) {
        World world = Story.instance.getServer().getWorld(yaml.getString(header + "world"));
        double yaw = yaml.getDouble(header + "yaw");
        double pitch = yaml.getDouble(header + "pitch");
        double x = yaml.getDouble(header + "x");
        double y = yaml.getDouble(header + "y");
        double z = yaml.getDouble(header + "z");
        return new Location(world, x, y, z, (float) yaw, (float) pitch);
    }
}
