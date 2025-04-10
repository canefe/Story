package com.canefe.story;

import com.google.gson.Gson;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import me.casperge.realisticseasons.api.SeasonsAPI;
import me.casperge.realisticseasons.calendar.Date;
import me.casperge.realisticseasons.season.Season;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.event.NavigationCompleteEvent;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.canefe.story.Story.ConversationMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ConversationManager {

    private static ConversationManager instance;
    private final Story plugin; // Reference to the main plugin class
    private final Gson gson = new Gson(); // JSON utility
    public final NPCContextGenerator npcContextGenerator;
    private boolean isRadiantEnabled = true;

    // Active group conversations (UUID -> GroupConversation)
    private final Map<Integer, GroupConversation> activeConversations = new HashMap<>();

    private final Map<String, Integer> hologramTasks = new HashMap<>();

    public Map<GroupConversation, Integer> getScheduledTasks() {
        return scheduledTasks;
    }

    private final Map<GroupConversation, Integer> scheduledTasks = new HashMap<>();

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


    public void setRadiantEnabled(boolean enabled) {
        if (enabled) {
            isRadiantEnabled = true;
        } else {
            isRadiantEnabled = false;
        }
    }

    public boolean isRadiantEnabled() {
        return isRadiantEnabled;
    }

    public Map<Integer, GroupConversation> getActiveConversations() {
        return activeConversations;
    }

    public GroupConversation getConversationById(Integer id) {
        return activeConversations.get(id);
    }

    private void addConversation(GroupConversation conversation) {
        int newId = activeConversations.size() + 1;
        activeConversations.put(newId, conversation);
    }

    private void removeConversation(GroupConversation conversation) {
        activeConversations.values().removeIf(convo -> convo.equals(conversation));
    }

    private GroupConversation getConversationByPlayer(Player player) {
        return activeConversations.values().stream()
                .filter(convo -> convo.getPlayers().contains(player.getUniqueId()))
                .findFirst().orElse(null);
    }

    // Start a new group conversation
    public GroupConversation startGroupConversation(Player player, List<NPC> npcs) {
        UUID playerUUID = player.getUniqueId();

        // End any existing conversation GroupConversation.players (list of UUIDs)
        GroupConversation existingConversation = activeConversations.values().stream()
                .filter(conversation -> conversation.getPlayers().contains(playerUUID))
                .findFirst().orElse(null);
        if (existingConversation != null && existingConversation.isActive()) {
            endConversation(player);
        }

        // Initialize a new conversation with the provided NPCs
        List <UUID> players = new ArrayList<>();
        players.add(playerUUID);
        GroupConversation newConversation = new GroupConversation(players, npcs);
        addConversation(newConversation);

        List <String> npcNames = new ArrayList<>();
        for (NPC npc : npcs) {
            npcNames.add(npc.getName());
        }

        player.sendMessage(ChatColor.GRAY + "You started a conversation with: " + String.join(", ", npcNames));
        return newConversation;
    }

    public void StopAllConversations() {
        // Create a copy of the conversations to avoid concurrent modification
        List<GroupConversation> conversationsToEnd = new ArrayList<>(activeConversations.values());

        // End each conversation in the copy
        for (GroupConversation conversation : conversationsToEnd) {
            endConversation(conversation);
        }
    }

    public GroupConversation startGroupConversationNoPlayer(List<NPC> npcs) {
        // Initialize a new conversation with the provided NPCs
        List <UUID> players = new ArrayList<>();
        // if any one of the npc is already in a conversation, don't start
        for (NPC npc : npcs) {
            if (isNPCInConversation(npc.getName())) {
                return null;
            }
        }

        GroupConversation newConversation = new GroupConversation(players, npcs);
        addConversation(newConversation);

        return newConversation;
    }

    // Start radiant conversation between two NPCs, no players involved
    public GroupConversation startRadiantConversation(List<NPC> npcs) {
        // Initialize a new conversation with the provided NPCs
        // Don't start if npc is already in a conversation
        for (NPC npc : npcs) {
            if (isNPCInConversation(npc.getName())) {
                return null;
            }
        }
        List <UUID> players = new ArrayList<>();

        GroupConversation newConversation = new GroupConversation(players, npcs);
        addConversation(newConversation);

        generateRadiantResponses(newConversation);

        return newConversation;
    }

    public void endRadiantConversation(GroupConversation conversation) {


        if (conversation != null && conversation.isActive()) {
            conversation.setActive(false);
            removeConversation(conversation);
        }

    }
    public void generateRadiantResponses(GroupConversation conversation) {
        List<String> npcNames = conversation.getNpcNames();
        List<NPC> npcs = conversation.getNPCs();
        List<ConversationMessage> conversationHistory = conversation.getConversationHistory();

        if (npcNames.size() == 1) {
            endConversation(conversation);
            return;
        }

        if (!conversation.isActive())
        {
            return;
        }

        // Process NPC responses one by one with a delay
        for (int i = 0; i < npcNames.size(); i++) {
            String npcName = npcNames.get(i);
            int delay = i * 3; // 6 seconds delay for each NPC (3 seconds for "is thinking" + 3 seconds for response)

            // Schedule the "is thinking" message after the delay
            int finalI = i;
            int finalI1 = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                npcs.stream().filter(npc -> npc.getName().equals(npcName)).findFirst().ifPresent(npc -> {
                    // Show "is thinking" hologram
                    showThinkingHolo(npc);

                    // Schedule the NPC response after another 3-second delay
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        CompletableFuture.runAsync(() -> {
                            try {
                                NPCUtils.NPCContext NPCContext = NPCUtils.getInstance(plugin).getOrCreateContextForNPC(npcName);

                                // Prepare temp history
                                List<ConversationMessage> tempHistory = new ArrayList<>(conversation.getConversationHistory());

                                tempHistory.addFirst(new ConversationMessage("system",
                                        "You are " + npcName + " in a with " + String.join(", ", npcNames) + ". Conversation can be about anything like about day or recent events. Don't make it go waste by asking questions."));
                                plugin.getGeneralContexts().forEach(context -> tempHistory.addFirst(new ConversationMessage("system", context)));
                                if (NPCContext.location != null)
                                    NPCContext.location.getContext().forEach(context -> tempHistory.addFirst(new ConversationMessage("system", context)));


                                List<ConversationMessage> lastTwentyMessages = NPCContext.conversationHistory.subList(
                                        Math.max(NPCContext.conversationHistory.size() - 20, 0), NPCContext.conversationHistory.size());
                                tempHistory.addFirst(new ConversationMessage("system", "Relations: " + NPCContext.relations.toString()));
                                tempHistory.addFirst(new ConversationMessage("system", "Your responses should reflect your relations with the other characters if applicable. Never print out the relation as dialogue."));
                                lastTwentyMessages.addFirst(NPCContext.conversationHistory.get(0));

                                tempHistory.addAll(0, lastTwentyMessages);

                                // Request AI response
                                String aiResponse = plugin.getAIResponse(tempHistory);

                                DHAPI.removeHologram(npc.getUniqueId().toString());

                                Integer taskId = hologramTasks.get(npcName);
                                if (taskId != null) {
                                    Bukkit.getScheduler().cancelTask(taskId);
                                    hologramTasks.remove(npcName);
                                }

                                if (aiResponse == null || aiResponse.isEmpty()) {
                                    plugin.getLogger().warning("Failed to generate NPC response for " + npcName);
                                    return;
                                }

                                // Add NPC response to conversation
                                conversation.addMessage(new ConversationMessage("assistant", npcName + ": " + aiResponse));
// get the next npc name
                                if (finalI1 + 1 < npcNames.size()) {
                                    conversation.addMessage(new ConversationMessage("user", npcNames.get(finalI1 + 1) + " listens"));
                                } else {
                                    conversation.addMessage(new ConversationMessage("user", npcNames.getFirst() + " listens"));
                                }

                                // Broadcast NPC response
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    plugin.broadcastNPCMessage(aiResponse, npcName, false, npc, null, null, NPCContext.avatar, plugin.randomColor(npcName));
                                });



                                endConversation(conversation);

                            } catch (Exception e) {
                                plugin.getLogger().warning("Error while generating response for NPC: " + npcName);
                                e.printStackTrace();
                            }
                        });
                    }, 3 * 20L); // 3 seconds delay for the response
                });
            }, delay * 20L); // Convert seconds to ticks (1 second = 20 ticks)
        }
    }

    // Add NPC to an existing conversation
    public void addNPCToConversation(Player player, NPC npc) {
        UUID playerUUID = player.getUniqueId();
        String npcName = npc.getName();
        GroupConversation conversation = getConversationByPlayer(player);

        if (conversation == null || !conversation.isActive()) {
            player.sendMessage(ChatColor.RED + "You are not currently in an active conversation.");
            return;
        }

        if (conversation.addNPC(npc)) {
            player.sendMessage(ChatColor.GRAY + npcName + " has joined the conversation.");
            conversation.addMessage(new ConversationMessage("system", npcName + " has joined to the conversation."));
        }
    }

    public void removeNPCFromConversation(Player player, NPC npc, boolean anyNearbyNPC) {
        UUID playerUUID = player.getUniqueId();
        String npcName = npc.getName();
        GroupConversation conversation = activeConversations.values().stream()
                .filter(convo -> convo.getPlayers().contains(playerUUID))
                .findFirst().orElse(null);

        if (conversation == null || !conversation.isActive()) {
            player.sendMessage(ChatColor.RED + "You are not currently in an active conversation.");
            return;
        }

        if (conversation.getNpcNames().size() != 1 && conversation.removeNPC(npc)) {
            player.sendMessage(ChatColor.GRAY + npcName + " has left the conversation.");

            // If only one npc leaves, summarize the conversation for them
            conversation.addMessage(new ConversationMessage("system", npcName + " has left the conversation."));
            summarizeForSingleNPC(conversation.getConversationHistory(), conversation.getNpcNames(), player.getName(), npcName);



        }
        else if (conversation.getNpcNames().size() == 1 && !anyNearbyNPC) {
            endConversation(player);
        } else if (conversation.getNpcNames().size() == 1 && anyNearbyNPC) {
            player.sendMessage(ChatColor.GRAY + npcName + " has left the conversation.");
            conversation.addMessage(new ConversationMessage("system", npcName + " has left the conversation."));
            summarizeForSingleNPC(conversation.getConversationHistory(), conversation.getNpcNames(), player.getName(), npcName);
            conversation.removeNPC(npc);
        }
        else {
            player.sendMessage(ChatColor.YELLOW + npcName + " is not part of the conversation.");
        }
    }

    public void endConversation(GroupConversation conversation) {
        if (conversation != null && conversation.isActive()) {
            conversation.setActive(false);

            // Get location name
            String locationName = "Village"; // Default location
            List<NPC> npcs = conversation.getNPCs();
            if (!npcs.isEmpty()) {
                NPC firstNPC = npcs.get(0);
                if (firstNPC != null) {
                    NPCUtils.NPCContext context = NPCUtils.getInstance(plugin).getOrCreateContextForNPC(firstNPC.getName());
                    if (context != null && context.location != null) {
                        locationName = context.location.getName();
                    }
                }
            }

            // Existing functionality
            summarizeConversation(conversation.getConversationHistory(), conversation.getNpcNames(), null);
            applyEffects(conversation.getConversationHistory(), conversation.getNpcNames(), null);

            // New: Process the conversation for rumors and personal knowledge
            RumorManager.getInstance(plugin).processConversationSignificance(
                    conversation.getConversationHistory(),
                    conversation.getNpcNames(),
                    locationName
            );

            removeConversation(conversation);
        }
    }

    /**
     * Adds an NPC to an existing conversation
     * @param npc The NPC to add
     * @param conversation The conversation to add the NPC to
     * @param greetingMessage Optional greeting message (can be null)
     * @return True if successful, false otherwise
     */
    /**
     * Adds an NPC to an existing conversation by making the NPC walk to the conversation location first
     *
     * @param npc The NPC to add to the conversation
     * @param conversation The conversation to add the NPC to
     * @param greetingMessage Optional greeting message for the NPC to say when joining (can be null)
     * @return true if the NPC was added successfully, false otherwise
     */
    public boolean addNPCToConversationWalk(NPC npc, GroupConversation conversation, String greetingMessage) {
        if (npc == null || !npc.isSpawned() || conversation == null || !conversation.isActive()) {
            return false;
        }

        // Check if NPC is already in this conversation
        if (conversation.getNpcNames().contains(npc.getName())) {
            return false;
        }

        // Get the NPCContext for avatar and color information
        NPCUtils.NPCContext npcContext = plugin.npcUtils.getOrCreateContextForNPC(npc.getName());

        // Find a target location within the conversation
        // First try to find a player in the conversation to walk to
        Location targetLocation = null;
        Player targetPlayer = null;

        for (UUID playerUUID : conversation.getPlayers()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                targetLocation = player.getLocation();
                targetPlayer = player;
                break;
            }
        }

        // If no player is found, try to find another NPC in the conversation
        if (targetLocation == null) {
            for (String npcName : conversation.getNpcNames()) {
                NPC targetNPC = plugin.getNPCByName(npcName);
                if (targetNPC != null && targetNPC.isSpawned() && !targetNPC.equals(npc)) {
                    targetLocation = targetNPC.getEntity().getLocation();
                    break;
                }
            }
        }

        // If we still don't have a target location, return false
        if (targetLocation == null) {
            return false;
        }

        // Make a final reference for use in lambdas
        final Location finalTargetLocation = targetLocation;
        final Player finalTargetPlayer = targetPlayer;

        // Make NPC walk to the conversation location
        Bukkit.getScheduler().runTask(plugin, () -> {
            Navigator navigator = npc.getNavigator();
            navigator.setTarget(finalTargetLocation);
            navigator.getDefaultParameters().distanceMargin(2.0);

            // Listen for navigation completion
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onNavigationComplete(NavigationCompleteEvent event) {
                    if (event.getNPC().equals(npc)) {
                        // Unregister listener after completion
                        NavigationCompleteEvent.getHandlerList().unregister(this);

                        // Add NPC to conversation
                        conversation.addNPC(npc);

                        // System message about NPC joining
                        String joinMessage = npc.getName() + " has joined the conversation.";
                        conversation.addMessage(new Story.ConversationMessage("system", joinMessage));

                        String finalGreetingMessage = greetingMessage;

                        // if greeting null, create one
                        if (finalGreetingMessage == null || finalGreetingMessage.isEmpty()) {
                            finalGreetingMessage = generateNPCGreeting(npc, conversation);
                        }

                        // Handle greeting message if provided
                        if (finalGreetingMessage != null && !finalGreetingMessage.isEmpty()) {
                            // Add NPC's greeting to the conversation
                            conversation.addMessage(new Story.ConversationMessage(
                                    "assistant", npc.getName() + ": " + finalGreetingMessage));

                            // Broadcast the greeting message
                            plugin.broadcastNPCMessage(
                                    finalGreetingMessage,
                                    npc.getName(),
                                    false,
                                    npc,
                                    finalTargetPlayer != null ? finalTargetPlayer.getUniqueId() : null,
                                    finalTargetPlayer,
                                    npcContext.avatar,
                                    plugin.randomColor(npc.getName())
                            );
                        }

                        // Generate a response from the other NPCs in the conversation
                        generateGroupNPCResponses(conversation, null);
                    }
                }
            }, plugin);
        });

        return true;
    }

    /**
     * Adds an NPC's greeting to a conversation and triggers responses
     */
    private void addNPCGreeting(NPC npc, GroupConversation conversation, String greeting) {
        String npcName = npc.getName();

        // Add the greeting to conversation history
        conversation.addMessage(new Story.ConversationMessage(npcName, greeting));
        NPCUtils.NPCContext npcContext = plugin.npcUtils.getOrCreateContextForNPC(npcName);

        // Broadcast the greeting message
        Bukkit.getScheduler().runTask(plugin, () -> {
            String colorCode = plugin.randomColor(npcName);
            plugin.broadcastNPCMessage(greeting, npcName, false, npc, null, null, npcContext.avatar, colorCode);

            // Generate responses from other NPCs in the conversation
            generateGroupNPCResponses(conversation, null);
        });
    }

    /**
     * Generate a greeting for an NPC joining a conversation
     */
    private String generateNPCGreeting(NPC npc, GroupConversation conversation) {
        String npcName = npc.getName();
        NPCUtils.NPCContext npcContext = plugin.npcUtils.getOrCreateContextForNPC(npcName);

        // Build the list of existing participants
        StringBuilder participantsStr = new StringBuilder();
        for (UUID player : conversation.getPlayers()) {
            Player playerObj = Bukkit.getPlayer(player);
            String playerName = playerObj != null ? EssentialsUtils.getNickname(playerObj.getName()) : "Unknown Player";
            participantsStr.append(playerName).append(", ");
        }
        for (NPC existingNPC : conversation.getNPCs()) {
            if (!existingNPC.equals(npc)) {
                participantsStr.append(existingNPC.getName()).append(", ");
            }
        }
        if (participantsStr.length() > 2) {
            participantsStr.setLength(participantsStr.length() - 2); // Remove trailing comma and space
        }

        // Create prompt for generating greeting
        List<Story.ConversationMessage> promptMessages = new ArrayList<>();

        // Add system context
        promptMessages.add(new Story.ConversationMessage("system",
                "You are " + npcName + ". " + npcContext.context +
                        "\n\nYou are joining an ongoing conversation with: " + participantsStr.toString() +
                        "\n\nGenerate a greeting or introduction that acknowledges the ongoing conversation. " +
                        "Keep it brief and in-character. Don't use quotation marks or indicate who is speaking."));

        // Add recent conversation history for context
        List<Story.ConversationMessage> recentHistory = conversation.getConversationHistory()
                .subList(Math.max(conversation.getConversationHistory().size() - 10, 0), conversation.getConversationHistory().size());
        for (Story.ConversationMessage msg : recentHistory) {
            promptMessages.add(new Story.ConversationMessage("user", msg.getRole() + ": " + msg.getContent()));
        }

        // Add final instruction
        promptMessages.add(new Story.ConversationMessage("user",
                "Write a single greeting or introduction line as " + npcName + " joining this conversation."));

        // Generate the greeting
        try {
            String greeting = plugin.getAIResponse(promptMessages);
            return greeting != null ? greeting.trim() : null;
        } catch (Exception e) {
            plugin.getLogger().severe("Error generating NPC greeting: " + e.getMessage());
            return null;
        }
    }

    // Do the same change for the Player version of endConversation
    public void endConversation(Player player) {
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = getConversationByPlayer(player);

        if (conversation != null && conversation.isActive()) {
            conversation.setActive(false);

            // Get location name
            String locationName = "Village"; // Default location
            List<NPC> npcs = conversation.getNPCs();
            if (!npcs.isEmpty()) {
                NPC firstNPC = npcs.get(0);
                if (firstNPC != null) {
                    NPCUtils.NPCContext context = NPCUtils.getInstance(plugin).getOrCreateContextForNPC(firstNPC.getName());
                    if (context != null && context.location != null) {
                        locationName = context.location.getName();
                    }
                }
            }

            // Summarize the conversation (existing functionality)
            summarizeConversation(conversation.getConversationHistory(), conversation.getNpcNames(), player.getName());
            applyEffects(conversation.getConversationHistory(), conversation.getNpcNames(), player.getName());

            // New: Process the conversation for rumors and personal knowledge
            RumorManager.getInstance(plugin).processConversationSignificance(
                    conversation.getConversationHistory(),
                    conversation.getNpcNames(),
                    locationName
            );

            removeConversation(conversation);
            player.sendMessage(ChatColor.GRAY + "The conversation has ended.");
        } else {
            activeConversations.values().removeIf(convo -> !convo.isActive());
        }
    }

    // Add a player's message to the group conversation
    public void addPlayerMessage(Player player, String message, boolean chatEnabled) {
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = getConversationByPlayer(player);

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

        if (!chatEnabled) {
            return;
        }

        // Cancel any existing scheduled tasks for this conversation
        Integer existingTaskId = scheduledTasks.get(conversation);
        if (existingTaskId != null) {
            Bukkit.getScheduler().cancelTask(existingTaskId);
        }

        // Schedule a new task to generate responses after 5 seconds
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Generate responses from all NPCs sequentially
            generateGroupNPCResponses(conversation, player);
            // Remove from scheduled tasks map since it's now executing
            scheduledTasks.remove(conversation);
        }, plugin.getResponseDelay() * 20L).getTaskId(); // 5 seconds * 20 ticks/second

        // Store the task ID
        scheduledTasks.put(conversation, taskId);
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
        GroupConversation conversation = getConversationByPlayer(player);
        return conversation != null && conversation.isActive() && conversation.getNpcNames().contains(npcName);
    }

    public boolean isNPCInConversation(String npcName) {
        for (GroupConversation conversation : activeConversations.values()) {
            if (conversation.getNpcNames().contains(npcName)) {
                return true;
            }
        }
        return false;
    }

    public boolean addPlayerToConversation(Player player, String npcName) {
        // Join the player to another player's conversation
        for (GroupConversation conversation : activeConversations.values()) {
            if (conversation.getNpcNames().contains(npcName)) {
                if (conversation.addPlayerToConversation(player)) {
                    player.sendMessage(ChatColor.GRAY + "You joined the conversation with " + npcName);
                    return true;
                } else {
                    player.sendMessage(ChatColor.YELLOW + "You are already part of the conversation with " + npcName);
                    return false;
                }
            }
        }
        return false;
    }

    public boolean isPlayerInConversation(Player player) {
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = getConversationByPlayer(player);
        return conversation != null && conversation.isActive();
    }

    public void addNPCMessage(String npcName, String message) {
        for (GroupConversation conversation : activeConversations.values()) {
            if (conversation.getNpcNames().contains(npcName)) {
                conversation.addMessage(new ConversationMessage("assistant", npcName + ": " + message));
                conversation.addMessage(new ConversationMessage("user", "*Rest are listening...*"));
                return;
            }
        }
    }

    // return all npc names in conversation
    public List<NPC> getNPCsInConversation(Player player) {
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = getConversationByPlayer(player);
        return conversation != null ? conversation.getNPCs() : new ArrayList<>();
    }

    public List<String> getAllParticipantsInConversation(Player player) {
        // get both all npc names and player names in conversation
        UUID playerUUID = player.getUniqueId();
        GroupConversation conversation = getConversationByPlayer(player);
        List<String> npcNames = conversation != null ? conversation.getNpcNames() : new ArrayList<>();
        List<String> playerNames = conversation != null ? conversation.getPlayers().stream()
                .map(uuid -> {
                    Player playerIns = Bukkit.getPlayer(uuid);
                    return playerIns != null ? EssentialsUtils.getNickname(playerIns.getName()) : "";
                })
                .collect(Collectors.toList()) : new ArrayList<>();
        npcNames.addAll(playerNames);
        return npcNames;
    }

    public GroupConversation getActiveNPCConversation(String npcName) {
        return activeConversations.values().stream()
                .filter(conversation -> conversation.getNpcNames().contains(npcName))
                .findFirst().orElse(null);
    }

    // Generate NPC responses sequentially for the group conversation
    public CompletableFuture<Void> generateGroupNPCResponses(GroupConversation conversation, Player player) {
        List<String> npcNames = conversation.getNpcNames();
        List<NPC> npcs = conversation.getNPCs();

        List<CompletableFuture<Void>> responseFutures = new ArrayList<>();

        for (int i = 0; i < npcNames.size(); i++) {
            String npcName = npcNames.get(i);
            int delay = i * 3;

            if (plugin.isNPCDisabled(npcName)) continue;

            CompletableFuture<Void> future = new CompletableFuture<>();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                npcs.stream().filter(npc -> npc.getName().equals(npcName)).findFirst().ifPresent(npc -> {
                    showThinkingHolo(npc);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        CompletableFuture.runAsync(() -> {
                            try {
                                // --- AI logic and setup code ---
                                NPCUtils.NPCContext NPCContext = NPCUtils.getInstance(plugin).getOrCreateContextForNPC(npcName);

                                List<ConversationMessage> tempHistory = new ArrayList<>(conversation.getConversationHistory());
                                List<String> playerNames = conversation.getPlayers().stream()
                                        .map(uuid -> {
                                            Player playerIns = Bukkit.getPlayer(uuid);
                                            return playerIns != null ? EssentialsUtils.getNickname(playerIns.getName()) : "";
                                        })
                                        .collect(Collectors.toList());

                                plugin.getGeneralContexts().forEach(context ->
                                        tempHistory.addFirst(new ConversationMessage("system", context))
                                );

                                if (NPCContext.location != null)
                                    NPCContext.location.getContext().forEach(context ->
                                            tempHistory.addFirst(new ConversationMessage("system", context))
                                    );

                                List<ConversationMessage> lastTwentyMessages = NPCContext.conversationHistory.subList(
                                        Math.max(NPCContext.conversationHistory.size() - 10, 0),
                                        NPCContext.conversationHistory.size());
                                lastTwentyMessages.add(0, NPCContext.conversationHistory.get(0));
                                tempHistory.addAll(0, lastTwentyMessages);

                                tempHistory.add(new ConversationMessage("system",
                                        "You are " + npcName + ". You are currently in a conversation with: " +
                                                String.join(", ", npcNames) + " and " + String.join(", ", playerNames) + "." +
                                                " This is YOUR turn to speak. Do NOT generate dialogue for others. " +
                                                "Address the relevant character(s) naturally based on previous dialogue."));

                                tempHistory.add(new ConversationMessage("system", "Relations: " + NPCContext.relations.toString()));
                                tempHistory.add(new ConversationMessage("system", "Your responses should reflect your relations with the other characters if applicable. Never print out the relation as dialogue."));

                                String aiResponse = plugin.getAIResponse(tempHistory);

                                // --- Post-AI logic ---
                                DHAPI.removeHologram(npc.getUniqueId().toString());

                                Integer taskId = hologramTasks.get(npcName);
                                if (taskId != null) {
                                    Bukkit.getScheduler().cancelTask(taskId);
                                    hologramTasks.remove(npcName);
                                }

                                if (aiResponse == null || aiResponse.isEmpty()) {
                                    plugin.getLogger().warning("Failed to generate NPC response for " + npcName);
                                    future.complete(null);
                                    return;
                                }

                                Pattern npcNamePattern = Pattern.compile("^([\\w\\s']+):(?:\\s*\\1:)?");
                                Matcher matcher = npcNamePattern.matcher(aiResponse);
                                String finalNpcName = matcher.find() ? matcher.group(1) : npcName;

                                conversation.addMessage(new ConversationMessage("assistant", npcName + ": " + aiResponse));
                                conversation.addMessage(new ConversationMessage("user", "..."));

                                if (aiResponse.contains("[End]") && player != null) {
                                    endConversation(player);
                                }

                                String colorCode = plugin.randomColor(finalNpcName);
                                Bukkit.getScheduler().runTask(plugin, () ->
                                        plugin.broadcastNPCMessage(aiResponse, npcName, false, npc, null, null, NPCContext.avatar, colorCode)
                                );

                                future.complete(null);

                            } catch (Exception e) {
                                plugin.getLogger().warning("Error generating NPC response for " + npcName);
                                e.printStackTrace();
                                future.completeExceptionally(e);
                            }
                        });
                    }, 3 * 20L);
                });
            }, delay * 20L);

            responseFutures.add(future);
        }

        return CompletableFuture.allOf(responseFutures.toArray(new CompletableFuture[0]));
    }



    public void showThinkingHolo(NPC npc) {
        String npcName = npc.getName();

        if (!npc.isSpawned() || npc.getEntity() == null) return;

        Location npcPos = npc.getEntity().getLocation().clone().add(0, 2.10, 0);
        String npcUUID = npc.getUniqueId().toString();

        Hologram holo = DHAPI.getHologram(npcUUID);
        if (holo != null) DHAPI.removeHologram(npcUUID);

        holo = DHAPI.createHologram(npcUUID, npcPos);
        DHAPI.addHologramLine(holo, 0, "&7&othinking...");
        DHAPI.updateHologram(npcUUID);

        Hologram finalHolo = holo;

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!npc.isSpawned() || npc.getEntity() == null) {
                // Remove the hologram if the NPC is gone
                DHAPI.removeHologram(npc.getUniqueId().toString());

                // Check if the task ID exists in the map before canceling
                Integer taskToCancel = hologramTasks.get(npcName);
                if (taskToCancel != null) {
                    Bukkit.getScheduler().cancelTask(taskToCancel);
                    hologramTasks.remove(npcName);
                }
                return;
            }

            Location updatedPos = npc.getEntity().getLocation().clone().add(0, 2.10, 0);
            DHAPI.moveHologram(finalHolo, updatedPos);
        }, 0L, 5L).getTaskId();

        hologramTasks.put(npcName, taskId);
    }


    public void addSystemMessage(GroupConversation conversation, String message) {
        conversation.addMessage(new ConversationMessage("system", message));
    }



    // Summarize the conversation history
