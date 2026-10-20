package com.coolxer.service.dih.agent.skill;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.SkillSaveDto;
import com.coolxer.model.dih.dto.SkillSearchDto;
import com.coolxer.model.dih.vo.AgentSkillVo;
import com.coolxer.model.dih.vo.SkillDetailVo;
import com.coolxer.model.dih.vo.SkillChatConfigVo;
import com.coolxer.model.dih.vo.SkillChatEntryVo;
import com.coolxer.model.dih.vo.SkillRuntimeConfigVo;
import com.coolxer.model.dih.vo.SkillRuntimeLimitsVo;
import com.coolxer.model.dih.vo.SkillRuntimeToolsVo;
import com.coolxer.service.dih.mcp.BuiltinMcpServiceDefinition;
import com.coolxer.service.dih.rag.RagKnowledgeBaseService;
import com.coolxer.model.dih.vo.SkillVo;
import com.coolxer.model.dih.vo.SkillOptionVo;
import com.coolxer.utils.WalkFileUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.web.multipart.MultipartFile;

/**
 * 本地 Skill 注册表。
 *
 * <p>默认扫描 app.paths.skills 目录，目录结构示例：
 * skill_config/data-visualization-agent/skill.json + SKILL.md
 */
@Slf4j
@Service
public class SkillService {

