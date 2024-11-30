package com.canefe.story;

import com.google.gson.Gson;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import me.casperge.realisticseasons.api.SeasonsAPI;
import me.casperge.realisticseasons.calendar.Date;
import me.casperge.realisticseasons.season.Season;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.canefe.story.Story.ConversationMessage;

public class ConversationManager {

    private static ConversationManager instance;
    private final Story plugin; // Reference to the main plugin class
    private final Gson gson = new Gson(); // JSON utility
    private final NPCContextGenerator npcContextGenerator;

    // Active group conversations (UUID -> GroupConversation)
    private final List<GroupConversation> activeConversations = new ArrayList<>();

    private final Map<String, Integer> hologramTasks = new HashMap<>();

    private ConversationManager(Story plugin) {
        this.plugin = plugin;
        this.npcContextGenerator = NPCContextGenerator.getInstance(plugin);
    }

    public static ConversationManager getInstance(Story plugin) {
        if (instance == null) {
            instance = new ConversationManager(plugin);
        }
        return instance;
    }


    // Start a new group conversation
    public void startGroupConversation(Player player, List<String> npcNames) {
        UUID playerUUID = player.getUniqueId();

        // End any existing conversation GroupConversation.players (list of UUIDs)
        GroupConversation existingConversation = activeConversations.stream()
                .filter(conversation -> conversation.getPlayers().contains(playerUUID))
                .findFirst().orElse(null);
        if (existingConversation != null && existingConversation.isActive()) {
            endConversation(player);
        }

        // Initialize a new conversation with the provided NPCs
        List <UUID> players = new ArrayList<>();
        players.add(playerUUID);
        GroupConversation newConversation = new GroupConversation(players, npcNames);
        activeConversations.add(newConversation);

        player.sendMessage(ChatColor.GRAY + "You started a conversation with: " + String.join(", ", npcNames));
    }

    // Add NPC to an existing conversation
    public void addNPCToConversation(Player player, String npcName) {
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = activeConversations.stream()
                .filter(convo -> convo.getPlayers().contains(playerUUID))
                .findFirst().orElse(null);

        if (conversation == null || !conversation.isActive()) {
            player.sendMessage(ChatColor.RED + "You are not currently in an active conversation.");
            return;
        }

        if (conversation.addNPC(npcName)) {
            player.sendMessage(ChatColor.GRAY + npcName + " has joined the conversation.");
        } else {
            player.sendMessage(ChatColor.YELLOW + npcName + " is already part of the conversation.");
        }
    }

    // End a conversation
    public void endConversation(Player player) {
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = activeConversations.stream()
                .filter(convo -> convo.getPlayers().contains(playerUUID))
                .findFirst().orElse(null);

        if (conversation != null && conversation.isActive()) {
            conversation.setActive(false);

            // Summarize the conversation
            summarizeConversation(conversation.getConversationHistory(), conversation.getNpcNames(), player.getName());

            activeConversations.remove(conversation);
            player.sendMessage(ChatColor.GRAY + "The conversation has ended.");
        } else {
            activeConversations.removeIf(convo -> !convo.isActive());
        }
    }

    // Add a player's message to the group conversation
    public void addPlayerMessage(Player player, String message) {
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = activeConversations.stream()
                .filter(convo -> convo.getPlayers().contains(playerUUID))
                .findFirst().orElse(null);

        if (conversation == null || !conversation.isActive()) {
            player.sendMessage(ChatColor.RED + "You are not currently in an active conversation.");
            return;
        }

        String playerName = player.getName();

        if (EssentialsUtils.getNickname(playerName) != null) {
            playerName = EssentialsUtils.getNickname(playerName);
        }

        // Add the player's message to the conversation history
        conversation.addMessage(new ConversationMessage("user", playerName + ": " + message));

        // Generate responses from all NPCs sequentially
        generateGroupNPCResponses(conversation, player);
    }

    public Map<String, Integer> getHologramTasks() {
        return hologramTasks;
    }

    public void addHologramTask(String npcName, int taskId) {
        hologramTasks.put(npcName, taskId);
    }

    public void removeHologramTask(String npcName) {
        hologramTasks.remove(npcName);
    }

    public boolean isNPCInConversation(Player player, String npcName) {
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = activeConversations.stream()
                .filter(convo -> convo.getPlayers().contains(playerUUID))
                .findFirst().orElse(null);
        return conversation != null && conversation.isActive() && conversation.getNpcNames().contains(npcName);
    }

