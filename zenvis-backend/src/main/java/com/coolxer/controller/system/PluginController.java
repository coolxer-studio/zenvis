package com.coolxer.controller.system;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.FileTreeNodeVo;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.base.vo.SingleValueVo;
import com.coolxer.model.system.dto.PluginDto;
import com.coolxer.model.system.dto.PluginSearchDto;
import com.coolxer.model.system.dto.PluginUpgradeDto;
import com.coolxer.model.system.vo.PluginVo;
import com.coolxer.service.system.PluginService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 插件管理
 */
@Tag(name = "插件管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/system/plugin")
public class PluginController extends BaseController {

    private static final long PLUGIN_LOG_STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final String MARKET_PLUGINS_API = "https://gitee.com/api/v5/repos/coolxer-studio/zenvis/contents/zenvis-plugin/market-plugins.json?ref=main";
    private static final long MARKET_CACHE_TTL_MILLIS = Duration.ofMinutes(5).toMillis();

    private volatile List<PluginVo> marketPluginsCache = null;
    private volatile long marketCacheTimestamp = 0;

    @Autowired
    private PluginService pluginService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping({"/upload"})
    public ResponseWrap<PluginVo> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            PluginVo pluginVo = pluginService.uploadFile(file);
            return ResponseWrap.success(pluginVo);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/icon/base64"})
    public ResponseWrap<SingleValueVo> base64Icon(@RequestParam("file") MultipartFile file) {
        try {
            SingleValueVo singleValueVo = new SingleValueVo(pluginService.base64Icon(file));
            return ResponseWrap.success(singleValueVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/add"})
    public ResponseWrap<?> add(@RequestBody PluginDto pluginDto) {

        try {
            if (pluginService.isPackageExist(pluginDto.getPackageName())) {
                return ResponseWrap.fail(ResultCodeEnum.PLUGIN_IS_EXIST);
            }
            if (pluginService.create(pluginDto) != null) {
                return ResponseWrap.success("创建成功");
            } else {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/{id}"})
    public ResponseWrap<?> delete(@PathVariable("id") Long id) {
        try {
            pluginService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/bulk/{ids}"})
    public ResponseWrap<?> bulkDelete(@PathVariable("ids") List<Long> ids) {
        try {
            pluginService.deleteByIds(ids);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/list"})
    public ResponseWrap<?> list(PluginSearchDto pluginSearchDto) {
        try {
            PageRowsVo<PluginVo> pageDataVo = pluginService.getPageList(pluginSearchDto);
            return ResponseWrap.success(pageDataVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }

    }

    @GetMapping({"/{id}/view"})
    public ResponseWrap<PluginVo> query(@PathVariable("id") Long id) {
        try {
            PluginVo pluginVo = pluginService.info(id);
            if (pluginVo == null) {
                return ResponseWrap.fail();
            } else {
                return ResponseWrap.success(pluginVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{id}/readme"})
    public ResponseWrap<SingleValueVo> readme(@PathVariable("id") Long id) {
        try {
            String readmeMarkdownText = pluginService.readme(id);
            if (readmeMarkdownText == null) {
                return ResponseWrap.fail();
            } else {
                SingleValueVo singleValueVo = new SingleValueVo(readmeMarkdownText);
                return ResponseWrap.success(singleValueVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{id}/doc/tree"})
    public ResponseWrap<List<FileTreeNodeVo>> docTree(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(pluginService.docTree(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{id}/doc/view"})
    public ResponseWrap<SingleValueVo> docView(@PathVariable("id") Long id, @RequestParam(value = "file") String file) {
        try {
            String docMarkdownText = pluginService.readDocFile(id, file);
            if (docMarkdownText == null) {
                return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
            }
            docMarkdownText = docMarkdownText.replaceAll("%", "%25");
            SingleValueVo singleValueVo = new SingleValueVo(docMarkdownText);
            return ResponseWrap.success(singleValueVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @RequestMapping({"/{id}/export"})
    public void export(@PathVariable("id") Long id, HttpServletResponse response) {
        try {
            pluginService.export(id, response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<StreamingResponseBody> handleLog(@PathVariable("id") Long id,
                                                           HttpServletRequest request) {
        StreamingResponseBody stream = out -> {
            request.getAsyncContext().setTimeout(PLUGIN_LOG_STREAM_TIMEOUT_MILLIS);
            while (true) {
                String logInfo = pluginService.getLogs(id);
                if (logInfo != null) {
                    out.write((logInfo + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                if (StringUtils.contains(logInfo, "完成......") || StringUtils.contains(logInfo, "失败......")) {
                    break;
                }
            }
            out.close();
        };
        return new ResponseEntity(stream, HttpStatus.OK);
    }

    @PostMapping({"/{id}/install"})
    public ResponseWrap<PluginVo> install(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(pluginService.install(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/upgrade"})
    public ResponseWrap<PluginVo> upgrade(@PathVariable("id") Long id,
                                          @RequestBody PluginUpgradeDto upgradeDto) {
        try {
            return ResponseWrap.success(pluginService.upgrade(id, upgradeDto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/upgrade/recover"})
    public ResponseWrap<PluginVo> recoverUpgrade(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(pluginService.recoverUpgrade(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/uninstall"})
    public ResponseWrap<PluginVo> uninstall(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(pluginService.uninstall(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 插件市场列表
     * 从远程 Gitee 获取全部插件数据，由前端做分页
     * 自动对比本地已安装插件，标记市场状态：未安装/已安装/可升级
     */
    @GetMapping({"/market/list"})
    public ResponseWrap<List<PluginVo>> marketList() {
        try {
            List<PluginVo> marketPlugins = fetchMarketPlugins();
            // 获取本地所有插件，按包名建立索引
            List<PluginVo> localPlugins = pluginService.findAll();
            java.util.Map<String, PluginVo> localMap = new java.util.HashMap<>();
            for (PluginVo p : localPlugins) {
                if (p.getPackageName() != null) {
                    localMap.put(p.getPackageName(), p);
                }
            }
            // 对比版本，设置市场状态
            for (PluginVo marketPlugin : marketPlugins) {
                PluginVo local = localMap.get(marketPlugin.getPackageName());
                if (local == null) {
                    marketPlugin.setMarketStatus("not_installed");
                } else {
                    marketPlugin.setLocalId(local.getId());
                    marketPlugin.setLocalVersion(local.getVersion());
                    int cmp = compareVersion(marketPlugin.getVersion(), local.getVersion());
                    if (cmp > 0) {
                        marketPlugin.setMarketStatus("upgradeable");
                    } else {
                        marketPlugin.setMarketStatus("installed");
                    }
                }
            }
            return ResponseWrap.success(marketPlugins);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 比较两个版本号大小
     * @return 正数表示v1>v2，负数表示v1<v2，0表示相等
     */
    private int compareVersion(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? parseIntSafe(parts1[i]) : 0;
            int n2 = i < parts2.length ? parseIntSafe(parts2[i]) : 0;
            if (n1 != n2) {
                return n1 - n2;
            }
        }
        return 0;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从插件市场下载插件
     * 下载插件包到本地并创建插件记录，状态为未安装
     */
    @PostMapping({"/market/{id}/download"})
    public ResponseWrap<?> marketDownload(@PathVariable("id") Long id) {
        try {
            List<PluginVo> marketPlugins = fetchMarketPlugins();
            PluginVo marketPlugin = marketPlugins.stream()
                    .filter(p -> p.getId() == id.intValue())
                    .findFirst()
                    .orElse(null);
            if (marketPlugin == null) {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
            if (pluginService.isPackageExist(marketPlugin.getPackageName())) {
                return ResponseWrap.fail(ResultCodeEnum.PLUGIN_IS_EXIST);
            }
            PluginDto pluginDto = new PluginDto();
            pluginDto.setName(marketPlugin.getName());
            pluginDto.setPackageName(marketPlugin.getPackageName());
            pluginDto.setVersion(marketPlugin.getVersion());
            pluginDto.setDescription(marketPlugin.getDescription());
            pluginDto.setAuthor(marketPlugin.getAuthor());
            pluginDto.setIcon(marketPlugin.getIcon());
            pluginService.downloadFromUrl(marketPlugin.getDownloadUrl(), pluginDto);
            return ResponseWrap.success("下载成功，插件已添加到插件管理");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 从插件市场升级插件
     * 下载最新版本插件包并对本地已安装插件执行升级
     */
    @PostMapping({"/market/{id}/upgrade"})
    public ResponseWrap<PluginVo> marketUpgrade(@PathVariable("id") Long id) {
        try {
            List<PluginVo> marketPlugins = fetchMarketPlugins();
            PluginVo marketPlugin = marketPlugins.stream()
                    .filter(p -> p.getId() == id.intValue())
                    .findFirst()
                    .orElse(null);
            if (marketPlugin == null) {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
            // 找到本地对应的插件
            PluginVo localPlugin = pluginService.findAll().stream()
                    .filter(p -> marketPlugin.getPackageName().equals(p.getPackageName()))
                    .findFirst()
                    .orElse(null);
            if (localPlugin == null) {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "本地未找到对应插件，请先下载安装");
            }
            // 下载新版本插件包
            String pluginPath = pluginService.downloadPackage(marketPlugin.getDownloadUrl(), marketPlugin.getPackageName());
            // 执行升级
            PluginUpgradeDto upgradeDto = new PluginUpgradeDto();
            upgradeDto.setPluginPath(pluginPath);
            PluginVo result = pluginService.upgrade((long) localPlugin.getId(), upgradeDto);
            return ResponseWrap.success(result);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 获取市场插件列表：优先远程 API，不可用时回退本地文件
     */
    private List<PluginVo> fetchMarketPlugins() {
        long now = System.currentTimeMillis();
        if (marketPluginsCache != null && (now - marketCacheTimestamp) < MARKET_CACHE_TTL_MILLIS) {
            return new ArrayList<>(marketPluginsCache);
        }
        try {
            String json = fetchMarketPluginsFromGiteeApi();
            if (StringUtils.isEmpty(json)) {
                log.warn("远程 API 不可用，回退到本地文件读取");
                json = fetchMarketPluginsFromLocal();
            }
            if (StringUtils.isEmpty(json)) {
                return new ArrayList<>();
            }
            List<PluginVo> plugins = objectMapper.readValue(json, new TypeReference<List<PluginVo>>() {});
            // 补充默认字段
            for (PluginVo plugin : plugins) {
                if (plugin.getStatus() == null) {
                    plugin.setStatus(com.coolxer.commons.enums.PluginStatusType.UN_INSTALL);
                    plugin.setStatusDescription("未安装");
                }
                if (plugin.getUpdateTime() == null) {
                    plugin.setUpdateTime(new Date());
                }
            }
            marketPluginsCache = new ArrayList<>(plugins);
            marketCacheTimestamp = now;
            return plugins;
        } catch (Exception e) {
            log.error("获取市场插件列表失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 通过 Gitee Contents API 获取 market-plugins.json 内容
     * 返回解码后的 JSON 字符串
     */
    private String fetchMarketPluginsFromGiteeApi() {
        try {
            URL url = new URL(MARKET_PLUGINS_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("获取市场插件列表 HTTP 状态码: {}", code);
                return null;
            }
            String responseStr;
            try (InputStream is = conn.getInputStream()) {
                responseStr = IOUtils.toString(is, StandardCharsets.UTF_8);
            }
            // 解析 Gitee API 返回的 JSON，提取 base64 编码的 content 字段
            java.util.Map<String, Object> respMap = objectMapper.readValue(responseStr, new TypeReference<java.util.Map<String, Object>>() {});
            String content = (String) respMap.get("content");
            if (StringUtils.isEmpty(content)) {
                return null;
            }
            // 去除 base64 字符串中的换行符后解码
            content = content.replaceAll("\\s", "");
            byte[] decoded = java.util.Base64.getDecoder().decode(content);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("从 Gitee API 获取市场插件列表失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 classpath 资源读取 market-plugins.json
     */
    private String fetchMarketPluginsFromLocal() {
        try {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("market-plugins.json")) {
                if (is != null) {
                    return IOUtils.toString(is, StandardCharsets.UTF_8);
                }
            }
            log.warn("classpath 中未找到 market-plugins.json");
            return null;
        } catch (Exception e) {
            log.error("从 classpath 读取市场插件列表失败: {}", e.getMessage(), e);
            return null;
        }
    }

}