    private static final String DEFAULT_ENTRY_FILE = "SKILL.md";
    private static final String DEFAULT_MANIFEST_FILE = "skill.json";
    private static final String PLUGIN_SKILL_ROOT_DIR = "plugins";
    private static final String DEFAULT_PLUGIN_AGENT_TYPE = "ask";
    public static final String CHAT_TYPE_PREFIX = "skill:";
    public static final String GENERIC_SKILL_AGENT_TYPE = "agent_skill";
    private static final String DEFAULT_CHAT_ICON = "magic-stick";
    private static final int DEFAULT_CHAT_ORDER = 1000;
    private static final long MAX_SKILL_CONTENT_BYTES = 200 * 1024L;
    private static final int MAX_PROMPT_CHARS = 8000;
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$");
    private static final Pattern SAFE_PACKAGE_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,255}$");
    private static final long MAX_PACKAGE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_PACKAGE_ENTRIES = 512;
    private static final long MAX_PACKAGE_FILE_BYTES = 10L * 1024 * 1024;

    private final CustomWebConfig customWebConfig;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, SkillRecord> skillCache = new ConcurrentHashMap<>();

    /**
     * 可选注入：仅在 Web 容器中用于把知识库 ID 解析为展示名称；
     * 单元测试直接 new SkillService(...) 时允许为 null。
     */
    @Autowired(required = false)
    private RagKnowledgeBaseService ragKnowledgeBaseService;

    @Value("${app.ai.skill.max-selected-prompt-chars:32000}")
    private int maxSelectedPromptChars = 32000;

    public SkillService(CustomWebConfig customWebConfig, ObjectMapper objectMapper) {
        this.customWebConfig = customWebConfig;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 重新扫描并加载 skill 配置。
     */
    public List<SkillVo> reload() {
        Path root = getSkillRoot();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.error("创建 Skill 根目录失败: {}", root, e);
            return getAll();
        }

        ConcurrentHashMap<String, SkillRecord> nextCache = new ConcurrentHashMap<>();
        try {
            List<Path> skillDirs = listSkillDirs(root);
            for (Path skillDir : skillDirs) {
                loadSkill(skillDir, root, nextCache);
            }
        } catch (IOException e) {
            log.error("扫描 Skill 目录失败: {}", root, e);
        }
        skillCache.clear();
        skillCache.putAll(nextCache);
        log.info("Skill 加载完成，数量: {}", skillCache.size());
        return getAll();
    }

    /**
     * 分页查询 skill 列表。
     */
    public PageRowsVo<SkillVo> getPageList(SkillSearchDto searchDto) {
        SkillSearchDto condition = searchDto == null ? new SkillSearchDto() : searchDto;
        List<SkillVo> rows = getAll().stream()
                .filter(skill -> matchKeyword(skill, condition.getKeyword()))
                .filter(skill -> matchAgentType(skill, condition.getAgentType()))
                .filter(skill -> condition.getEnabled() == null
                        || condition.getEnabled().equals(Boolean.TRUE.equals(skill.getEnabled())))
                .filter(skill -> condition.getCustom() == null
                        || condition.getCustom().equals(Boolean.TRUE.equals(skill.getCustom())))
                .filter(skill -> matchSource(skill, condition.getSource()))
                .sorted(Comparator.comparing(SkillVo::getId))
                .collect(Collectors.toList());

        int page = Math.max(condition.getPage(), 1);
        int perPage = Math.max(condition.getPerPage(), 1);
        int from = Math.min((page - 1) * perPage, rows.size());
        int to = Math.min(from + perPage, rows.size());
        List<SkillVo> pageRows = enrichKnowledgeBaseNames(rows.subList(from, to));
        return new PageRowsVo<>(pageRows, rows.size());
    }

    /**
     * 为当前页 Skill 填充知识库展示名称。返回值可能是原对象（无绑定/名称服务不可用）
     * 或深拷贝对象，避免把展示字段写入 skillCache 中的共享实例。
     */
    private List<SkillVo> enrichKnowledgeBaseNames(List<SkillVo> skills) {
        if (ragKnowledgeBaseService == null || skills.isEmpty()) {
            return skills;
        }
        List<Integer> allIds = skills.stream()
                .map(SkillVo::getKnowledgeBaseIds)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(List::stream)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (allIds.isEmpty()) {
            return skills;
        }
        Map<Integer, String> nameMap;
        try {
            nameMap = ragKnowledgeBaseService.getNameMap(allIds);
        } catch (Exception e) {
            log.warn("解析 Skill 知识库名称失败，回退为 ID 展示", e);
            return skills;
        }
        List<SkillVo> enriched = new ArrayList<>(skills.size());
        for (SkillVo skill : skills) {
            if (CollectionUtils.isEmpty(skill.getKnowledgeBaseIds())) {
                enriched.add(skill);
                continue;
            }
            SkillVo copy = objectMapper.convertValue(skill, SkillVo.class);
            copy.setKnowledgeBaseNames(copy.getKnowledgeBaseIds().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(id -> nameMap.getOrDefault(id, "#" + id))
                    .toList());
            enriched.add(copy);
        }
        return enriched;
    }

    /**
     * 查询 DIH 内置智能体 Skill 入口列表。
     */
    public List<AgentSkillVo> getBuiltinAgentSkills(Boolean enabled) {
        return BuiltinAgentSkillRegistry.list().stream()
                .map(this::toAgentSkillVo)
                .filter(agentSkill -> enabled == null
                        || enabled.equals(Boolean.TRUE.equals(agentSkill.getEnabled())))
                .sorted(Comparator.comparing(AgentSkillVo::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * 查询 DIH 输入区可展示的 Skill 聊天入口。
     */
    public List<SkillChatEntryVo> getChatEntries(Boolean enabled) {
        return getAll().stream()
                .filter(skill -> skill.getChat() != null && Boolean.TRUE.equals(skill.getChat().getEnabled()))
                .filter(skill -> enabled == null
                        || enabled.equals(Boolean.TRUE.equals(skill.getEnabled())))
                .map(this::toChatEntrySafely)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(SkillChatEntryVo::getOrder)
                        .thenComparing(SkillChatEntryVo::getSkillId))
                .toList();
    }

    /**
     * 解析并校验动态 Skill 会话入口。
     */
    public SkillChatEntryVo requireEnabledChatEntry(String chatType) {
        SkillVo skill = requireEnabledChatSkill(chatType);
        return toChatEntry(skill);
    }

    /**
     * 解析动态会话类型对应的 Skill，供开场白等会话元数据使用。
     */
    public SkillVo requireEnabledChatSkill(String chatType) {
        if (!isDynamicChatType(chatType)) {
            throw new IllegalArgumentException("不是动态 Skill 会话类型: " + chatType);
        }
        String skillId = StringUtils.removeStart(chatType, CHAT_TYPE_PREFIX);
        if (StringUtils.isBlank(skillId)) {
            throw new IllegalArgumentException("动态 Skill 会话缺少 Skill ID");
        }
        SkillRecord record;
        try {
            record = getRecord(skillId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Skill 已停用或不存在: " + skillId, e);
        }
        SkillVo skill = record.getSkill();
        if (!Boolean.TRUE.equals(skill.getEnabled())) {
            throw new IllegalArgumentException("Skill 已停用或不存在: " + skillId);
        }
        if (skill.getChat() == null || !Boolean.TRUE.equals(skill.getChat().getEnabled())) {
            throw new IllegalArgumentException("Skill 未开放 DIH 聊天入口: " + skillId);
        }
        toChatEntry(skill);
        return skill;
    }

    public static boolean isDynamicChatType(String chatType) {
        return StringUtils.startsWith(chatType, CHAT_TYPE_PREFIX);
    }

    public boolean isBuiltinAgentType(String agentType) {
        return BuiltinAgentSkillRegistry.isBuiltinAgentType(agentType);
    }

    public boolean isBuiltinAgentEnabled(String agentType) {
        return BuiltinAgentSkillRegistry.findByAgentType(agentType)
                .map(this::toAgentSkillVo)
                .map(AgentSkillVo::getEnabled)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }

    public String getBuiltinAgentPlaceholder(String agentType) {
        return BuiltinAgentSkillRegistry.findByAgentType(agentType)
                .map(BuiltinAgentSkillRegistry.BuiltinAgentSkill::placeholderMessage)
                .orElse("智能体能力正在建设中，当前 Skill 仅用于入口占位。");
    }

    /**
     * 查询 skill 详情。
     */
    public SkillDetailVo detail(String id) {
        SkillRecord record = getRecord(id);
        SkillDetailVo detail = copyToDetail(record.getSkill());
        try {
            detail.setContent(readSkillContent(record.getEntryPath()));
        } catch (IOException e) {
            throw new IllegalStateException("读取 Skill 内容失败: " + id, e);
        }
        List<SkillVo> enriched = enrichKnowledgeBaseNames(List.<SkillVo>of(detail));
        if (!enriched.isEmpty()) {
            detail.setKnowledgeBaseNames(enriched.get(0).getKnowledgeBaseNames());
        }
        return detail;
    }

    /**
     * 设置 skill 启用状态并回写 manifest。
     */
    public SkillVo setEnabled(String id, boolean enabled) {
        SkillRecord record = getRecord(id);
        try {
            Map<String, Object> manifest = readManifestAsMap(record);
            manifest.put("id", record.getSkill().getId());
            manifest.put("name", record.getSkill().getName());
            manifest.put("enabled", enabled);
            Object entry = manifest.get("entry");
            if (entry == null || StringUtils.isBlank(entry.toString())) {
                manifest.put("entry", StringUtils.defaultIfBlank(record.getSkill().getEntry(), DEFAULT_ENTRY_FILE));
            }
            Files.createDirectories(record.getManifestPath().getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(record.getManifestPath().toFile(), manifest);
            reload();
            return getRecord(id).getSkill();
        } catch (IOException e) {
            throw new IllegalStateException("更新 Skill 状态失败: " + id, e);
        }
    }

    /**
     * 创建页面自定义 Skill，落盘 skill.json + SKILL.md 后重载注册表。
     */
    public SkillVo create(SkillSaveDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        validateSaveDto(dto, true);
        String id = dto.getId().trim();
        if (skillCache.containsKey(id)) {
            throw new IllegalArgumentException("Skill ID 已存在: " + id);
        }
        Path skillDir = safeResolve(getSkillRoot(), id);
        if (Files.exists(skillDir)) {
            throw new IllegalStateException("Skill 目录已存在: " + id);
        }
        writeSkillFiles(skillDir, buildManifest(dto, id, null, Map.of(), true, "custom"),
                dto.getContent());
        reload();
        return getRecord(id).getSkill();
    }

    /**
     * 更新可编辑 Skill。content 为 null 时保留原入口文件内容。
     */
    public SkillVo update(String id, SkillSaveDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        SkillRecord record = getRecord(id);
        requireEditableSkill(record);
        String skillId = record.getSkill().getId();
        dto.setId(skillId);
        validateSaveDto(dto, false);
        String fallbackEntry = StringUtils.defaultIfBlank(record.getSkill().getEntry(), DEFAULT_ENTRY_FILE);
        Map<String, Object> existingManifest;
        try {
            existingManifest = readManifestAsMap(record);
        } catch (IOException e) {
            throw new IllegalStateException("读取 Skill 配置失败: " + id, e);
        }
        boolean custom = Boolean.TRUE.equals(record.getSkill().getCustom());
        String source = StringUtils.defaultIfBlank(record.getSkill().getSource(), custom ? "custom" : "builtin");
        writeSkillFiles(record.getSkillDir(),
                buildManifest(dto, skillId, fallbackEntry, existingManifest, custom, source),
                dto.getContent());
        reload();
        return getRecord(skillId).getSkill();
    }

    /**
     * 删除页面自定义 Skill 目录并重载注册表。
     */
    public void delete(String id) {
        SkillRecord record = getRecord(id);
        requireCustomSkill(record);
        try {
            WalkFileUtil.delete(record.getSkillDir());
        } catch (IOException e) {
            throw new IllegalStateException("删除 Skill 失败: " + id, e);
        }
        reload();
    }

    /**
     * 上传 zip 技能包并安装为自定义 Skill。已存在的自定义 Skill 会被覆盖，
     * 内置/插件 Skill 拒绝覆盖。
     */
    public SkillVo uploadPackage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传技能包文件");
        }
        String filename = StringUtils.defaultString(file.getOriginalFilename());
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("技能包必须是 .zip 文件: " + filename);
        }
        if (file.getSize() > MAX_PACKAGE_BYTES) {
            throw new IllegalArgumentException("技能包超过大小限制 20MB");
        }
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("skill-upload-");
        } catch (IOException e) {
            throw new IllegalStateException("创建临时目录失败", e);
        }
        try {
            extractZip(file.getInputStream(), tempDir);
            Path packageRoot = locatePackageRoot(tempDir);
            Path manifestPath = resolveManifest(packageRoot);
            Map<String, Object> manifest = Files.exists(manifestPath)
                    ? objectMapper.readValue(manifestPath.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
                    })
                    : new LinkedHashMap<>();
            String id = StringUtils.defaultIfBlank(
                    manifest.get("id") == null ? null : manifest.get("id").toString(),
                    packageRoot.getFileName().toString()).trim();
            if (!SAFE_ID_PATTERN.matcher(id).matches()) {
                throw new IllegalArgumentException("技能包包含非法 Skill ID: " + id);
            }
            String entryName = StringUtils.defaultIfBlank(
                    manifest.get("entry") == null ? null : manifest.get("entry").toString(), DEFAULT_ENTRY_FILE);
            if (!Files.exists(safeResolve(packageRoot, entryName))) {
                throw new IllegalArgumentException("技能包缺少入口文件: " + entryName);
            }
            SkillRecord existing = skillCache.get(id);
            if (existing != null) {
                requireCustomSkill(existing);
            }
            Path target = safeResolve(getSkillRoot(), id);
            if (Files.exists(target) && existing == null) {
                throw new IllegalStateException("Skill 目录已存在但未被注册，请先手动清理: " + id);
            }
            if (Files.exists(target)) {
                WalkFileUtil.delete(target);
            }
            WalkFileUtil.copy(packageRoot, target);
            Path targetManifest = resolveManifest(target);
            Map<String, Object> targetMap = Files.exists(targetManifest)
                    ? objectMapper.readValue(targetManifest.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
                    })
                    : new LinkedHashMap<>();
            targetMap.put("id", id);
            targetMap.put("custom", true);
            targetMap.put("editable", true);
            targetMap.put("source", "custom");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(targetManifest.toFile(), targetMap);
            reload();
            return getRecord(id).getSkill();
        } catch (IOException e) {
            throw new IllegalStateException("解析技能包失败: " + e.getMessage(), e);
        } finally {
            try {
                if (Files.exists(tempDir)) {
                    WalkFileUtil.delete(tempDir);
                }
            } catch (IOException e) {
                log.warn("清理技能包临时目录失败: {}", tempDir, e);
            }
        }
    }

    /**
     * 导出 Skill 为可再导入的 zip 技能包。
     *
     * <p>zip 内统一以 {@code <skillId>/} 为顶层目录，完整保留 skill.json、入口文件
     * 及其附属资源；条目数与总大小采用与上传一致的限制，保证导出产物可通过
     * 「上传技能包」直接回导。</p>
     */
    public SkillPackage exportPackage(String id) {
        SkillRecord record = getRecord(id);
        Path skillDir = record.getSkillDir();
        if (!Files.exists(skillDir) || !Files.isDirectory(skillDir)) {
            throw new IllegalArgumentException("Skill 目录不存在: " + id);
        }
        String rootName = record.getSkill().getId();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(skillDir)) {
            files = walk.filter(path -> Files.isRegularFile(path) && !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(path -> skillDir.relativize(path).toString()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("读取 Skill 目录失败: " + id, e);
        }
        if (files.size() > MAX_PACKAGE_ENTRIES) {
            throw new IllegalArgumentException("Skill 文件数量超过技能包限制 " + MAX_PACKAGE_ENTRIES);
        }
        long totalBytes = files.stream().mapToLong(path -> {
            try {
                return Files.size(path);
            } catch (IOException e) {
                throw new IllegalStateException("读取 Skill 文件失败: " + path, e);
            }
        }).sum();
        if (totalBytes > MAX_PACKAGE_BYTES) {
            throw new IllegalArgumentException("Skill 文件总大小超过技能包限制 20MB");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) Math.min(totalBytes + 4096L, MAX_PACKAGE_BYTES + 1));
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Path file : files) {
                String relative = skillDir.relativize(file).toString().replace("\\", "/");
                if (StringUtils.isBlank(relative) || relative.startsWith("/") || relative.contains("..")) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(rootName + "/" + relative));
                Files.copy(file, zip);
                zip.closeEntry();
            }
            zip.finish();
        } catch (IOException e) {
            throw new IllegalStateException("导出 Skill 失败: " + id, e);
        }
        return new SkillPackage(rootName + ".zip", buffer.toByteArray());
    }

    /**
     * 汇总显式选择 Skill 绑定的 RAG 知识库 ID 并集（仅统计已启用 Skill）。
     */
    public List<Integer> resolveKnowledgeBaseIds(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        return skillIds.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .map(skillCache::get)
                .filter(java.util.Objects::nonNull)
                .map(SkillRecord::getSkill)
                .filter(skill -> Boolean.TRUE.equals(skill.getEnabled()))
                .flatMap(skill -> (skill.getKnowledgeBaseIds() == null
                        ? List.<Integer>of() : skill.getKnowledgeBaseIds()).stream())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private void requireCustomSkill(SkillRecord record) {
        if (!Boolean.TRUE.equals(record.getSkill().getCustom())) {
            throw new IllegalArgumentException("仅允许操作页面创建的自定义 Skill: " + record.getSkill().getId());
        }
    }

    private void requireEditableSkill(SkillRecord record) {
        if (!Boolean.TRUE.equals(record.getSkill().getEditable())) {
            throw new IllegalArgumentException("该 Skill 不允许编辑: " + record.getSkill().getId());
        }
    }

    private void validateSaveDto(SkillSaveDto dto, boolean create) {
        if (create) {
            String id = StringUtils.trimToEmpty(dto.getId());
            if (!SAFE_ID_PATTERN.matcher(id).matches()) {
                throw new IllegalArgumentException("非法 Skill ID: " + dto.getId());
            }
        }
        if (StringUtils.isBlank(dto.getName())) {
            throw new IllegalArgumentException("Skill 名称不能为空");
        }
        if (create && StringUtils.isBlank(dto.getContent())) {
            throw new IllegalArgumentException("Skill 入口内容不能为空");
        }
        if (StringUtils.isNotBlank(dto.getContent())
                && dto.getContent().getBytes(StandardCharsets.UTF_8).length > MAX_SKILL_CONTENT_BYTES) {
            throw new IllegalArgumentException("Skill 入口内容超过大小限制 200KB");
        }
        SkillVo probe = new SkillVo();
        probe.setId(StringUtils.defaultIfBlank(dto.getId(), "probe"));
        probe.setName(dto.getName());
        probe.setRuntime(buildRuntimeVo(dto));
        validateRuntimeTools(probe);
        if (Boolean.TRUE.equals(dto.getChatEnabled())
                && StringUtils.isNotBlank(dto.getChatAgentType())
                && !isSupportedChatAgentType(dto.getChatAgentType().trim())) {
            throw new IllegalArgumentException("Skill 聊天 Agent 类型不受支持: " + dto.getChatAgentType());
        }
    }

    private SkillRuntimeConfigVo buildRuntimeVo(SkillSaveDto dto) {
        SkillRuntimeToolsVo tools = new SkillRuntimeToolsVo();
        tools.setLocal(toServiceToolMap(dto.getLocalTools()));
        tools.setMcp(toServerToolMap(dto.getMcpTools()));
        SkillRuntimeLimitsVo limits = new SkillRuntimeLimitsVo(
                positiveOrNull(dto.getMaxToolCalls()),
                positiveOrNull(dto.getMaxRepeatedFailures()),
                positiveOrNull(dto.getMaxToolResultChars()),
                positiveOrNull(dto.getMaxAccumulatedToolResultChars()),
                null);
        boolean hasTools = !tools.getLocal().isEmpty() || !tools.getMcp().isEmpty();
        boolean hasLimits = limits.getMaxToolCalls() != null
                || limits.getMaxRepeatedFailures() != null
                || limits.getMaxToolResultChars() != null
                || limits.getMaxAccumulatedToolResultChars() != null;
        if (!hasTools && !hasLimits && StringUtils.isBlank(dto.getPromptMode())) {
            return null;
        }
        return new SkillRuntimeConfigVo(
                StringUtils.trimToNull(dto.getPromptMode()),
                hasTools ? tools : null,
                hasLimits ? limits : null);
    }

    private Map<String, Object> buildManifest(SkillSaveDto dto,
                                              String id,
                                              String fallbackEntry,
                                              Map<String, Object> existingManifest,
                                              boolean custom,
                                              String source) {
        Map<String, Object> manifest = existingManifest == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(existingManifest);
        manifest.put("id", id);
        manifest.put("name", dto.getName().trim());
        if (StringUtils.isNotBlank(dto.getDescription())) {
            manifest.put("description", dto.getDescription().trim());
        } else {
            manifest.remove("description");
        }
        if (StringUtils.isNotBlank(dto.getVersion())) {
            manifest.put("version", dto.getVersion().trim());
        } else {
            manifest.remove("version");
        }
        if (StringUtils.isNotBlank(dto.getAuthor())) {
            manifest.put("author", dto.getAuthor().trim());
        } else {
            manifest.remove("author");
        }
        List<String> agentTypes = normalizeStringList(dto.getAgentTypes());
        if (!agentTypes.isEmpty()) {
            manifest.put("agentTypes", agentTypes);
        } else {
            manifest.remove("agentTypes");
        }
        List<String> tags = normalizeStringList(dto.getTags());
        if (!tags.isEmpty()) {
            manifest.put("tags", tags);
        } else {
            manifest.remove("tags");
        }
        manifest.put("enabled", Boolean.TRUE.equals(dto.getEnabled()));
        manifest.put("custom", custom);
        manifest.put("editable", true);
        manifest.put("source", source);
        manifest.put("entry", StringUtils.defaultIfBlank(fallbackEntry, DEFAULT_ENTRY_FILE));
        if (dto.getKnowledgeBaseIds() != null) {
            List<Integer> kbIds = dto.getKnowledgeBaseIds().stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
            if (!kbIds.isEmpty()) {
                manifest.put("knowledgeBaseIds", kbIds);
            } else {
                manifest.remove("knowledgeBaseIds");
            }
            manifest.remove("knowledgeBaseCodes");
        }
        Map<String, Object> chat = buildChatManifest(dto);
        if (chat != null) {
            manifest.put("chat", chat);
        } else {
            manifest.remove("chat");
        }
        Map<String, Object> runtime = buildRuntimeManifest(dto);
        if (runtime != null) {
            manifest.put("runtime", runtime);
        } else {
            manifest.remove("runtime");
        }
        return manifest;
    }

    private Map<String, Object> buildChatManifest(SkillSaveDto dto) {
        if (!Boolean.TRUE.equals(dto.getChatEnabled())) {
            return null;
        }
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("enabled", true);
        if (StringUtils.isNotBlank(dto.getChatLabel())) {
            chat.put("label", dto.getChatLabel().trim());
        }
        if (StringUtils.isNotBlank(dto.getChatIcon())) {
            chat.put("icon", dto.getChatIcon().trim());
        }
        if (dto.getChatOrder() != null) {
            chat.put("order", dto.getChatOrder());
        }
        if (StringUtils.isNotBlank(dto.getChatAgentType())) {
            chat.put("agentType", dto.getChatAgentType().trim());
        }
        if (StringUtils.isNotBlank(dto.getChatPrologue())) {
            chat.put("prologue", dto.getChatPrologue().trim());
        }
        return chat;
    }

    private Map<String, Object> buildRuntimeManifest(SkillSaveDto dto) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(dto.getPromptMode())) {
            runtime.put("promptMode", dto.getPromptMode().trim());
        }
        Map<String, Object> tools = new LinkedHashMap<>();
        Map<String, List<String>> local = toServiceToolMap(dto.getLocalTools());
        if (!local.isEmpty()) {
            tools.put("local", local);
        }
        Map<String, List<String>> mcp = toServerToolMap(dto.getMcpTools());
        if (!mcp.isEmpty()) {
            tools.put("mcp", mcp);
        }
        if (!tools.isEmpty()) {
            runtime.put("tools", tools);
        }
        Map<String, Object> limits = new LinkedHashMap<>();
        putIfPositive(limits, "maxToolCalls", dto.getMaxToolCalls());
        putIfPositive(limits, "maxRepeatedFailures", dto.getMaxRepeatedFailures());
        putIfPositive(limits, "maxToolResultChars", dto.getMaxToolResultChars());
        putIfPositive(limits, "maxAccumulatedToolResultChars", dto.getMaxAccumulatedToolResultChars());
        if (!limits.isEmpty()) {
            runtime.put("limits", limits);
        }
        return runtime.isEmpty() ? null : runtime;
    }

    private Map<String, List<String>> toServiceToolMap(List<SkillSaveDto.ServiceToolsDto> serviceTools) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (serviceTools == null) {
            return map;
        }
        for (SkillSaveDto.ServiceToolsDto entry : serviceTools) {
            if (entry == null || StringUtils.isBlank(entry.getService())) {
                continue;
            }
            List<String> tools = normalizeStringList(entry.getTools());
            if (!tools.isEmpty()) {
                map.put(entry.getService().trim(), tools);
            }
        }
        return map;
    }

    private Map<String, List<String>> toServerToolMap(List<SkillSaveDto.ServerToolsDto> serverTools) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (serverTools == null) {
            return map;
        }
        for (SkillSaveDto.ServerToolsDto entry : serverTools) {
            if (entry == null || StringUtils.isBlank(entry.getServer())) {
                continue;
            }
            List<String> tools = normalizeStringList(entry.getTools());
            if (!tools.isEmpty()) {
                map.put(entry.getServer().trim(), tools);
            }
        }
        return map;
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private void putIfPositive(Map<String, Object> map, String key, Integer value) {
        if (value != null && value > 0) {
            map.put(key, value);
        }
    }

    private void writeSkillFiles(Path skillDir, Map<String, Object> manifest, String content) {
        try {
            Files.createDirectories(skillDir);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(skillDir.resolve(DEFAULT_MANIFEST_FILE).toFile(), manifest);
            if (content != null) {
                String entryName = StringUtils.defaultIfBlank(
                        manifest.get("entry") == null ? null : manifest.get("entry").toString(), DEFAULT_ENTRY_FILE);
                Files.writeString(safeResolve(skillDir, entryName), content, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("写入 Skill 文件失败: " + manifest.get("id"), e);
        }
    }

    private void extractZip(InputStream inputStream, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            int count = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_PACKAGE_ENTRIES) {
                    throw new IOException("技能包文件数量超过限制 " + MAX_PACKAGE_ENTRIES);
                }
                String name = entry.getName().replace("\\", "/");
                if (StringUtils.isBlank(name) || name.startsWith("__MACOSX/")) {
                    continue;
                }
                Path target = targetDir.resolve(name).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IOException("技能包包含非法路径: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                if (Files.size(target) > MAX_PACKAGE_FILE_BYTES) {
                    throw new IOException("技能包内文件超过大小限制 10MB: " + name);
                }
            }
        }
    }

    private Path locatePackageRoot(Path tempDir) throws IOException {
        if (Files.exists(tempDir.resolve(DEFAULT_MANIFEST_FILE)) || Files.exists(tempDir.resolve("index.json"))) {
            return tempDir;
        }
        try (Stream<Path> paths = Files.list(tempDir)) {
            List<Path> dirs = paths.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
            for (Path dir : dirs) {
                if (Files.exists(dir.resolve(DEFAULT_MANIFEST_FILE)) || Files.exists(dir.resolve("index.json"))) {
                    return dir;
                }
            }
        }
        throw new IllegalArgumentException("技能包缺少 skill.json 或 index.json 清单文件");
    }

    /**
     * 将启用的 skill 汇总为 Agent 可注入的提示词片段。
     */
    public String buildEnabledSkillPrompt(String agentType) {
        return buildSkillPrompt(agentType, Set.of());
    }

    /**
     * 为 Agent 构建 Skill 提示词。显式 Skill 列表存在时只加载指定项；
     * 未指定时兼容按 agentTypes 匹配的既有行为。
     */
    public String buildAgentSkillPrompt(String agentType, List<String> explicitSkillIds) {
        List<String> normalizedSkillIds = explicitSkillIds == null
                ? List.of()
                : explicitSkillIds.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (normalizedSkillIds.isEmpty()) {
            return buildEnabledSkillPrompt(agentType);
        }
        validateEnabledSkillIds(normalizedSkillIds);
        Set<String> selectedIds = Set.copyOf(normalizedSkillIds);
        List<SkillRecord> records = skillCache.values().stream()
                .filter(record -> selectedIds.contains(record.getSkill().getId()))
                .sorted(Comparator.comparing(record -> record.getSkill().getId()))
                .toList();
        return buildSelectedSkillPrompt(records);
    }

    /**
     * 将启用的 skill 与指定的强制 skill 汇总为 Agent 可注入的提示词片段。
     */
    public String buildRequiredSkillPrompt(String agentType, List<String> requiredSkillIds) {
        Set<String> requiredIds = requiredSkillIds == null ? Set.of() : requiredSkillIds.stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (requiredIds.isEmpty()) {
            return buildEnabledSkillPrompt(agentType);
        }
        for (String requiredId : requiredIds) {
            if (!skillCache.containsKey(requiredId)) {
                log.warn("强制加载的 Skill 未找到: {}", requiredId);
            }
        }
        return buildSkillPrompt(agentType, requiredIds);
    }

    /**
     * Build a task prompt after strictly validating that every explicitly selected Skill still exists and is enabled.
     */
    public String buildTaskSkillPrompt(String agentType, List<String> selectedSkillIds) {
        return buildAgentSkillPrompt(agentType, selectedSkillIds);
    }

    /**
     * Validate task-selected Skills independently from their declared Agent type.
     */
    public void validateEnabledSkillIds(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return;
        }
        List<String> invalid = skillIds.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .filter(id -> {
                    SkillRecord record = skillCache.get(id);
                    return record == null || !Boolean.TRUE.equals(record.getSkill().getEnabled());
                })
                .sorted()
                .toList();
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("以下 Skill 不存在或未启用: " + String.join(", ", invalid));
        }
    }

    /**
     * 合并显式选择 Skill 的可选运行时约束。
     *
     * <p>工具白名单取并集；数值预算取所有 Skill 中最严格的正数值。没有任何
     * Skill 声明 runtime 时返回 {@code null}，保持旧行为。</p>
     */
    public SkillRuntimeConfigVo resolveRuntimeConfig(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return null;
        }
        List<SkillRuntimeConfigVo> runtimes = skillIds.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .map(skillCache::get)
                .filter(java.util.Objects::nonNull)
                .map(SkillRecord::getSkill)
                .filter(skill -> Boolean.TRUE.equals(skill.getEnabled()))
                .map(SkillVo::getRuntime)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (runtimes.isEmpty()) {
            return null;
        }

        SkillRuntimeConfigVo merged = new SkillRuntimeConfigVo();
        merged.setPromptMode(runtimes.stream()
                .map(SkillRuntimeConfigVo::getPromptMode)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null));

        Map<String, LinkedHashSet<String>> localTools = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> mcpTools = new LinkedHashMap<>();
        for (SkillRuntimeConfigVo runtime : runtimes) {
            if (runtime.getTools() == null) {
                continue;
            }
            if (runtime.getTools().getLocal() != null) {
                mergeToolMap(localTools, runtime.getTools().getLocal());
            }
            if (runtime.getTools().getMcp() != null) {
                runtime.getTools().getMcp().forEach((serverCode, toolNames) -> {
                    if (StringUtils.isBlank(serverCode) || toolNames == null) {
                        return;
                    }
                    LinkedHashSet<String> mergedNames = mcpTools.computeIfAbsent(
                            serverCode.trim(), ignored -> new LinkedHashSet<>());
                    toolNames.stream()
                            .filter(StringUtils::isNotBlank)
                            .map(String::trim)
                            .forEach(mergedNames::add);
                });
            }
        }
        SkillRuntimeToolsVo tools = new SkillRuntimeToolsVo();
        Map<String, List<String>> local = new LinkedHashMap<>();
        localTools.forEach((serverCode, toolNames) ->
                local.put(serverCode, new ArrayList<>(toolNames)));
        tools.setLocal(local);
        Map<String, List<String>> external = new LinkedHashMap<>();
        mcpTools.forEach((serverCode, toolNames) ->
                external.put(serverCode, new ArrayList<>(toolNames)));
        tools.setMcp(external);
        merged.setTools(tools);

        SkillRuntimeLimitsVo limits = new SkillRuntimeLimitsVo();
        limits.setMaxToolCalls(minPositive(runtimes, runtime ->
                runtime.getLimits() == null ? null : runtime.getLimits().getMaxToolCalls()));
        limits.setMaxRepeatedFailures(minPositive(runtimes, runtime ->
                runtime.getLimits() == null ? null : runtime.getLimits().getMaxRepeatedFailures()));
        limits.setMaxToolResultChars(minPositive(runtimes, runtime ->
                runtime.getLimits() == null ? null : runtime.getLimits().getMaxToolResultChars()));
        limits.setMaxAccumulatedToolResultChars(minPositive(runtimes, runtime ->
                runtime.getLimits() == null ? null : runtime.getLimits().getMaxAccumulatedToolResultChars()));
        limits.setMaxAccumulatedToolResultTokens(minPositive(runtimes, runtime ->
                runtime.getLimits() == null ? null : runtime.getLimits().getMaxAccumulatedToolResultTokens()));
        merged.setLimits(limits);
        return merged;
    }

    private void mergeToolMap(Map<String, LinkedHashSet<String>> destination,
                              Map<String, List<String>> source) {
        source.forEach((serverCode, toolNames) -> {
            if (StringUtils.isBlank(serverCode) || toolNames == null) {
                return;
            }
            LinkedHashSet<String> mergedNames = destination.computeIfAbsent(
                    serverCode.trim(), ignored -> new LinkedHashSet<>());
            toolNames.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .forEach(mergedNames::add);
        });
    }

    private Integer minPositive(List<SkillRuntimeConfigVo> runtimes,
                                java.util.function.Function<SkillRuntimeConfigVo, Integer> extractor) {
        return runtimes.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .filter(value -> value > 0)
                .min(Integer::compareTo)
                .orElse(null);
    }

    public List<SkillOptionVo> getEnabledOptions() {
        return getAll().stream()
                .filter(skill -> Boolean.TRUE.equals(skill.getEnabled()))
                .map(skill -> new SkillOptionVo(
                        skill.getName() + " (" + skill.getId() + ")",
                        skill.getId(),
                        skill.getDescription(),
                        skill.getAgentTypes() == null ? new ArrayList<>() : new ArrayList<>(skill.getAgentTypes())
                ))
                .toList();
    }

    private String buildSkillPrompt(String agentType, Set<String> requiredSkillIds) {
        List<SkillRecord> records = skillCache.values().stream()
                .filter(record -> shouldLoadSkill(record, agentType, requiredSkillIds))
                .sorted(Comparator.comparing(record -> record.getSkill().getId()))
                .toList();
        return buildSkillPrompt(records);
    }

    private String buildSkillPrompt(List<SkillRecord> records) {
        StringBuilder prompt = new StringBuilder();
        for (SkillRecord record : records) {
            try {
                String content = readSkillContent(record.getEntryPath()).trim();
                if (StringUtils.isBlank(content)) {
                    continue;
                }
                String block = "### " + record.getSkill().getName() + " (" + record.getSkill().getId() + ")\n"
                        + content + "\n";
                int remain = MAX_PROMPT_CHARS - prompt.length();
                if (remain <= 0) {
                    break;
                }
                if (block.length() > remain) {
                    prompt.append(block, 0, remain);
                    break;
                }
                prompt.append(block).append("\n");
            } catch (IOException e) {
                log.warn("读取已启用 Skill 失败: {}", record.getSkill().getId(), e);
            }
        }
        return prompt.toString().trim();
    }

    private String buildSelectedSkillPrompt(List<SkillRecord> records) {
        StringBuilder prompt = new StringBuilder();
        int configuredLimit = Math.max(maxSelectedPromptChars, 1);
        for (SkillRecord record : records) {
            try {
                String content = readSkillContent(record.getEntryPath()).trim();
                if (StringUtils.isBlank(content)) {
                    continue;
                }
                String block = "### " + record.getSkill().getName() + " (" + record.getSkill().getId() + ")\n"
                        + content + "\n";
                if (prompt.length() + block.length() > configuredLimit) {
                    throw new IllegalArgumentException(
                            "Skill 提示词超过上限 " + configuredLimit + " 字符: " + record.getSkill().getId()
                    );
                }
                prompt.append(block).append("\n");
            } catch (IOException e) {
                throw new IllegalStateException("读取已选择 Skill 失败: " + record.getSkill().getId(), e);
            }
        }
        return prompt.toString().trim();
    }

    private boolean shouldLoadSkill(SkillRecord record, String agentType, Set<String> requiredSkillIds) {
        if (requiredSkillIds.contains(record.getSkill().getId())) {
            return true;
        }
        return Boolean.TRUE.equals(record.getSkill().getEnabled())
                && matchAgentType(record.getSkill(), agentType);
    }

    /**
     * 安装插件包内的 Skill，并重新加载本地 Skill 注册表。
     */
    public List<SkillVo> installPluginSkills(String packageName, Path pluginSkillPath) {
        validatePackageName(packageName);
        if (pluginSkillPath == null || !Files.exists(pluginSkillPath) || !Files.isDirectory(pluginSkillPath)) {
            log.info("插件未包含 Skill 目录: package={}, path={}", packageName, pluginSkillPath);
            return reload();
        }

        Path targetRoot = getPluginSkillRoot(packageName);
        try {
            if (Files.exists(targetRoot)) {
                WalkFileUtil.delete(targetRoot);
            }
            Files.createDirectories(targetRoot);

            List<Path> sourceSkillDirs = listSourceSkillDirs(pluginSkillPath);
            for (Path sourceSkillDir : sourceSkillDirs) {
                WalkFileUtil.copy(sourceSkillDir, targetRoot.resolve(sourceSkillDir.getFileName().toString()));
            }
            log.info("插件 Skill 安装完成: package={}, count={}", packageName, sourceSkillDirs.size());
        } catch (IOException e) {
            throw new IllegalStateException("安装插件 Skill 失败: " + packageName, e);
        }
        return reload();
    }

    /**
     * 卸载插件安装的 Skill，并重新加载本地 Skill 注册表。
     */
    public List<SkillVo> uninstallPluginSkills(String packageName) {
        validatePackageName(packageName);
        Path targetRoot = getPluginSkillRoot(packageName);
        try {
            if (Files.exists(targetRoot)) {
                WalkFileUtil.delete(targetRoot);
            }
            log.info("插件 Skill 卸载完成: package={}", packageName);
        } catch (IOException e) {
            throw new IllegalStateException("卸载插件 Skill 失败: " + packageName, e);
        }
        return reload();
    }

    public Path getInstalledPluginSkillPath(String packageName) {
        validatePackageName(packageName);
        return getPluginSkillRoot(packageName);
    }

    private List<Path> listSkillDirs(Path root) throws IOException {
        List<Path> skillDirs = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> !PLUGIN_SKILL_ROOT_DIR.equals(path.getFileName().toString()))
                    .filter(this::isSkillDir)
                    .forEach(skillDirs::add);
        }

        Path pluginRoot = root.resolve(PLUGIN_SKILL_ROOT_DIR);
        if (Files.exists(pluginRoot) && Files.isDirectory(pluginRoot)) {
            try (Stream<Path> packagePaths = Files.list(pluginRoot)) {
                List<Path> packageDirs = packagePaths.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .collect(Collectors.toList());
                for (Path packageDir : packageDirs) {
                    try (Stream<Path> paths = Files.list(packageDir)) {
                        paths.filter(Files::isDirectory)
                                .filter(this::isSkillDir)
                                .forEach(skillDirs::add);
                    }
                }
            }
        }

        return skillDirs.stream()
                .sorted(Comparator.comparing(path -> root.relativize(path.toAbsolutePath().normalize()).toString()))
                .collect(Collectors.toList());
    }

    private List<Path> listSourceSkillDirs(Path pluginSkillPath) throws IOException {
        Path sourceRoot = pluginSkillPath.toAbsolutePath().normalize();
        if (isSkillDir(sourceRoot)) {
            return List.of(sourceRoot);
        }
        try (Stream<Path> paths = Files.list(sourceRoot)) {
            return paths.filter(Files::isDirectory)
                    .filter(this::isSkillDir)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    private void loadSkill(Path skillDir, Path root, Map<String, SkillRecord> nextCache) {
        try {
            Path manifestPath = resolveManifest(skillDir);
            SkillVo skill = readSkillManifest(manifestPath);
            String pluginPackage = resolvePluginPackage(skillDir, root);
            boolean pluginSkill = pluginPackage != null;
            if (StringUtils.isBlank(skill.getId())) {
                skill.setId(skillDir.getFileName().toString());
            }
            if (!SAFE_ID_PATTERN.matcher(skill.getId()).matches()) {
                log.warn("忽略非法 Skill ID: {}", skill.getId());
                return;
            }
            if (StringUtils.isBlank(skill.getName())) {
                skill.setName(skill.getId());
            }
            if (pluginSkill) {
                skill.setCustom(false);
                skill.setSource(pluginPackage);
                if (skill.getEditable() == null) {
                    skill.setEditable(true);
                }
            } else if (Boolean.TRUE.equals(skill.getCustom())) {
                skill.setSource("custom");
                if (skill.getEditable() == null) {
                    skill.setEditable(true);
                }
            } else {
                skill.setSource(StringUtils.defaultIfBlank(skill.getSource(), "builtin"));
                if (skill.getEditable() == null) {
                    skill.setEditable(false);
                }
            }
            resolvePluginKnowledgeBaseIds(skill, pluginPackage);
            if (skill.getAgentTypes() == null) {
                skill.setAgentTypes(new ArrayList<>());
            }
            if (pluginSkill && skill.getAgentTypes().isEmpty()) {
                skill.setAgentTypes(new ArrayList<>(List.of(DEFAULT_PLUGIN_AGENT_TYPE)));
            }
            if (skill.getTags() == null) {
                skill.setTags(new ArrayList<>());
            }
            if (skill.getEnabled() == null) {
                skill.setEnabled(false);
            }
            validateRuntimeTools(skill);
            skill.setEntry(StringUtils.defaultIfBlank(skill.getEntry(), DEFAULT_ENTRY_FILE));
            Path entryPath = safeResolve(skillDir, skill.getEntry());
            if (!Files.exists(entryPath) || !Files.isRegularFile(entryPath)) {
                log.warn("忽略缺少入口文件的 Skill: {}, entry={}", skill.getId(), skill.getEntry());
                return;
            }
            skill.setPath(root.relativize(skillDir.toAbsolutePath().normalize()).toString().replace("\\", "/"));
            skill.setUpdateTime(resolveUpdateTime(manifestPath, entryPath));
            nextCache.put(skill.getId(), new SkillRecord(skill, skillDir, manifestPath, entryPath));
        } catch (Exception e) {
            log.warn("加载 Skill 失败: {}, reason={}", skillDir, e.getMessage(), e);
        }
    }

    private void validateRuntimeTools(SkillVo skill) {
        if (skill.getRuntime() == null || skill.getRuntime().getTools() == null) {
            return;
        }
        Map<String, List<String>> local = skill.getRuntime().getTools().getLocal();
        if (local == null) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : local.entrySet()) {
            String serviceCode = StringUtils.trimToEmpty(entry.getKey());
            BuiltinMcpServiceDefinition service = BuiltinMcpServiceDefinition.findByCode(serviceCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Skill 声明未知内置 MCP 服务: " + serviceCode));
            List<String> toolNames = entry.getValue();
            if (toolNames == null) {
                throw new IllegalArgumentException(
                        "Skill 内置 MCP 服务工具列表不能为空: " + serviceCode);
            }
            if (toolNames.stream().anyMatch(StringUtils::isBlank)) {
                throw new IllegalArgumentException(
                        "Skill 内置 MCP 服务工具名不能为空: " + serviceCode);
            }
            List<String> normalized = toolNames.stream()
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (normalized.contains("*")
                    && (toolNames.size() != 1 || normalized.size() != 1)) {
                throw new IllegalArgumentException(
                        "Skill 内置 MCP 服务的 * 不能与具体工具混用: " + serviceCode);
            }
            normalized.stream()
                    .filter(toolName -> !"*".equals(toolName))
                    .filter(toolName -> !service.containsTool(toolName))
                    .findFirst()
                    .ifPresent(toolName -> {
                        throw new IllegalArgumentException(
                                "Skill 工具不属于内置 MCP 服务: "
                                        + serviceCode + " -> " + toolName);
                    });
        }
    }

    private AgentSkillVo toAgentSkillVo(BuiltinAgentSkillRegistry.BuiltinAgentSkill builtinAgentSkill) {
        SkillRecord record = skillCache.get(builtinAgentSkill.skillId());
        SkillVo skill = record == null ? null : record.getSkill();
        AgentSkillVo agentSkillVo = new AgentSkillVo();
        agentSkillVo.setSkillId(builtinAgentSkill.skillId());
        agentSkillVo.setAgentType(builtinAgentSkill.agentType());
        agentSkillVo.setLabel(builtinAgentSkill.label());
        agentSkillVo.setOrder(builtinAgentSkill.order());
        agentSkillVo.setName(skill == null ? builtinAgentSkill.label() : skill.getName());
        agentSkillVo.setDescription(skill == null ? builtinAgentSkill.placeholderMessage() : skill.getDescription());
        agentSkillVo.setEnabled(skill != null && Boolean.TRUE.equals(skill.getEnabled()));
        agentSkillVo.setPath(skill == null ? null : skill.getPath());
        agentSkillVo.setUpdateTime(skill == null ? null : skill.getUpdateTime());
        return agentSkillVo;
    }

    private SkillChatEntryVo toChatEntrySafely(SkillVo skill) {
        try {
            return toChatEntry(skill);
        } catch (IllegalArgumentException e) {
            log.warn("忽略无效的 Skill 聊天入口: skillId={}, reason={}", skill.getId(), e.getMessage());
            return null;
        }
    }

    private SkillChatEntryVo toChatEntry(SkillVo skill) {
        SkillChatConfigVo chat = skill.getChat();
        if (chat == null || !Boolean.TRUE.equals(chat.getEnabled())) {
            throw new IllegalArgumentException("Skill 未开放 DIH 聊天入口: " + skill.getId());
        }
        String agentType = resolveChatAgentType(skill, chat);
        String chatType = BuiltinAgentSkillRegistry.findBySkillId(skill.getId())
                .filter(agent -> agent.agentType().equals(agentType))
                .map(BuiltinAgentSkillRegistry.BuiltinAgentSkill::agentType)
                .orElse(CHAT_TYPE_PREFIX + skill.getId());
        return new SkillChatEntryVo(
                skill.getId(),
                chatType,
                agentType,
                StringUtils.defaultIfBlank(chat.getLabel(), skill.getName()),
                skill.getDescription(),
                StringUtils.defaultIfBlank(chat.getIcon(), DEFAULT_CHAT_ICON),
                chat.getOrder() == null ? DEFAULT_CHAT_ORDER : chat.getOrder()
        );
    }

    private String resolveChatAgentType(SkillVo skill, SkillChatConfigVo chat) {
        String configuredAgentType = StringUtils.trimToNull(chat.getAgentType());
        if (configuredAgentType != null) {
            if (!isSupportedChatAgentType(configuredAgentType)) {
                throw new IllegalArgumentException("Skill 聊天 Agent 类型不受支持: " + configuredAgentType);
            }
            return configuredAgentType;
        }
        List<String> supportedAgentTypes = skill.getAgentTypes() == null ? List.of() : skill.getAgentTypes().stream()
                .filter(this::isSupportedBusinessAgentType)
                .distinct()
                .toList();
        return supportedAgentTypes.size() == 1
                ? supportedAgentTypes.get(0)
                : GENERIC_SKILL_AGENT_TYPE;
    }

    private boolean isSupportedChatAgentType(String agentType) {
        return GENERIC_SKILL_AGENT_TYPE.equals(agentType) || isSupportedBusinessAgentType(agentType);
    }

    private boolean isSupportedBusinessAgentType(String agentType) {
        return BuiltinAgentSkillRegistry.isBuiltinAgentType(agentType);
    }

    private String resolvePluginPackage(Path skillDir, Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedSkillDir = skillDir.toAbsolutePath().normalize();
        if (!normalizedSkillDir.startsWith(normalizedRoot)) {
            return null;
        }
        Path relativePath = normalizedRoot.relativize(normalizedSkillDir);
        if (relativePath.getNameCount() != 3
                || !PLUGIN_SKILL_ROOT_DIR.equals(relativePath.getName(0).toString())) {
            return null;
        }
        String packageName = relativePath.getName(1).toString();
        if (!SAFE_PACKAGE_PATTERN.matcher(packageName).matches()) {
            log.warn("忽略非法插件 Skill 包名: {}", packageName);
            return null;
        }
        return packageName;
    }

    private void resolvePluginKnowledgeBaseIds(SkillVo skill, String pluginPackage) {
        List<String> codes = skill.getKnowledgeBaseCodes();
        if (StringUtils.isBlank(pluginPackage) || CollectionUtils.isEmpty(codes)) {
            return;
        }
        if (ragKnowledgeBaseService == null) {
            log.warn("无法解析插件 Skill 知识库编码，RAG 服务未就绪: skillId={}, package={}",
                    skill.getId(), pluginPackage);
            return;
        }
        try {
            List<Integer> knowledgeBaseIds = ragKnowledgeBaseService
                    .resolveManagedKnowledgeBaseIds(pluginPackage, codes);
            skill.setKnowledgeBaseIds(knowledgeBaseIds);
            if (knowledgeBaseIds.isEmpty()) {
                log.warn("插件 Skill 未解析到托管知识库: skillId={}, package={}, codes={}",
                        skill.getId(), pluginPackage, codes);
            }
        } catch (Exception e) {
            log.warn("解析插件 Skill 知识库编码失败: skillId={}, package={}, codes={}",
                    skill.getId(), pluginPackage, codes, e);
        }
    }

    private SkillVo readSkillManifest(Path manifestPath) throws IOException {
        if (!Files.exists(manifestPath)) {
            return new SkillVo();
        }
        JsonNode manifest = objectMapper.readTree(manifestPath.toFile());
        JsonNode localTools = manifest.path("runtime").path("tools").get("local");
        if (localTools != null && localTools.isArray()) {
            throw new IllegalArgumentException(
                    "Skill runtime.tools.local 旧数组格式已不支持，必须改为内置 MCP 服务映射");
        }
        return objectMapper.readerFor(SkillVo.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(manifest);
    }

    private Map<String, Object> readManifestAsMap(SkillRecord record) throws IOException {
        if (!Files.exists(record.getManifestPath())) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(record.getManifestPath().toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private String readSkillContent(Path entryPath) throws IOException {
        long size = Files.size(entryPath);
        if (size > MAX_SKILL_CONTENT_BYTES) {
            throw new IllegalStateException("Skill 文件过大: " + entryPath.getFileName());
        }
        return Files.readString(entryPath, StandardCharsets.UTF_8);
    }

    private Path resolveManifest(Path skillDir) {
        Path skillJson = skillDir.resolve(DEFAULT_MANIFEST_FILE);
        if (Files.exists(skillJson)) {
            return skillJson;
        }
        Path indexJson = skillDir.resolve("index.json");
        if (Files.exists(indexJson)) {
            return indexJson;
        }
        return skillJson;
    }

    private boolean isSkillDir(Path skillDir) {
        return Files.exists(skillDir.resolve(DEFAULT_ENTRY_FILE))
                || Files.exists(skillDir.resolve(DEFAULT_MANIFEST_FILE))
                || Files.exists(skillDir.resolve("index.json"));
    }

    private Date resolveUpdateTime(Path manifestPath, Path entryPath) throws IOException {
        long latest = 0L;
        if (Files.exists(manifestPath)) {
            latest = Math.max(latest, Files.getLastModifiedTime(manifestPath).toMillis());
        }
        FileTime entryTime = Files.getLastModifiedTime(entryPath);
        latest = Math.max(latest, entryTime.toMillis());
        return new Date(latest);
    }

    private Path safeResolve(Path root, String relativePath) {
        Path base = root.toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("非法 Skill 文件路径: " + relativePath);
        }
        return resolved;
    }

    private SkillRecord getRecord(String id) {
        if (!SAFE_ID_PATTERN.matcher(StringUtils.defaultString(id)).matches()) {
            throw new IllegalArgumentException("非法 Skill ID: " + id);
        }
        SkillRecord record = skillCache.get(id);
        if (record == null) {
            throw new IllegalArgumentException("Skill 不存在: " + id);
        }
        return record;
    }

    private boolean matchKeyword(SkillVo skill, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        String lowerKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        String tagText = CollectionUtils.isEmpty(skill.getTags()) ? "" : StringUtils.join(skill.getTags(), " ");
        String content = StringUtils.defaultString(skill.getId()) + " "
                + StringUtils.defaultString(skill.getName()) + " "
                + StringUtils.defaultString(skill.getDescription()) + " "
                + tagText;
        return content.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private boolean matchAgentType(SkillVo skill, String agentType) {
        if (StringUtils.isBlank(agentType)) {
            return true;
        }
        if (CollectionUtils.isEmpty(skill.getAgentTypes())) {
            return true;
        }
        return skill.getAgentTypes().contains(agentType);
    }

    private boolean matchSource(SkillVo skill, String source) {
        if (StringUtils.isBlank(source)) {
            return true;
        }
        return StringUtils.containsIgnoreCase(skill.getSource(), source.trim());
    }

    private List<SkillVo> getAll() {
        return skillCache.values().stream()
                .map(SkillRecord::getSkill)
                .sorted(Comparator.comparing(SkillVo::getId))
                .collect(Collectors.toList());
    }

    private Path getSkillRoot() {
        return Paths.get(customWebConfig.getSkillPath()).toAbsolutePath().normalize();
    }

    private Path getPluginSkillRoot(String packageName) {
        return getSkillRoot().resolve(PLUGIN_SKILL_ROOT_DIR).resolve(packageName).normalize();
    }

    private void validatePackageName(String packageName) {
        if (!SAFE_PACKAGE_PATTERN.matcher(StringUtils.defaultString(packageName)).matches()) {
            throw new IllegalArgumentException("非法插件包名: " + packageName);
        }
    }

    private SkillDetailVo copyToDetail(SkillVo skill) {
        SkillDetailVo detail = new SkillDetailVo();
        detail.setId(skill.getId());
        detail.setName(skill.getName());
        detail.setDescription(skill.getDescription());
        detail.setVersion(skill.getVersion());
        detail.setAuthor(skill.getAuthor());
        detail.setSource(skill.getSource());
        detail.setEditable(skill.getEditable());
        detail.setAgentTypes(skill.getAgentTypes());
        detail.setTags(skill.getTags());
        detail.setEnabled(skill.getEnabled());
        detail.setCustom(skill.getCustom());
        detail.setKnowledgeBaseIds(skill.getKnowledgeBaseIds());
        detail.setKnowledgeBaseCodes(skill.getKnowledgeBaseCodes());
        detail.setChat(skill.getChat());
        detail.setRuntime(skill.getRuntime());
        detail.setEntry(skill.getEntry());
        detail.setPath(skill.getPath());
        detail.setUpdateTime(skill.getUpdateTime());
        return detail;
    }

    /**
     * 导出的 Skill 技能包：下载文件名与 zip 字节内容。
     */
    public record SkillPackage(String filename, byte[] content) {
    }

    @Data
    @AllArgsConstructor
    private static class SkillRecord {
        private SkillVo skill;
        private Path skillDir;
        private Path manifestPath;
        private Path entryPath;
    }
}
