package com.bireport.service;

import com.bireport.dto.ConfigDTOs.ChartDim;
import com.bireport.dto.ConfigDTOs.ChartConfig;
import com.bireport.dto.ConfigDTOs.ChartMeasure;
import com.bireport.dto.ConfigDTOs.DatasetField;
import com.bireport.entity.Chart;
import com.bireport.entity.DataSource;
import com.bireport.entity.Dataset;
import com.bireport.entity.Dashboard;
import com.bireport.entity.User;
import com.bireport.mapper.ChartMapper;
import com.bireport.mapper.DataSourceMapper;
import com.bireport.mapper.DatasetMapper;
import com.bireport.mapper.DashboardMapper;
import com.bireport.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 首次启动初始化：管理员账号、内置演示数据源、电商演示数据（约 8 万订单）、
 * 示例数据集/图表/仪表板，全部幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedService {

    private final UserMapper userMapper;
    private final DataSourceMapper dataSourceMapper;
    private final DatasetMapper datasetMapper;
    private final ChartMapper chartMapper;
    private final DashboardMapper dashboardMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private static final String[][] REGION_CITY = {
            {"华北", "北京市", "北京"}, {"华北", "天津市", "天津"}, {"华北", "河北省", "石家庄"},
            {"华东", "上海市", "上海"}, {"华东", "浙江省", "杭州"}, {"华东", "江苏省", "南京"}, {"华东", "江苏省", "苏州"},
            {"华南", "广东省", "广州"}, {"华南", "广东省", "深圳"}, {"华南", "福建省", "厦门"},
            {"华中", "湖北省", "武汉"}, {"华中", "湖南省", "长沙"}, {"华中", "河南省", "郑州"},
            {"西南", "四川省", "成都"}, {"西南", "重庆市", "重庆"}, {"西南", "云南省", "昆明"},
            {"西北", "陕西省", "西安"}, {"西北", "甘肃省", "兰州"},
            {"东北", "辽宁省", "沈阳"}, {"东北", "辽宁省", "大连"}, {"东北", "黑龙江省", "哈尔滨"}
    };

    private static final String[][] PRODUCTS = {
            {"家电", "变频冰箱", "3299"}, {"家电", "滚筒洗衣机", "2599"}, {"家电", "壁挂空调", "2899"},
            {"家电", "4K智能电视", "4599"}, {"家电", "微波炉", "599"},
            {"数码", "旗舰手机", "5999"}, {"数码", "轻薄笔记本", "7299"}, {"数码", "平板电脑", "3599"},
            {"数码", "降噪耳机", "1299"}, {"数码", "微单相机", "8999"},
            {"服饰", "男士夹克", "499"}, {"服饰", "女士连衣裙", "399"}, {"服饰", "儿童套装", "199"}, {"服饰", "运动跑鞋", "599"},
            {"食品", "每日坚果", "89"}, {"食品", "精品咖啡豆", "129"}, {"食品", "进口饼干", "49"}, {"食品", "有机牛奶", "69"},
            {"美妆", "丝绒口红", "259"}, {"美妆", "补水面膜", "139"}, {"美妆", "淡香精", "499"},
            {"家居", "布艺沙发", "3899"}, {"家居", "乳胶床垫", "2599"}, {"家居", "智能台灯", "199"}, {"家居", "收纳箱套装", "99"}
    };

    private static final String[] CHANNELS = {"线上商城", "线下门店", "小程序", "直播带货"};
    private static final String[] LEVELS = {"普通客户", "白银会员", "黄金会员", "钻石会员"};
    private static final String[] SURNAMES = {"王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴", "徐", "孙", "马", "朱", "胡"};
    private static final String[] GIVEN = {"伟", "芳", "娜", "敏", "静", "磊", "军", "洋", "勇", "艳", "杰", "涛", "明", "超", "霞", "平", "辉", "婷"};

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void seed() {
        try {
            ensureAdmin();
            ensureBuiltinDataSource();
            ensureDemoData();
            ensureSampleContent();
            log.info("Seed 初始化检查完成");
        } catch (Exception e) {
            log.error("Seed 初始化失败", e);
        }
    }

    private void ensureAdmin() {
        Long count = userMapper.selectCount(null);
        if (count == null || count == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setNickname("管理员");
            admin.setRole("admin");
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("已创建默认管理员 admin / admin123");
        }
    }

    private void ensureBuiltinDataSource() {
        Long count = dataSourceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DataSource>()
                        .eq(DataSource::getType, "builtin_demo"));
        if (count == null || count == 0) {
            DataSource ds = new DataSource();
            ds.setName("内置演示数据");
            ds.setType("builtin_demo");
            try {
                ds.setConfigJson(objectMapper.writeValueAsString(Map.of("database", "bi_report")));
            } catch (Exception e) {
                ds.setConfigJson("{}");
            }
            ds.setOwnerId(1L);
            dataSourceMapper.insert(ds);
        }
    }

    private void ensureDemoData() {
        Long exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'demo_orders'",
                Long.class);
        if (exists != null && exists > 0) {
            return;
        }
        log.info("开始生成演示数据（约 8 万订单）...");
        long start = System.currentTimeMillis();

        jdbcTemplate.execute("""
                CREATE TABLE demo_region (
                    region VARCHAR(32), province VARCHAR(32), city VARCHAR(64)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

        jdbcTemplate.execute("""
                CREATE TABLE demo_product (
                    id BIGINT PRIMARY KEY, category VARCHAR(32), product_name VARCHAR(64), price DECIMAL(10,2)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

        jdbcTemplate.execute("""
                CREATE TABLE demo_customer (
                    id BIGINT PRIMARY KEY, customer_name VARCHAR(64), level VARCHAR(32)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

        jdbcTemplate.execute("""
                CREATE TABLE demo_orders (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    order_no VARCHAR(32) NOT NULL,
                    order_date DATE NOT NULL,
                    region VARCHAR(32), province VARCHAR(32), city VARCHAR(64),
                    channel VARCHAR(32), category VARCHAR(32), product_name VARCHAR(64),
                    unit_price DECIMAL(10,2), quantity INT, amount DECIMAL(12,2), profit DECIMAL(12,2),
                    customer_id BIGINT, customer_name VARCHAR(64), customer_level VARCHAR(32),
                    KEY idx_order_date (order_date), KEY idx_region (region), KEY idx_category (category)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

        Random random = new Random(42);

        // region 字典
        List<Object[]> regionRows = new ArrayList<>();
        for (String[] r : REGION_CITY) {
            regionRows.add(new Object[]{r[0], r[1], r[2]});
        }
        jdbcTemplate.batchUpdate("INSERT INTO demo_region VALUES (?,?,?)", regionRows);

        // product 字典
        List<Object[]> productRows = new ArrayList<>();
        for (int i = 0; i < PRODUCTS.length; i++) {
            productRows.add(new Object[]{i + 1, PRODUCTS[i][0], PRODUCTS[i][1], new BigDecimal(PRODUCTS[i][2])});
        }
        jdbcTemplate.batchUpdate("INSERT INTO demo_product VALUES (?,?,?,?)", productRows);

        // customer 字典
        int customerCount = 500;
        List<Object[]> customerRows = new ArrayList<>();
        for (int i = 0; i < customerCount; i++) {
            String name = SURNAMES[random.nextInt(SURNAMES.length)] + GIVEN[random.nextInt(GIVEN.length)]
                    + GIVEN[random.nextInt(GIVEN.length)];
            customerRows.add(new Object[]{i + 1, name, LEVELS[Math.min(3, random.nextInt(10) / 3)]});
        }
        jdbcTemplate.batchUpdate("INSERT INTO demo_customer VALUES (?,?,?)", customerRows);

        // 订单事实表
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 30);
        long days = startDate.datesUntil(endDate.plusDays(1)).count();
        int total = 80_000;
        final int BATCH = 2000;
        List<Object[]> batch = new ArrayList<>(BATCH);
        String insertSql = """
                INSERT INTO demo_orders (order_no, order_date, region, province, city, channel, category,
                    product_name, unit_price, quantity, amount, profit, customer_id, customer_name, customer_level)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";
        long seq = 0;
        for (int i = 0; i < total; i++) {
            LocalDate date = startDate.plusDays(random.nextInt((int) days));
            String[] rc = REGION_CITY[random.nextInt(REGION_CITY.length)];
            String[] product = PRODUCTS[random.nextInt(PRODUCTS.length)];
            int qty = random.nextInt(5) + 1;
            BigDecimal price = new BigDecimal(product[2]);
            // 大促月（6月/11月）销量加权
            if (date.getMonthValue() == 6 || date.getMonthValue() == 11) {
                qty += random.nextInt(3);
            }
            BigDecimal discount = BigDecimal.valueOf(0.80 + random.nextDouble() * 0.20);
            BigDecimal amount = price.multiply(BigDecimal.valueOf(qty)).multiply(discount)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal profit = amount.multiply(BigDecimal.valueOf(0.03 + random.nextDouble() * 0.30))
                    .setScale(2, RoundingMode.HALF_UP);
            int custId = random.nextInt(customerCount) + 1;
            String custName = SURNAMES[random.nextInt(SURNAMES.length)] + GIVEN[random.nextInt(GIVEN.length)]
                    + GIVEN[random.nextInt(GIVEN.length)];
            String level = LEVELS[random.nextInt(100) < 10 ? 3 : random.nextInt(3)];

            seq++;
            batch.add(new Object[]{
                    String.format("SO%08d", seq), date, rc[0], rc[1], rc[2],
                    CHANNELS[random.nextInt(CHANNELS.length)], product[0], product[1],
                    price, qty, amount, profit, (long) custId, custName, level
            });
            if (batch.size() >= BATCH) {
                jdbcTemplate.batchUpdate(insertSql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(insertSql, batch);
        }
        log.info("演示数据生成完成，共 {} 行，耗时 {} ms", total, System.currentTimeMillis() - start);
    }

    private void ensureSampleContent() {
        Long datasetCount = datasetMapper.selectCount(null);
        Long datasetId;
        if (datasetCount == null || datasetCount == 0) {
            DataSource builtin = dataSourceMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DataSource>()
                            .eq(DataSource::getType, "builtin_demo").last("LIMIT 1"));
            if (builtin == null) {
                return;
            }
            List<DatasetField> fields = List.of(
                    field("order_date", "订单日期", "dimension", "date", null),
                    field("region", "大区", "dimension", "string", null),
                    field("province", "省份", "dimension", "string", null),
                    field("city", "城市", "dimension", "string", null),
                    field("channel", "渠道", "dimension", "string", null),
                    field("category", "品类", "dimension", "string", null),
                    field("product_name", "商品", "dimension", "string", null),
                    field("customer_level", "客户等级", "dimension", "string", null),
                    field("customer_name", "客户", "dimension", "string", null),
                    field("order_no", "订单号", "dimension", "string", null),
                    field("quantity", "数量", "measure", "number", "sum"),
                    field("amount", "销售额", "measure", "number", "sum"),
                    field("profit", "利润", "measure", "number", "sum")
            );
            Dataset dataset = new Dataset();
            dataset.setName("电商订单分析");
            dataset.setDatasourceId(builtin.getId());
            dataset.setSqlText("""
                    SELECT id, order_no, order_date, region, province, city, channel, category,
                           product_name, customer_id, customer_name, customer_level,
                           quantity, amount, profit
                    FROM demo_orders""");
            try {
                dataset.setFieldsJson(objectMapper.writeValueAsString(fields));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            dataset.setOwnerId(1L);
            datasetMapper.insert(dataset);
            datasetId = dataset.getId();
        } else {
            datasetId = datasetMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Dataset>()
                                    .orderByAsc(Dataset::getId).last("LIMIT 1")).get(0).getId();
        }

        Long chartCount = chartMapper.selectCount(null);
        Map<String, Long> chartIds = new LinkedHashMap<>();
        if (chartCount == null || chartCount == 0) {
            chartIds.put("kpi", createChart("总销售额 KPI", datasetId, kpiConfig()));
            chartIds.put("trend", createChart("销售额趋势", datasetId, lineConfig("销售额趋势", "amount", "month")));
            chartIds.put("categoryPie", createChart("品类销售占比", datasetId, pieConfig()));
            chartIds.put("regionBar", createChart("各大区销售额", datasetId, barConfig()));
            chartIds.put("profitArea", createChart("利润趋势", datasetId, areaConfig()));
            chartIds.put("channelBar", createChart("各渠道销量", datasetId, channelConfig()));
            chartIds.put("detail", createChart("订单明细", datasetId, detailConfig()));
        } else {
            List<Chart> charts = chartMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Chart>()
                            .orderByAsc(Chart::getId));
            String[] keys = {"kpi", "trend", "categoryPie", "regionBar", "profitArea", "channelBar", "detail"};
            for (int i = 0; i < charts.size() && i < keys.length; i++) {
                chartIds.put(keys[i], charts.get(i).getId());
            }
        }

        Long dashCount = dashboardMapper.selectCount(null);
        if (dashCount == null || dashCount == 0) {
            createSampleDashboard(chartIds);
        }
    }

    private Long createChart(String name, Long datasetId, ChartConfig config) {
        Chart chart = new Chart();
        chart.setName(name);
        chart.setDatasetId(datasetId);
        try {
            chart.setConfigJson(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        chart.setOwnerId(1L);
        chartMapper.insert(chart);
        return chart.getId();
    }

    private void createSampleDashboard(Map<String, Long> ids) {
        try {
            List<Map<String, Object>> comps = new ArrayList<>();
            comps.add(comp("title", "text", 460, 18, 1000, 52, 10,
                    Map.of("text", "电商销售分析看板"),
                    style("#ffffff", 30, "#111827", "center", 0)));
            comps.add(comp("date", "dateFilter", 1500, 22, 380, 46, 11,
                    Map.of("field", "order_date",
                            "bindChartIds", ids.values().stream().toList()),
                    style("#ffffff", 14, "#374151", "left", 8)));
            comps.add(comp("kpi", "chart", 40, 90, 320, 380, 1,
                    Map.of("chartId", ids.get("kpi"), "title", "总销售额"),
                    style("#ffffff", 16, "#111827", "center", 10)));
            comps.add(comp("trend", "chart", 380, 90, 790, 380, 2,
                    Map.of("chartId", ids.get("trend"), "title", "销售额趋势"),
                    style("#ffffff", 16, "#111827", "center", 10)));
            comps.add(comp("pie", "chart", 1190, 90, 690, 380, 3,
                    Map.of("chartId", ids.get("categoryPie"), "title", "品类销售占比"),
                    style("#ffffff", 16, "#111827", "center", 10)));
            comps.add(comp("region", "chart", 40, 490, 610, 400, 4,
                    Map.of("chartId", ids.get("regionBar"), "title", "各大区销售额",
                            "drillFields", List.of("region", "province", "city")),
                    style("#ffffff", 16, "#111827", "center", 10)));
            comps.add(comp("profit", "chart", 670, 490, 610, 400, 5,
                    Map.of("chartId", ids.get("profitArea"), "title", "利润趋势"),
                    style("#ffffff", 16, "#111827", "center", 10)));
            comps.add(comp("channel", "chart", 1300, 490, 580, 400, 6,
                    Map.of("chartId", ids.get("channelBar"), "title", "各渠道销量"),
                    style("#ffffff", 16, "#111827", "center", 10)));
            comps.add(comp("detail", "chart", 40, 910, 1840, 140, 7,
                    Map.of("chartId", ids.get("detail"), "title", "订单明细"),
                    style("#ffffff", 14, "#111827", "center", 10)));

            List<Map<String, Object>> links = List.of(Map.of(
                    "source", "region",
                    "sourceField", "region",
                    "targets", List.of(
                            Map.of("component", "trend", "field", "region"),
                            Map.of("component", "pie", "field", "region"),
                            Map.of("component", "profit", "field", "region"))));

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("canvas", Map.of("width", 1920, "height", 1080, "theme", "light", "bgColor", "#eef1f6"));
            config.put("components", comps);
            config.put("links", links);

            Dashboard dashboard = new Dashboard();
            dashboard.setName("电商销售分析看板");
            dashboard.setConfigJson(objectMapper.writeValueAsString(config));
            dashboard.setShareEnabled(0);
            dashboard.setOwnerId(1L);
            dashboardMapper.insert(dashboard);
            log.info("已创建示例仪表板: 电商销售分析看板");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> comp(String id, String type, int x, int y, int w, int h, int z,
                                     Map<String, Object> props, Map<String, Object> style) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("x", x);
        m.put("y", y);
        m.put("w", w);
        m.put("h", h);
        m.put("z", z);
        m.put("props", props);
        m.put("style", style);
        return m;
    }

    private Map<String, Object> style(String bg, int fontSize, String fontColor, String align, int radius) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("bgColor", bg);
        s.put("fontSize", fontSize);
        s.put("fontColor", fontColor);
        s.put("align", align);
        s.put("borderRadius", radius);
        s.put("borderColor", "#e5e7eb");
        s.put("showTitle", true);
        return s;
    }

    private DatasetField field(String name, String label, String fieldType, String dataType, String agg) {
        DatasetField f = new DatasetField();
        f.setName(name);
        f.setLabel(label);
        f.setFieldType(fieldType);
        f.setDataType(dataType);
        f.setDefaultAgg(agg);
        return f;
    }

    private ChartConfig baseConfig(String type) {
        ChartConfig c = new ChartConfig();
        c.setChartType(type);
        c.setLimit(1000);
        return c;
    }

    private ChartConfig kpiConfig() {
        ChartConfig c = baseConfig("kpi");
        ChartMeasure m = new ChartMeasure();
        m.setField("amount");
        m.setAgg("sum");
        m.setLabel("总销售额");
        c.getMeasures().add(m);
        return c;
    }

    private ChartConfig lineConfig(String label, String measureField, String level) {
        ChartConfig c = baseConfig("line");
        ChartDim d = new ChartDim();
        d.setField("order_date");
        d.setDateLevel(level);
        d.setLabel("月份");
        c.getDims().add(d);
        ChartMeasure m = new ChartMeasure();
        m.setField(measureField);
        m.setAgg("sum");
        m.setLabel(label);
        c.getMeasures().add(m);
        Map<String, String> sort = new LinkedHashMap<>();
        sort.put("field", "order_date");
        sort.put("dir", "asc");
        c.setSort(sort);
        return c;
    }

    private ChartConfig pieConfig() {
        ChartConfig c = baseConfig("pie");
        ChartDim d = new ChartDim();
        d.setField("category");
        d.setLabel("品类");
        c.getDims().add(d);
        ChartMeasure m = new ChartMeasure();
        m.setField("amount");
        m.setAgg("sum");
        m.setLabel("销售额");
        c.getMeasures().add(m);
        return c;
    }

    private ChartConfig barConfig() {
        ChartConfig c = baseConfig("bar");
        ChartDim d = new ChartDim();
        d.setField("region");
        d.setLabel("大区");
        c.getDims().add(d);
        ChartMeasure m = new ChartMeasure();
        m.setField("amount");
        m.setAgg("sum");
        m.setLabel("销售额");
        c.getMeasures().add(m);
        return c;
    }

    private ChartConfig areaConfig() {
        ChartConfig c = lineConfig("利润", "profit", "month");
        c.setChartType("area");
        return c;
    }

    private ChartConfig channelConfig() {
        ChartConfig c = baseConfig("bar");
        ChartDim d = new ChartDim();
        d.setField("channel");
        d.setLabel("渠道");
        c.getDims().add(d);
        ChartMeasure m = new ChartMeasure();
        m.setField("quantity");
        m.setAgg("sum");
        m.setLabel("销量");
        c.getMeasures().add(m);
        return c;
    }

    private ChartConfig detailConfig() {
        ChartConfig c = baseConfig("detail");
        c.setDetailFields(List.of("order_no", "order_date", "region", "city", "channel",
                "category", "product_name", "customer_level", "quantity", "amount", "profit"));
        c.setLimit(200);
        Map<String, String> sort = new LinkedHashMap<>();
        sort.put("field", "order_date");
        sort.put("dir", "desc");
        c.setSort(sort);
        return c;
    }
}
