package com.canefe.story;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class StoryPlaceholderExpansion extends PlaceholderExpansion {

    private final Story plugin;

    public StoryPlaceholderExpansion(Story plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getAuthor() {
        return "Author"; //
    }

    @Override
    public String getIdentifier() {
        return "story"; //
    }

    @Override
    public String getVersion() {
        return "1.0.0"; //
    }

    @Override
    public boolean persist() {
        return true; //
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("quest_obj")) {
            return plugin.questObj;
        }

        if (params.equalsIgnoreCase("quest_title")) {
            return plugin.questTitle;
        }

        return null; //
    }
}