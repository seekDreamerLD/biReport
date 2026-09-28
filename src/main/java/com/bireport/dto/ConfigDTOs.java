package com.bireport.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigDTOs {

    /** 数据集字段元数据 */
    @Data
    public static class DatasetField {
        private String name;
        private String label;
        /** dimension / measure */
        private String fieldType;
        /** string / number / date */
        private String dataType;
        /** sum / avg / count / countDistinct / max / min */
        private String defaultAgg;
    }

    @Data
    public static class ChartDim {
        private String field;
        private String label;
        /** 日期维度聚合粒度: year/quarter/month/week/day，非日期字段为空 */
        private String dateLevel;
    }

    @Data
    public static class ChartMeasure {
        private String field;
        private String label;
        private String agg = "sum";
    }

    @Data
    public static class DataFilter {
        private String field;
        /** eq/ne/in/notIn/like/gt/gte/lt/lte/between */
        private String op;
        private List<Object> values = new ArrayList<>();
    }

    @Data
    public static class ChartConfig {
        /** bar/line/area/pie/table/kpi/detail */
        private String chartType = "bar";
        private List<ChartDim> dims = new ArrayList<>();
        private List<ChartMeasure> measures = new ArrayList<>();
        private List<DataFilter> filters = new ArrayList<>();
        /** {field, dir: asc/desc} */
        private Map<String, String> sort;
        private Integer limit = 1000;
        private Map<String, Object> style;
        /** detail 表格展示的字段 */
        private List<String> detailFields = new ArrayList<>();
    }

    @Data
    public static class QueryReq {
        private Long chartId;
        private Long datasetId;
        /** 仪表板运行时叠加的外部筛选（全局筛选/联动/下钻） */
        private List<DataFilter> extraFilters = new ArrayList<>();
        /** 公开嵌入场景携带的 share token */
        private String shareToken;
    }
}
