package com.canefe.story;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.event.NavigationCompleteEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;

public class NPCManager {

    private static NPCManager instance; // Singleton instance
    private final Story plugin;
    private final Map<String, NPCData> npcDataMap = new HashMap<>(); // Centralized NPC storage
    private final Map<GroupConversation, Integer> scheduledTasks = new HashMap<>();

    private NPCManager(Story plugin) {
        // Private constructor to enforce singleton pattern
        this.plugin = plugin;
    }

    // Get the singleton instance
    public static synchronized NPCManager getInstance(Story plugin) {
        if (instance == null) {
            instance = new NPCManager(plugin);
        }
        return instance;
    }

    public void eventGoToPlayerAndTalk(NPC npc, Player player, String message) {
        // Get NPC's context
        NPCUtils.NPCContext npcContext = plugin.npcUtils.getOrCreateContextForNPC(npc.getName());

        // Switch to the main thread for Citizens API calls
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Create a task ID holder
            final int[] taskId = {-1};

            // Register event listener for navigation completion
            Listener navigationListener = new Listener() {
                @EventHandler
                public void onNavigationComplete(NavigationCompleteEvent event) {
                    if (event.getNPC().equals(npc)) {
                        // Cancel the location update task when navigation completes
                        if (taskId[0] != -1) {
                            Bukkit.getScheduler().cancelTask(taskId[0]);
                        }

                        // Unregister the listener after completion
                        NavigationCompleteEvent.getHandlerList().unregister(this);

                        // Start the conversation once NPC reaches the player
                        List<NPC> npcs = new ArrayList<>();
                        npcs.add(npc);
                        GroupConversation conversation = plugin.conversationManager.startGroupConversation(player, npcs);

                        if (conversation == null) return;

                        // Add system message about the NPC initiating conversation
                        conversation.addMessage(new Story.ConversationMessage("system",
                                npc.getName() + " approached you and started a conversation."));

                        // Add NPC message to conversation
                        conversation.addMessage(new Story.ConversationMessage("assistant", npc.getName() + ": " + message));

                        // Broadcast message
                        plugin.broadcastNPCMessage(message, npc.getName(), false, npc, player.getUniqueId(),
                                player, npcContext.avatar, plugin.randomColor(npc.getName()));

                        // Schedule proximity check
                        plugin.scheduleProximityCheck(player, npc, conversation);
                    }
                }
            };

            // Register navigation listener
            Bukkit.getPluginManager().registerEvents(navigationListener, plugin);

            // Initial navigation setup
            Navigator navigator = npc.getNavigator();
            navigator.getDefaultParameters().distanceMargin(2.0); // Stop 2 blocks away
            navigator.setTarget(player.getLocation());

            // Create a task that periodically updates the navigation target
            taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (!player.isOnline() || !npc.isSpawned()) {
                    // Cancel task and unregister listener if player or NPC is no longer valid
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    NavigationCompleteEvent.getHandlerList().unregister(navigationListener);
                    return;
                }

                // Check if we're already close enough
                if (npc.getEntity().getLocation().distance(player.getLocation()) <= 3.0) {
                    // Trigger navigation complete manually
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    NavigationCompleteEvent.getHandlerList().unregister(navigationListener);

                    // Start conversation
                    List<NPC> npcs = new ArrayList<>();
                    npcs.add(npc);
                    GroupConversation conversation = plugin.conversationManager.startGroupConversation(player, npcs);

                    if (conversation != null) {
                        conversation.addMessage(new Story.ConversationMessage("system",
                                npc.getName() + " approached you and started a conversation."));
                        conversation.addMessage(new Story.ConversationMessage("assistant", npc.getName() + ": " + message));
                        plugin.broadcastNPCMessage(message, npc.getName(), false, npc, player.getUniqueId(),
                                player, npcContext.avatar, plugin.randomColor(npc.getName()));
                        plugin.scheduleProximityCheck(player, npc, conversation);
                    }
                    return;
                }

                // Update target location if player moved
                navigator.setTarget(player.getLocation());
            }, 10L, 20L); // Check every second (20 ticks)
        });
    }

    public void eventGoToPlayerAndSay(NPC npc, String playerName, String message) {
        // Asynchronously get the NPC
            // Get the player by name
            Player player = Bukkit.getPlayerExact(playerName);
            if (player == null || !player.isOnline()) {
                Bukkit.getLogger().warning("Player with name '" + playerName + "' is not online!");
                return;
            }
            NPCUtils.NPCContext npcContext = plugin.npcUtils.getOrCreateContextForNPC(npc.getName());
            // Switch back to the main thread for Citizens API calls
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Get the player's location
                Location playerLocation = player.getLocation();

                // Make the NPC navigate to the player
                Navigator navigator = npc.getNavigator();
                navigator.setTarget(playerLocation); // Set the target location
                navigator.getDefaultParameters().distanceMargin(2.0); // Stop 2 blocks away
                Bukkit.getLogger().info("Navigator target set for NPC: " + npc.getName() + " to player: " + player.getName());

                Listener listener = new Listener() {
                    @EventHandler
                    public void onNavigationComplete(NavigationCompleteEvent event) {
                        if (event.getNPC().equals(npc)) {
                            // Unregister the listener after completion
                            NavigationCompleteEvent.getHandlerList().unregister(this);

                            // Start the conversation once NPC reaches the player
                            List<NPC> npcs = new ArrayList<>();
                            npcs.add(npc);
                            GroupConversation conversation = plugin.conversationManager.startGroupConversation(player, npcs);

                            String colorCode = plugin.randomColor(npc.getName());

                            // Send the message to the player
                            conversation.addMessage(new Story.ConversationMessage("assistant", npc.getName() + ": " + message));
                            plugin.broadcastNPCMessage(message, npc.getName(), false, npc, player.getUniqueId(), player, npcContext.avatar, colorCode);
                        }
                    }
                };
                // Register event listener for navigation completion
                Bukkit.getPluginManager().registerEvents(listener, plugin);
            });
    }



    public void walkToNPC(NPC npc, NPC targetNPC, String firstMessage) {
        // Asynchronously get the NPC
        NPCUtils.NPCContext npcContext = plugin.npcUtils.getOrCreateContextForNPC(npc.getName());
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Get the target NPC's location
            Location targetLocation = targetNPC.getEntity().getLocation();

            // Make the NPC navigate to the target NPC
            Navigator navigator = npc.getNavigator();
            navigator.setTarget(targetLocation); // Set the target location
            navigator.getDefaultParameters().distanceMargin(2.0); // Stop 2 blocks away

            // Register event listener for navigation completion
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onNavigationComplete(NavigationCompleteEvent event) {
                    if (event.getNPC().equals(npc)) {
                        // Unregister the listener after completion
                        NavigationCompleteEvent.getHandlerList().unregister(this);

                        // Start the conversation once NPC reaches the target NPC
                        List<NPC> npcs = new ArrayList<>();
                        npcs.add(npc);
                        npcs.add(targetNPC);
                        GroupConversation conversation = plugin.conversationManager.startGroupConversationNoPlayer(npcs);
                        // Send the message to the player
                        conversation.addMessage(new Story.ConversationMessage("system", npc.getName() + ": " + firstMessage));
                        conversation.addMessage(new Story.ConversationMessage("user", targetNPC.getName() + " is listening..."));

                        String colorCode = plugin.randomColor(npc.getName());

                        plugin.broadcastNPCMessage(firstMessage, npc.getName(), false, npc, null, null, npcContext.avatar, colorCode);

                        plugin.conversationManager.generateRadiantResponses(conversation);


                    }
                }
            }, plugin);
        });

    }

}
