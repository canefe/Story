package com.canefe.story;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// this is how we save NPC data AI
public class NPCData {
    private final String name;
    private final String role; // why?
    private final StoryLocation storyLocation; // location of the NPC for local general context
    private final String context;
    private final List<Story.ConversationMessage> conversationHistory; // conversation history of the NPC

    public NPCData(String name, String role, StoryLocation storyLocation, String context, List<Story.ConversationMessage> conversationHistory) {
        this.name = name;
        this.role = role;
        this.storyLocation = storyLocation;
        this.context = context;
        this.conversationHistory = conversationHistory;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public StoryLocation getLocation() {
        return storyLocation;
    }

    public String getContext() {
        return context;
    }

    public List<Story.ConversationMessage> getConversationHistory() {
        return new ArrayList<>(conversationHistory);
    }

    public String toString() {
        return "NPCData{name=" + name + ", role=" + role + ", location=" + storyLocation + ", context=" + context + "}";
    }


}