    public boolean isNPCInConversation(String npcName) {
        for (GroupConversation conversation : activeConversations) {
            if (conversation.getNpcNames().contains(npcName)) {
                return true;
            }
        }
        return false;
    }

    public void addPlayerToConversation(Player player, String npcName) {
        // Join the player to another player's conversation
        for (GroupConversation conversation : activeConversations) {
            if (conversation.getNpcNames().contains(npcName)) {
                conversation.addPlayerToConversation(player);
                player.sendMessage(ChatColor.GRAY + "You joined the conversation with " + npcName);
                return;
            }
        }
    }

    public boolean isPlayerInConversation(Player player) {
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = activeConversations.stream()
                .filter(convo -> convo.getPlayers().contains(playerUUID))
                .findFirst().orElse(null);
        return conversation != null && conversation.isActive();
    }

    public void addNPCMessage(String npcName, String message) {
        for (GroupConversation conversation : activeConversations) {
            if (conversation.getNpcNames().contains(npcName)) {
                conversation.addMessage(new ConversationMessage("assistant", npcName + ": " + message));
                return;
            }
        }
    }

    public GroupConversation getActiveNPCConversation(String npcName) {
        for (GroupConversation conversation : activeConversations) {
            if (conversation.getNpcNames().contains(npcName)) {
                return conversation;
            }
        }
        return null;
    }

