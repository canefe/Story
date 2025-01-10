package com.canefe.story;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationManager {

    private static LocationManager instance; // Singleton instance
    private final Story plugin;
    private final Map<String, StoryLocation> locations;
    private final File locationDirectory;

    private LocationManager(Story plugin) {
        this.plugin = plugin;
        this.locationDirectory = new File(plugin.getDataFolder(), "locations");
        this.locations = new HashMap<>();

        if (!locationDirectory.exists()) {
            locationDirectory.mkdirs(); // Create the directory if it doesn't exist
        }
    }

    // Get the singleton instance
    public static synchronized LocationManager getInstance(Story plugin) {
        if (instance == null) {
            instance = new LocationManager(plugin);
        }
        return instance;
    }

    public File getLocationDirectory() {
        return locationDirectory;
    }

    public void saveLocationFile(String locationName, FileConfiguration config) {
        File locationFile = new File(locationDirectory, locationName + ".yml");
        try {
            config.save(locationFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StoryLocation loadLocationData(String locationName) {
        File locationFile = new File(getLocationDirectory(), locationName + ".yml");
        if (!locationFile.exists()) {
            return null; // Return null if no file exists
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(locationFile);
        StoryLocation storyLocation = new StoryLocation(locationName, config.getStringList("npcNames"), config.getStringList("context"));

        locations.put(locationName, storyLocation);

        return storyLocation;
    }

    // Add a location
    public void addLocation(StoryLocation storyLocation) {
        locations.put(storyLocation.getName(), storyLocation);
    }

    // Get a location by name
    public StoryLocation getLocation(String name) {
        return loadLocationData(name);
    }

    // Get all locations
    public List<StoryLocation> getAllLocations() {
        return List.copyOf(locations.values());
    }

    // Get location-specific global contexts
    public List<String> getLocationGlobalContexts(String locationName) {
        StoryLocation storyLocation = locations.get(locationName);
        return (storyLocation != null) ? storyLocation.getContext() : List.of();
    }

    // Check if an NPC is part of a location
    public boolean isNPCInLocation(String npcName, String locationName) {
        StoryLocation storyLocation = locations.get(locationName);
        return storyLocation != null && storyLocation.getNpcNames().contains(npcName);
    }
}
