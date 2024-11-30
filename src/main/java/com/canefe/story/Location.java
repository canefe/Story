package com.canefe.story;

import java.util.List;

public class Location {
    private final String name;
    private final List<String> context;

    public Location(String name, List<String> context) {
        this.name = name;
        this.context = context;
    }

    public String getName() {
        return name;
    }

    public List<String> getContext() {
        return context;
    }

}
