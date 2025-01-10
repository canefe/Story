package com.canefe.story;

import java.util.List;

public class StoryLocation {
    private final String name;
    private final List<String> npcNames; // List of NPC names (references to the central NPC collection)
    private final List<String> context;

    public StoryLocation(String name, List<String> npcNames, List<String> context) {
        this.name = name;
        this.npcNames = npcNames; // Store references to NPCs by name
        this.context = context;
    }

    public String getName() {
        return name;
    }

    public List<String> getNpcNames() {
        return npcNames;
    }

    public List<String> getContext() {
        return context;
    }

    @Override
    public String toString() {
        return "Location{name='" + name + "', npcNames=" + npcNames + ", context=" + context + "}";
    }
}
