package com.canefe.story;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class LocationManager {

    private static LocationManager instance;

    private List<Location> locations;

    private final Story plugin;

    private final File locationDir;

    private LocationManager(Story plugin) {
        this.plugin = plugin;
        this.locationDir = new File(plugin.getDataFolder(), "locations");

    }

    public static LocationManager getInstance(Story plugin) {
        if (instance == null) {
            instance = new LocationManager(plugin);
        }
        return instance;
    }

    public File getLocationDirectory() {
        return locationDir;
    }

    public void loadLocation(String locationName) {
        // Load locations from a file
        File locationFile = new File(locationDir, locationName + ".yml");
        if (!locationFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(locationFile);
        List<String> context = config.getStringList("context");
        Location location = new Location(locationName, context);
        locations.add(location);

    }
}
