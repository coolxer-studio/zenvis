package com.coolxer.service.dih;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.commons.enums.MessageType;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.Message;
import com.coolxer.model.policy.dto.ConfigDto;
import com.coolxer.model.policy.vo.ConfigVo;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.DashboardSearchDto;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.MenuOrderRowDto;
import com.coolxer.model.system.dto.MenuSearchDto;
import com.coolxer.model.system.vo.DashboardVo;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DataVisualizationDemoResponseServiceTest {

    private final FakeConfigService configService = new FakeConfigService();
    private final FakeMenuService menuService = new FakeMenuService();
    private final FakeDashboardService dashboardService = new FakeDashboardService();
    private final DataVisualizationDemoResponseService service = new DataVisualizationDemoResponseService(
            configService,
            menuService,
            dashboardService
    );

    @Test
    void chartRequirementReturnsInfoSteps() {
        configService.metaExists = true;

        String response = responseOf(service.findResponse(null, "chat-1", """
                # 用户事件数据可视化：临时图表
                请查看用户事件数据的上报情况。
                """, null));

        assertThat(response)
                .contains("zenvis:info-steps")
                .contains("用户事件临时图表信息确认")
                .contains("时间范围")
                .contains("图表类型");
    }

    @Test
    void plainDemoPromptRoutesToSelectedScenario() {
        configService.metaExists = true;

        assertThat(responseOf(service.findResponse(null, "chat-1", "请查看用户事件数据的上报情况，并生成一个临时性的可视化图表。", null)))
                .contains("用户事件临时图表信息确认")
                .doesNotContain("用户事件单页面应用实现方式确认");
        assertThat(responseOf(service.findResponse(null, "chat-1", "请根据用户事件数据生成一个单页面应用。", null)))
                .contains("用户事件单页面应用实现方式确认");
        assertThat(responseOf(service.findResponse(null, "chat-1", "请生成一个带侧边栏的用户事件数据应用。", null)))
                .contains("用户事件侧边栏应用信息确认");
        assertThat(responseOf(service.findResponse(null, "chat-1", "请生成一个用户事件数据看板。", null)))
                .contains("用户事件数据看板信息确认");
    }

    @Test
    void chartInfoSubmittedReturnsPreviewWithToolbarAction() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件临时图表信息确认"}
                """, null));

        assertThat(response)
                .contains("zenvis:visualization-chart-preview")
                .contains("\"echarts\"")
                .contains("\"option\"")
                .contains("amisConfig")
                .contains("/zenvis/api/v1/entity/trend/query")
                .contains("\"method\": \"post\"")
                .contains("\"dataset\"")
                .doesNotContain("legend_data")
                .doesNotContain("xaxis_data")
                .doesNotContain("series_data_")
                .contains("\"action\": \"data_visualization.add_chart_library\"")
                .doesNotContain("```zenvis:confirm")
                .doesNotContain("是否加入图表库");
        assertThat(new ChatMessagePartParser().parse(response, MessageType.TEXT))
                .extracting("type")
                .contains("visualization-chart-preview");
    }

    @Test
    void singlePageLowCodeSelectionReturnsLowCodeConfig() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件单页面应用实现方式确认","content":"请选择用低代码 amis 还是静态 HTML 实现用户事件增删改查单页面。","answers":[{"id":"implementation","title":"实现方式","value":"使用低代码 amis 方式实现单页面 CRUD 应用"}]}
                """, null));

        assertThat(response)
                .contains("zenvis:low-code-page-config")
                .contains("\"type\": \"crud\"")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"implementation\":\"low_code\"")
                .doesNotContain("zenvis:html-page-config")
                .doesNotContain("\"implementation\":\"html\"");
    }

    @Test
    void singlePageHtmlSelectionReturnsHtmlConfig() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件单页面应用实现方式确认","content":"请选择用低代码 amis 还是静态 HTML 实现用户事件增删改查单页面。","answers":[{"id":"implementation","title":"实现方式","value":"使用静态 HTML 单页面直接调用实体 REST API"}]}
                """, null));

        assertThat(response)
                .contains("zenvis:html-page-config")
                .contains("<!doctype html>")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"implementation\":\"html\"")
                .doesNotContain("zenvis:low-code-page-config")
                .doesNotContain("\"implementation\":\"low_code\"");
    }

    @Test
    void dashboardLowCodeSelectionReturnsLowCodeDashboardConfig() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件数据看板信息确认","content":"请选择低代码、静态 HTML 或外链接方式。","answers":[{"id":"implementation","title":"实现方式","value":"使用低代码 amis 页面实现数据看板"}]}
                """, null));

        assertThat(response)
                .contains("zenvis:low-code-page-config")
                .contains("用户事件数据看板")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"dashboardType\":\"low_code\"")
                .doesNotContain("zenvis:html-page-config")
                .doesNotContain("\"dashboardType\":\"html\"")
                .doesNotContain("\"dashboardType\":\"link\"");
    }

    @Test
    void dashboardHtmlSelectionReturnsHtmlDashboardConfig() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件数据看板信息确认","content":"请选择低代码、静态 HTML 或外链接方式。","answers":[{"id":"implementation","title":"实现方式","value":"使用静态 HTML 页面实现数据看板"}]}
                """, null));

        assertThat(response)
                .contains("zenvis:html-page-config")
                .contains("<title>用户事件数据看板</title>")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"dashboardType\":\"html\"")
                .doesNotContain("zenvis:low-code-page-config")
                .doesNotContain("\"dashboardType\":\"low_code\"")
                .doesNotContain("\"dashboardType\":\"link\"");
    }

    @Test
    void dashboardLinkWithoutUrlAsksForUrl() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件数据看板信息确认","answers":[{"value":"使用外链接方式接入已有看板"}]}
                """, null));

        assertThat(response)
                .contains("用户事件外链看板地址确认")
                .contains("https://");
    }

    @Test
    void dashboardLinkUrlSelectionReturnsLinkDashboardConfirm() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件外链看板地址确认","answers":[{"id":"url","title":"外链接地址","value":"https://example.com/user-event-dashboard"}]}
                """, null));

        assertThat(response)
                .contains("是否创建用户事件外链看板")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"dashboardType\":\"link\"")
                .contains("\"url\":\"https://example.com/user-event-dashboard\"")
                .doesNotContain("zenvis:low-code-page-config")
                .doesNotContain("zenvis:html-page-config");
    }

    @Test
    void reviseVisualizationConfigReturnsUpdatedDemoConfigWithoutModelFallback() {
        ChatSession session = new ChatSession();
        session.setMessages(JacksonUtil.toJson(List.of(
                new Message("ai", "{\"demoScenario\":\"single_page\",\"implementation\":\"low_code\"}")
        )));

        String response = responseOf(service.findResponse(session, "chat-1", """
                我需要补充信息继续更新数据可视化配置。调整要求如下：
                增加用户事件趋势图。
                """, null));

        assertThat(response)
                .contains("已根据补充信息更新用户事件单页面应用配置")
                .contains("zenvis:low-code-page-config")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"implementation\":\"low_code\"");
    }

    @Test
    void abandonVisualizationConfigReturnsNotice() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我选择放弃本次数据可视化配置。请不要写入 open_config。
                """, null));

        assertThat(response)
                .contains("已放弃本次数据可视化配置")
                .contains("zenvis:notice")
                .doesNotContain("zenvis:low-code-page-config")
                .doesNotContain("zenvis:html-page-config");
    }

    @Test
    void applySinglePageLowCodeWritesConfigAndReturnsRecords() {
        Menu parent = new Menu().setName("配置管理").setType(MenuType.BUILT_APP).setParentId(0).setLevel(MenuLevel.LEVEL_1);
        parent.setId(1);
        menuService.parentMenus = List.of(parent);

        ChatSession session = new ChatSession();
        session.setMessages(JacksonUtil.toJson(List.of(
                new Message("ai", "{\"demoScenario\": \"single_page\",\"implementation\":\"low_code\"}")
        )));

        String response = responseOf(service.findResponse(session, "chat-1", "我已确认并授权应用上一轮数据可视化配置。", null));

        assertThat(response)
                .contains("zenvis:visualization-config-record")
                .contains("zenvis:menu-config-record")
                .contains("user-event-page")
                .contains("用户事件单页面应用");
        assertThat(configService.ensuredRoots).contains("user-event-page");
        assertThat(configService.addedFiles).contains("user-event-page/index.json");
        Menu policyMenu = menuService.menus.stream()
                .filter(menu -> MenuType.POLICY_CONFIG == menu.getType())
                .findFirst()
                .orElseThrow();
        Menu pageMenu = menuService.menus.stream()
                .filter(menu -> MenuType.LOW_CODE_PAGE == menu.getType())
                .findFirst()
                .orElseThrow();
        assertThat(policyMenu.getLevel()).isEqualTo(MenuLevel.LEVEL_2);
        assertThat(policyMenu.getParentId()).isEqualTo(1);
        assertThat(pageMenu.getLevel()).isEqualTo(MenuLevel.LEVEL_1);
        assertThat(pageMenu.getParentId()).isEqualTo(0);
    }

    @Test
    void nonPolicyVisualizationMenusDefaultToLevelOne() throws Exception {
        Menu parent = new Menu().setName("配置管理").setType(MenuType.BUILT_APP).setParentId(0).setLevel(MenuLevel.LEVEL_1);
        parent.setId(1);
        menuService.parentMenus = List.of(parent);
        java.lang.reflect.Method method = DataVisualizationDemoResponseService.class.getDeclaredMethod(
                "buildMenuDto",
                String.class,
                String.class,
                MenuType.class,
                String.class
        );
        method.setAccessible(true);

        MenuDto lowCodePage = (MenuDto) method.invoke(service, "source:page", "页面", MenuType.LOW_CODE_PAGE, "page-config");
        MenuDto htmlPage = (MenuDto) method.invoke(service, "source:html", "HTML", MenuType.HTML_PAGE, "/html-page/demo.html");
        MenuDto lowCodeApp = (MenuDto) method.invoke(service, "source:app", "应用", MenuType.LOW_CODE_APP, "app-config");
        MenuDto policyConfig = (MenuDto) method.invoke(service, "source:policy", "配置", MenuType.POLICY_CONFIG, "page-config");

        assertThat(List.of(lowCodePage, htmlPage, lowCodeApp))
                .allSatisfy(menuDto -> {
                    assertThat(menuDto.getLevel()).isEqualTo(MenuLevel.LEVEL_1);
                    assertThat(menuDto.getParentId()).isEqualTo(0);
                });
        assertThat(policyConfig.getLevel()).isEqualTo(MenuLevel.LEVEL_2);
        assertThat(policyConfig.getParentId()).isEqualTo(1);
    }

    private String responseOf(Optional<Flux<String>> response) {
        assertThat(response).isPresent();
        return String.join("", response.get().collectList().block());
    }

    private static class FakeConfigService implements ConfigService {
        private boolean metaExists;
        private final List<String> ensuredRoots = new ArrayList<>();
        private final List<String> addedFiles = new ArrayList<>();
        private final List<String> existingFiles = new ArrayList<>();

        @Override
        public List<ConfigVo> getConfigFileTree(String type) {
            return List.of();
        }

        @Override
        public String readFileSchema(String type, String fileName) {
            return null;
        }

        @Override
        public String readFile(String type, String fileName) {
            return null;
        }

        @Override
        public void modifyConfig(String type, ConfigDto configDto) {
            existingFiles.add(type + "/" + configDto.getFileName());
        }

        @Override
        public boolean addFile(String type, String fileName) {
            addedFiles.add(type + "/" + fileName);
            existingFiles.add(type + "/" + fileName);
            return true;
        }

        @Override
        public boolean renameFile(String type, String originalFile, String newFile) {
            return false;
        }

        @Override
        public boolean deleteFile(String type, String fileName) {
            return false;
        }

        @Override
        public String configPath(String type) {
            return "";
        }

        @Override
        public void applyPolicy(String type, ConfigDto configDto) {
        }

        @Override
        public boolean addRootPath(String type) {
            return ensureRootPath(type);
        }

        @Override
        public boolean ensureRootPath(String type) {
            ensuredRoots.add(type);
            return true;
        }

        @Override
        public boolean fileExistsInConfigPath(String type, String fileName) {
            if ("meta".equals(type)) {
                return metaExists;
            }
            return existingFiles.contains(type + "/" + fileName);
        }
    }

    private static class FakeMenuService implements MenuService {
        private List<Menu> parentMenus = List.of();
        private final List<Menu> menus = new ArrayList<>();
        private int nextId = 10;

        @Override
        public List<MenuVo> findAll() {
            return menus.stream().map(MenuVo::new).toList();
        }

        @Override
        public Menu create(MenuDto menuDto) {
            Menu menu = new Menu()
                    .setName(menuDto.getName())
                    .setType(menuDto.getType())
                    .setParams(menuDto.getParams())
                    .setRoute(menuDto.getType() == null ? null : menuDto.getType().getRoute())
                    .setParentId(menuDto.getParentId())
                    .setLevel(menuDto.getLevel())
                    .setSource(menuDto.getSource());
            menu.setId(nextId++);
            menus.add(menu);
            return menu;
        }

        @Override
        public Boolean update(Long id, MenuDto menuDto) {
            return false;
        }

        @Override
        public Boolean updateOrder(MenuOrderRowDto menuOrderRowDto) {
            return false;
        }

        @Override
        public void delete(Long id) {
        }

        @Override
        public void deleteByIds(List<Long> ids) {
        }

        @Override
        public MenuVo info(Long id) {
            return null;
        }

        @Override
        public PageRowsVo<MenuVo> getPageList(MenuSearchDto menuSearchDto) {
            return null;
        }

        @Override
        public List<Menu> findAllParentMenu() {
            return parentMenus;
        }

        @Override
        public List<Menu> findBySource(String source) {
            return menus.stream().filter(menu -> source.equals(menu.getSource())).toList();
        }
    }

    private static class FakeDashboardService implements DashboardService {
        private final List<Dashboard> dashboards = new ArrayList<>();
        private int nextId = 20;

        @Override
        public List<DashboardVo> findAll() {
            return dashboards.stream().map(DashboardVo::new).toList();
        }

        @Override
        public Dashboard create(DashboardDto dashboardDto) {
            Dashboard dashboard = new Dashboard();
            dashboard.updateFromDto(dashboardDto);
            dashboard.setId(nextId++);
            dashboards.add(dashboard);
            return dashboard;
        }

        @Override
        public Boolean update(Long id, DashboardDto dashboardDto) {
            return false;
        }

        @Override
        public Boolean bulkUpdate(List<Long> ids, DashboardDto dashboardDto) {
            return false;
        }

        @Override
        public void delete(Long id) {
        }

        @Override
        public void deleteByIds(List<Long> ids) {
        }

        @Override
        public DashboardVo info(Long id) {
            return null;
        }

        @Override
        public PageRowsVo<DashboardVo> getPageList(DashboardSearchDto dashboardSearchDto) {
            return null;
        }
    }
}
