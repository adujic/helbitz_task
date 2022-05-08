package com.test.helbitz.task.model.api;

import java.util.ArrayList;
import java.util.List;

public class FbiResponse {

    private int total;
    private List<FbiResponseItem> items;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<FbiResponseItem> getItems() {
        return items;
    }

    public void setItems(List<FbiResponseItem> items) {
        this.items = items;
    }
}
