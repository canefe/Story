package com.canefe.story;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NPCDataManager {

    private static NPCDataManager instance;

    private final JavaPlugin plugin;
    private final Map<String, NPCData> npcDataMap;
    private final File npcDirectory;

    private NPCDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.npcDirectory = new File(plugin.getDataFolder(), "npcs");
        this.npcDataMap = new HashMap<>();

        if (!npcDirectory.exists()) {
            npcDirectory.mkdirs(); // Create the directory if it doesn't exist
        }
    }

    public static NPCDataManager getInstance(Story plugin) {
        if (instance == null) {
            instance = new NPCDataManager(plugin);
        }
        return instance;
    }


    public File getNPCDirectory() {
        return npcDirectory;
    }


    public FileConfiguration loadNPCData(String npcName) {
        File npcFile = new File(getNPCDirectory(), npcName + ".yml");
        if (!npcFile.exists()) {
            return new YamlConfiguration(); // Return an empty configuration if no file exists
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(npcFile);

        return YamlConfiguration.loadConfiguration(npcFile);
    }


    public void saveNPCFile(String npcName, FileConfiguration config) {
        File npcFile = new File(npcDirectory, npcName + ".yml");
        try {
            config.save(npcFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteNPCFile(String npcName) {
        File npcFile = new File(npcDirectory, npcName + ".yml");
        if (npcFile.exists()) {
            npcFile.delete();
        }
    }
}

