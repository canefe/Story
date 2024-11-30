package com.canefe.story;

import java.util.List;

public class ConversationState {
    private final List<String> npcList;
    private boolean active;

    public ConversationState(List<String> npcList, boolean active) {
        this.npcList = npcList;
        this.active = active;
    }

    public List<String> getNPCList() {
        return npcList;
    }

    public void addNPC(String npcName) {
        npcList.add(npcName);
    }

    public void removeNPC(String npcName) {
        npcList.remove(npcName);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
