package com.canefe.story;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NPCUtils {

    // Cache for NPCs
    private final Map<String, NPC> npcCache = new ConcurrentHashMap<>();

    private Story plugin;

    // Private constructor to prevent instantiation
    private NPCUtils(Story plugin) {
        this.plugin = plugin;
    }

    private static final class InstanceHolder {
        // Single instance of the class
        private static NPCUtils instance;

        private static void initialize(Story plugin) {
            if (instance == null) {
                instance = new NPCUtils(plugin);
            }
        }
    }

    // Static method to get the single instance
    public static NPCUtils getInstance(Story plugin) {
        InstanceHolder.initialize(plugin);
        return InstanceHolder.instance;
    }

    // Asynchronous method to get an NPC by name, with caching
    public CompletableFuture<NPC> getNPCByNameAsync(String npcName) {
        return CompletableFuture.supplyAsync(() -> {
            // Check cache first
            if (npcCache.containsKey(npcName.toLowerCase())) {
                return npcCache.get(npcName.toLowerCase());
            }

            // Search NPC registry if not in cache
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.getName().equalsIgnoreCase(npcName)) {
                    npcCache.put(npcName.toLowerCase(), npc);
                    return npc;
                }
            }

            return null; // NPC not found
        });
    }

    // Optional: Clear the cache (e.g., on reload)
    public void clearCache() {
        npcCache.clear();
    }
}

