package com.bireport.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bireport.common.BizException;
import com.bireport.dto.ChatBiDTOs.AskReq;
import com.bireport.dto.ChatBiDTOs.ChatAnswer;
import com.bireport.dto.ConfigDTOs.ChartConfig;
import com.bireport.dto.ConfigDTOs.DatasetField;
import com.bireport.entity.Dataset;
import com.bireport.entity.User;
import com.bireport.mapper.DatasetMapper;
import com.bireport.service.ai.ZhipuAiClient;
import com.bireport.service.ai.ZhipuAiClient.Msg;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ChatBI：自然语言问数。
 * LLM 根据数据集字段元数据生成 ChartConfig JSON（不是裸 SQL，可控且安全），
 * 后端校验字段白名单后复用查询引擎执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBiService {

    private final ZhipuAiClient aiClient;
    private final DatasetService datasetService;
    private final DatasetMapper datasetMapper;
    private final QueryService queryService;
    private final ObjectMapper objectMapper;

    public ChatAnswer ask(User user, AskReq req) {
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new BizException("问题不能为空");
        }
        List<Dataset> datasets = datasetMapper.selectList(
                Wrappers.<Dataset>lambdaQuery().orderByAsc(Dataset::getId));
        if (datasets.isEmpty()) {
            throw new BizException("请先创建数据集");
        }
        String datasetContext = buildDatasetContext(datasets);
        String systemPrompt = buildSystemPrompt(datasetContext);
        String userPrompt = req.getDatasetId() != null
                ? req.getQuestion() + "\n（限定使用数据集 id=" + req.getDatasetId() + "）"
                : req.getQuestion();

        List<Msg> messages = List.of(Msg.system(systemPrompt), Msg.user(userPrompt));
        String raw = aiClient.chat(messages);
        JsonNode plan = extractJson(raw);

        ChatAnswer answer;
        try {
            answer = validateAndRun(user, plan);
        } catch (BizException first) {
            // 纠错重试一次：把失败原因反馈给模型
            log.info("ChatBI 首次生成执行失败，触发纠错重试: {}", first.getMessage());
            List<Msg> retry = new ArrayList<>(messages);
            retry.add(Msg.assistant(raw));
            retry.add(Msg.user("你的 JSON 执行失败，原因：" + first.getMessage()
                    + "\n请严格检查字段名与规则后修正，重新只输出修正后的 JSON 对象。"));
            answer = validateAndRun(user, extractJson(aiClient.chat(retry)));
            answer.setRetried(true);
        }
        answer.setChartName(orDefault(answer.getChartName(), "AI 生成图表"));
        answer.setExplanation(orDefault(answer.getExplanation(), ""));
        return answer;
    }

    private ChatAnswer validateAndRun(User user, JsonNode plan) {
        long datasetId = plan.path("datasetId").asLong(0);
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BizException("AI 选择了不存在的数据集 id=" + datasetId);
        }
        JsonNode configNode = plan.path("config");
        if (!configNode.isObject()) {
            throw new BizException("AI 输出缺少 config 对象");
        }
        ChartConfig config;
        try {
            config = objectMapper.treeToValue(configNode, ChartConfig.class);
        } catch (Exception e) {
            throw new BizException("AI 生成的图表配置格式不合法: " + e.getMessage());
        }
        QueryService.ChartQuery query = queryService.queryByConfig(user, datasetId, config);

        ChatAnswer answer = new ChatAnswer();
        answer.setExplanation(plan.path("explanation").asText(""));
        answer.setChartName(plan.path("chartName").asText(""));
        answer.setDatasetId(datasetId);
        answer.setDatasetName(dataset.getName());
        answer.setConfig(config);
        answer.setResult(query.result());
        answer.setSql(query.sql());
        return answer;
    }

    /** 从模型输出中提取 JSON（容忍 markdown 代码块包裹） */
    private JsonNode extractJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BizException("AI 未返回有效的 JSON 配置");
        }
        try {
            return objectMapper.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            throw new BizException("AI 返回的 JSON 解析失败");
        }
    }

    private String buildDatasetContext(List<Dataset> datasets) {
        StringBuilder sb = new StringBuilder();
        for (Dataset d : datasets) {
            sb.append("- 数据集 id=").append(d.getId()).append("，名称：").append(d.getName()).append("，字段：");
            List<DatasetField> fields = datasetService.parseFields(d.getFieldsJson());
            List<String> parts = new ArrayList<>();
            for (DatasetField f : fields) {
                StringBuilder p = new StringBuilder(f.getFieldType().equals("measure") ? "[度量]" : "[维度]");
                p.append(f.getName());
                if (f.getLabel() != null && !f.getLabel().equals(f.getName())) {
                    p.append("(").append(f.getLabel()).append(")");
                }
                if (f.getDataType() != null) {
                    p.append("<").append(f.getDataType()).append(">");
                }
                parts.add(p.toString());
            }
            sb.append(String.join("、", parts)).append("\n");
        }
        return sb.toString();
    }

    private String buildSystemPrompt(String datasetContext) {
        String today = LocalDate.now().toString();
        return """
                你是资深的 BI 数据分析师助手。用户会用自然语言提出数据分析问题，你需要选择最合适的一个数据集，并生成图表配置。

                当前日期：%s。

                可用数据集及字段（field 必须严格使用这里的字段名，禁止编造）：
                %s
                输出要求：只输出一个 JSON 对象，不要 markdown 代码块，不要任何解释性文字。结构：
                {
                  "datasetId": 数字,
                  "chartName": "中文图表名(不超过12字)",
                  "explanation": "中文一两句：说明选择思路与结果解读",
                  "config": {
                    "chartType": "bar|line|area|pie|bubble|combo|table|kpi|detail 之一",
                    "dims": [{"field":"字段名","label":"中文标签","dateLevel":"year|quarter|month|week|day（仅日期维度填写）"}],
                    "measures": [{"field":"字段名","label":"中文标签","agg":"sum|avg|count|countDistinct|max|min"}],
                    "filters": [{"field":"字段名","op":"eq|ne|in|notIn|like|gt|gte|lt|lte|between","values":["值"]}],
                    "sort": {"field":"字段名","dir":"asc|desc"},
                    "limit": 1000
                  }
                }

                图表类型选择规则：
                - 时间趋势/随时间变化 → line 或 area；dateLevel 按粒度：近N天用 day、近N月用 month、按年用 year，并按时间字段升序排序
                - 占比/构成 → pie，一个维度 + 一个度量
                - 类别间对比 → bar
                - 两个度量量纲差异大（如金额和百分比、销售额和利润率）→ combo
                - 相关性/分布（X 对 Y 的散布）→ bubble，需要 2~3 个度量（依次为 X、Y、气泡大小）
                - 只问一个总数 → kpi，dims 留空
                - 看明细记录 → detail

                其他规则：
                - 时间范围用 between：values 形如 ["2026-07-01 00:00:00","2026-09-30 23:59:59"]（按当前日期推算）
                - 用户问"销售额/收入/金额"类默认 sum 金额字段；"订单量/次数"用 count 或数量字段 sum
                - sort 用于让图表可读：趋势按时间 asc，对比类按度量 desc
                - dims 的 field 若是日期字段必须带 dateLevel；非日期字段 dateLevel 省略
                """.formatted(today, datasetContext);
    }

    private String orDefault(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }
}
