package com.canefe.story;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class StoryManager {
    private static StoryManager instance; // Singleton instance
    private final Story plugin;

    private StoryManager(Story plugin) {
        // Private constructor to enforce singleton pattern
        this.plugin = plugin;
    }

    // Get the singleton instance
    public static synchronized StoryManager getInstance(Story plugin) {
        if (instance == null) {
            instance = new StoryManager(plugin);
        }
        return instance;
    }


}
