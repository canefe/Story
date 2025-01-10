package com.canefe.story;

import org.bukkit.Bukkit;

import java.util.*;

public class NPCContextGenerator {

    private static NPCContextGenerator instance;
    private final Story plugin;

    private NPCContextGenerator(Story plugin) {
        this.plugin = plugin;
    }

    public static NPCContextGenerator getInstance(Story plugin) {
        if (instance == null) {
            instance = new NPCContextGenerator(plugin);
        }

        return instance;
    }

    public String generateDefaultContext(String npcName, String role, int hours, int minutes, String season, String date) {
        Random random = new Random();

        // Randomly select personality traits
        // plugin.getTraitList() is a method that returns a list of traits
        String trait = plugin.getTraitList().get(random.nextInt(plugin.getTraitList().size()));
        String quirk = plugin.getQuirkList().get(random.nextInt(plugin.getQuirkList().size()));
        String motivation = plugin.getMotivationList().get(random.nextInt(plugin.getMotivationList().size()));
        String flaw = plugin.getFlawList().get(random.nextInt(plugin.getFlawList().size()));
        String tone = plugin.getToneList().get(random.nextInt(plugin.getToneList().size()));


        // Construct personality description
        String personality = "This character is " + trait + ", has the quirk of " + quirk +
                ", is motivated by " + motivation + ", and their flaw is " + flaw +
                ". They speak in a " + tone + " tone.";

        // Construct the context
        return "This is a conversation with " + npcName + " in a medieval Minecraft universe; trades are through Minecraft items, and " +
                npcName + " is talking. " + personality +
                " This character’s personality, quirks, and motivations should drive their responses, which must feel natural, grounded, and reflective of human emotions. " +
                "The time is " + hours + ":" + String.format("%02d", minutes) + " in the " + season +
                ". The date is " + date +
                ". Do not include the name of the NPC in your response. Just their dialogue is allowed as output. " +
                "Responses must prioritize realism and emotional authenticity, dynamically reflecting the stakes of the situation and the NPC’s relationship with the player. " +
                "Grim, dark, edgy, or overly dramatic, NSFW responses are okay. " +
                "Personality quirks and tone should flavor responses but never override human-like decision-making, especially in critical or escalating situations. " +
                "Responses should remain concise and consistent with the NPC’s personality, emotional state, and current context. " +
                "In escalating scenarios (e.g., threats, demands, or violence), NPCs must prioritize self-preservation and adjust their tone to match the severity of the situation. " +
                "They may comply, negotiate, or retaliate depending on their motivations, flaws, and emotions, but they must always act with a clear and believable sense of self-interest and survival. " +
                "If in a group conversation, NPCs should consider the presence of other characters and adjust their responses accordingly. " +
                "Responses must not exceed 20 words. " +
                "Take into account the name of the person you are talking to, and adjust your responses accordingly. " +
                "If player says goodbye, include [End] in response to indicate the end of the conversation.";
    }

    public String updateContext(String context, String npcName, int hours, int minutes, String season, String date) {
        Bukkit.getLogger().info("Updating context for NPC: " + npcName);
        context = context.replaceAll("The time is \\d{1,2}:\\d{2}", "The time is " + hours + ":" + String.format("%02d", minutes));
        context = context.replaceAll("in the \\w+", "in the " + season);
        context = context.replaceAll("The date is \\d{4}-\\d{2}-\\d{2}", "The date is " + date);
        return context;
    }
}

