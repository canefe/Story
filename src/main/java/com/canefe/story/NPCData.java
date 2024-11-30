package com.canefe.story;


// this is how we save NPC data AI
public class NPCData {
    private final String name;
    private final String role; // why?
    private final Location location; // location of the NPC for local general context
    private final String context;

    public NPCData(String name, String role, Location location, String context) {
        this.name = name;
        this.role = role;
        this.location = location;
        this.context = context;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public Location getLocation() {
        return location;
    }

    public String getContext() {
        return context;
    }

    public String toString() {
        return "NPCData{name=" + name + ", role=" + role + ", location=" + location + ", context=" + context + "}";
    }


}