// Summarize the conversation history
    private void summarizeConversation(List<ConversationMessage> history, List<String> npcNames, String playerName) {
        if (history.isEmpty() || history.size() < 3) return; // Skip trivial conversations

        // List of prompts to be sent to the AI
        List<ConversationMessage> prompts = new ArrayList<>();

        // Add the conversation history
        prompts.addAll(history);

        // Create a more direct prompt for summarization with clear output formatting requirements
        prompts.add(new ConversationMessage("system", """
        Summarize this conversation concisely and chronologically, focusing on key information and events.
        Analyze what happened and rate the conversation's significance on a scale of 0-10:
        - 0-2: Not significant (greetings, small talk with no useful information)
        - 3-5: Somewhat significant (basic information shared)
        - 6-8: Significant (meaningful interaction, relationship development)
        - 9-10: Highly significant (major revelations, critical information)
        
        Format your response exactly like this:
        [SUMMARY]
        Your actual summary text here...
        [SIGNIFICANCE: X]
        
        Where X is the numeric significance rating (0-10).
        Both sections are required.
        """));

        // Generate summary using AI
        CompletableFuture.runAsync(() -> {
            try {
                String summaryResult = plugin.getAIResponse(prompts);
                if (summaryResult != null && !summaryResult.isEmpty()) {
                    // Extract summary and significance
                    String summary = "";
                    int significance = 5; // Default to middle rating

                    // Extract the summary section
                    Pattern summaryPattern = Pattern.compile("\\[SUMMARY\\](.*?)(?:\\[SIGNIFICANCE|$)", Pattern.DOTALL);
                    Matcher summaryMatcher = summaryPattern.matcher(summaryResult);
                    if (summaryMatcher.find()) {
                        summary = summaryMatcher.group(1).trim();
                    } else {
                        // If formatting failed, use the whole response
                        summary = summaryResult;
                    }

                    // Extract significance rating
                    Pattern significancePattern = Pattern.compile("\\[SIGNIFICANCE:\\s*(\\d+)\\]");
                    Matcher significanceMatcher = significancePattern.matcher(summaryResult);
                    if (significanceMatcher.find()) {
                        significance = Integer.parseInt(significanceMatcher.group(1));
                    }

                    plugin.getLogger().info("Conversation summary significance: " + significance);

                    // Only add significant conversations to NPC memory
                    if (significance > 2) {
                        for (String npcName : npcNames) {
                            plugin.addSystemMessage(npcName, summary);
                        }
                        plugin.getLogger().info("Added significant conversation to memory");
                    } else {
                        plugin.getLogger().info("Skipped adding insignificant conversation to memory");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                plugin.getLogger().severe("Error occurred while summarizing conversation: " + e.getMessage());
            }
        });
    }

    private void applyEffects(List<ConversationMessage> history, List<String> npcNames, String playerName){
        if (history.isEmpty()) return;

        // Build the summary prompt
        StringBuilder prompt = new StringBuilder("Apply effects of this conversation between ");
        prompt.append(playerName).append(" and ").append(String.join(", ", npcNames)).append(".\n");

        // instructions on how to give effects
        prompt.append("To apply effects, output the effects in the following format: \n");
        prompt.append("Character: <name> possible values: [name of the npc] \n");
        prompt.append("Effect: <effect name> possible values: [relation] \n");
        prompt.append("Target: <target name> possible values: ").append(playerName).append("\n");
        prompt.append("relation: -20, 20 (only change as much needed) \n");

        prompt.append("Example: \n");
        prompt.append("Conversation summarisation: Player helps NPC greatly, which gains trust. \n");
        prompt.append("Effect: relation Target: player Value: 10 \n");
        prompt.append("Here's the conversation, apply effects only if necessary: \n");

        for (ConversationMessage msg : history) {
            prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        // Generate summary using AI
        CompletableFuture.runAsync(() -> {
            try {
                for (String npcName : npcNames) {
                    String effectsOutput = plugin.getAIResponse(Collections.singletonList(
                            new ConversationMessage("system", prompt.toString())
                    ));
                    if (effectsOutput != null && !effectsOutput.isEmpty()) {
                        effectsOutputParser(effectsOutput);
                    } else {
                        plugin.getLogger().warning("Failed to apply effects of the conversation.");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                plugin.getLogger().severe("Error occurred while applying effects of the conversation.");
            }
        });



    }

    private void summarizeForSingleNPC(List<ConversationMessage> history, List<String> npcNames, String playerName, String npcName){
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
                    plugin.addSystemMessage(npcName, summary);
                } else {
                    plugin.getLogger().warning("Failed to summarize the conversation.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                plugin.getLogger().severe("Error occurred while summarizing the conversation.");
            }
        });
    }

    private void effectsOutputParser(String effectsOutput) {
        String[] lines = effectsOutput.split("\n");
        String npcName = null;
        String effect = null;
        String target = null;
        String value = null;

        for (String line : lines) {
            // Check for a new character block
            if (line.startsWith("Character:")) {
                npcName = line.split(":")[1].trim();
            } else if (line.startsWith("Effect:")) {
                effect = line.split(":")[1].trim();
            } else if (line.startsWith("Target:")) {
                target = line.split(":")[1].trim();
            } else if (line.startsWith("Value:")) {
                value = line.split(":")[1].trim();
            }

            // When all necessary variables are set, process the effect
            if (npcName != null && effect != null && target != null && value != null) {
                switch (effect) {
                    case "relation":
                        plugin.saveNPCRelationValue(npcName, target, value);
                        break;
                    case "title":
                        //plugin.broadcastNPCMessage("Title of " + target + " has changed to " + value, npcName, false, null, null, null, "#599B45");
                        break;
                    case "item":
                        //plugin.broadcastNPCMessage("Item " + value + " has been given to " + target, npcName, false, null, null, null, "#599B45");
                        break;
                    default:
                        System.out.println("Unknown effect: " + effect);
                        break;
                }
                // Reset effect-specific variables for the next effect
                effect = null;
                target = null;
                value = null;
            }
        }
    }


    // Check if a player has an active conversation
    public boolean hasActiveConversation(Player player) {
        GroupConversation conversation = getConversationByPlayer(player);
        return conversation != null && conversation.isActive();
    }

    public GroupConversation getActiveConversation(Player player) {
        return getConversationByPlayer(player);
    }
}