    // Generate NPC responses sequentially for the group conversation
    public void generateGroupNPCResponses(GroupConversation conversation, Player player) {
        List<String> npcNames = conversation.getNpcNames();
        List<ConversationMessage> conversationHistory = conversation.getConversationHistory();

        MiniMessage mm = MiniMessage.miniMessage();

        // Process NPC responses one by one with a delay
        for (int i = 0; i < npcNames.size(); i++) {
            String npcName = npcNames.get(i);
            int delay = i * 3; // 6 seconds delay for each NPC (3 seconds for "is thinking" + 3 seconds for response)

            // Schedule the "is thinking" message
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location npcPos = plugin.getNPCPos(npcName);
                showThinkingHolo(npcPos, npcName);

                // Schedule the NPC response after the "is thinking" message
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    CompletableFuture.runAsync(() -> {
                        try {
                            // Fetch NPC data dynamically
                            FileConfiguration npcData = plugin.getNPCData(npcName);
                            String npcRole = npcData.getString("role", "Default role");
                            String existingContext = npcData.getString("context", null);

                            // Add dynamic world context
                            SeasonsAPI seasonsAPI = SeasonsAPI.getInstance();
                            Season season = seasonsAPI.getSeason(Bukkit.getWorld("world")); // Replace "world" with actual world name
                            int hours = seasonsAPI.getHours(Bukkit.getWorld("world"));
                            int minutes = seasonsAPI.getMinutes(Bukkit.getWorld("world"));
                            Date date = seasonsAPI.getDate(Bukkit.getWorld("world"));

                            // Update or generate context
                            if (existingContext != null) {
                                existingContext = npcContextGenerator.updateContext(existingContext, npcName, hours, minutes, season.toString(), date.toString(true));
                            } else {
                                existingContext = npcContextGenerator.generateDefaultContext(npcName, npcRole, hours, minutes, season.toString(), date.toString(true));
                            }

                            // Add context to the conversation history
                            List<ConversationMessage> npcConversationHistory = plugin.getMessages(npcData);
                            if (npcConversationHistory.isEmpty()) {
                                npcConversationHistory.add(new ConversationMessage("system", existingContext));
                            } else if (!Objects.equals(npcConversationHistory.get(0).getContent(), existingContext)) {
                                npcConversationHistory.set(0, new ConversationMessage("system", existingContext));
                            }

                            plugin.saveNPCData(npcName, npcRole, existingContext, npcConversationHistory);

                            // Prepare temp history
                            List<ConversationMessage> tempHistory = new ArrayList<>(conversation.getConversationHistory());
                            plugin.getGeneralContexts().forEach(context -> tempHistory.add(new ConversationMessage("system", context)));

                            List<ConversationMessage> lastTwentyMessages = npcConversationHistory.subList(
                                    Math.max(npcConversationHistory.size() - 20, 0), npcConversationHistory.size());
                            lastTwentyMessages.add(0, npcConversationHistory.get(0));

                            tempHistory.addAll(0, lastTwentyMessages);

                            // EssentialsUtils.getNickname(player.getName())
                            List<String> playerNames = conversation.getPlayers().stream()
                                    .map(uuid -> EssentialsUtils.getNickname(Bukkit.getPlayer((UUID) uuid).getName()))
                                    .collect(Collectors.toList());

                            // Add group conversation context
                            tempHistory.add(new ConversationMessage("system",
                                    "You are " + npcName + " in a group conversation with " + String.join(", ", npcNames) + " , " + String.join(", ", playerNames) + "."));

                            // 20 words max
                            tempHistory.add(new ConversationMessage("system",
                                    "Responses must be short, 20 words max. Only speak as " + npcName + " would."));

                            // Request AI response
                            String aiResponse = plugin.getAIResponse(tempHistory);

                            if (aiResponse == null || aiResponse.isEmpty()) {
                                plugin.getLogger().warning("Failed to generate NPC response for " + npcName);
                                return;
                            }
                            DHAPI.removeHologram(plugin.getNPCUUID(npcName).toString());

                            int taskId = hologramTasks.remove(npcName);
                            Bukkit.getScheduler().cancelTask(taskId);
                            // Add NPC response to conversation
                            conversation.addMessage(new ConversationMessage("assistant", npcName + ": " + aiResponse));
                            conversation.addMessage(new ConversationMessage("user", EssentialsUtils.getNickname(player.getName()) + " listens"));

                            // Broadcast NPC response
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.broadcastNPCMessage(aiResponse, npcName, false, null, null, null);
                            });

                        } catch (Exception e) {
                            plugin.getLogger().warning("Error while generating response for NPC: " + npcName);
                            e.printStackTrace();
                        }
                    });
                }, 3 * 20L); // 3 seconds delay for the response
            }, delay * 20L); // Convert seconds to ticks (1 second = 20 ticks)
        }
    }

    public void showThinkingHolo(Location npcPos, String npcName) {
        if (npcPos != null) {
            npcPos.add(0, 2.10, 0);

            Hologram holo = DHAPI.getHologram(plugin.getNPCUUID(npcName).toString());
            if (holo != null) {
                DHAPI.removeHologram(plugin.getNPCUUID(npcName).toString());
            }
            holo = DHAPI.createHologram(plugin.getNPCUUID(npcName).toString(), npcPos);
            DHAPI.addHologramLine(holo, 0, "&7&othinking...");
            DHAPI.updateHologram(npcName);

            Hologram finalHolo = holo;
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Location updatedPos = plugin.getNPCPos(npcName);
                if (updatedPos != null) {
                    updatedPos.add(0, 2.10, 0);
                    DHAPI.moveHologram(finalHolo, updatedPos);
                }
            }, 0L, 5L).getTaskId();

            hologramTasks.put(npcName, taskId);
        } else {
            plugin.getLogger().warning("Failed to find the position of NPC: " + npcName);
        }
    }


    // Summarize the conversation history
    private void summarizeConversation(List<ConversationMessage> history, List<String> npcNames, String playerName) {
        if (history.isEmpty()) return;

        // Build the summary prompt
        StringBuilder prompt = new StringBuilder("Summarize this conversation between ");
        prompt.append(playerName).append(" and NPCs ").append(String.join(", ", npcNames)).append(".\n");
        for (ConversationMessage msg : history) {
            prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        // Generate summary using AI
        CompletableFuture.runAsync(() -> {
            try {
                String summary = plugin.getAIResponse(Collections.singletonList(
                        new ConversationMessage("system", prompt.toString())
                ));
                if (summary != null && !summary.isEmpty()) {
                    for (String npcName : npcNames) {
                        plugin.addSystemMessage(npcName, summary);
                    }
                } else {
                    plugin.getLogger().warning("Failed to summarize the conversation.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                plugin.getLogger().severe("Error occurred while summarizing the conversation.");
            }
        });
    }

    // Check if a player has an active conversation
    public boolean hasActiveConversation(Player player) {
        GroupConversation conversation = activeConversations.stream()
                .filter(convo -> convo.getPlayers().contains(player.getUniqueId()))
                .findFirst().orElse(null);
        return conversation != null && conversation.isActive();
    }

    public GroupConversation getActiveConversation(Player player) {
        return activeConversations.stream()
                .filter(convo -> convo.getPlayers().contains(player.getUniqueId()))
                .findFirst().orElse(null);
    }
}
