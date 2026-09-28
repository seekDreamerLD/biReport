package com.bireport.dto;

import com.bireport.dto.ConfigDTOs.ChartConfig;
import com.bireport.service.query.QueryResult;
import lombok.Data;

public class ChatBiDTOs {

    @Data
    public static class AskReq {
        private String question;
        /** 可选：限定数据集，不传则由 AI 自主选择 */
        private Long datasetId;
    }

    @Data
    public static class ChatAnswer {
        private String explanation;
        private String chartName;
        private Long datasetId;
        private String datasetName;
        private ChartConfig config;
        private QueryResult result;
        private String sql;
        /** 本次是否经过纠错重试 */
        private boolean retried;
    }
}
