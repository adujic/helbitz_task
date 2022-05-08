package com.test.helbitz.task.model.api;

import java.util.ArrayList;

public class FbiResponseItem{
    private String title;
    private ArrayList<String> aliases;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ArrayList<String> getAliases() {
        return aliases;
    }

    public void setAliases(ArrayList<String> aliases) {
        this.aliases = aliases;
    }
}
