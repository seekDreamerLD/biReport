package com.bireport.service.query;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QueryResult {

    private List<Column> columns = new ArrayList<>();
    private List<List<Object>> rows = new ArrayList<>();
    private long costMs;

    @Data
    public static class Column {
        private String name;
        private String type;

        public Column() {
        }

        public Column(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }
}
