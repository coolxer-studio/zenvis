package com.coolxer.service.dih.agent.skill;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.SkillSearchDto;
import com.coolxer.model.dih.vo.AgentSkillVo;
import com.coolxer.model.dih.vo.SkillDetailVo;
import com.coolxer.model.dih.vo.SkillVo;
import com.coolxer.utils.WalkFileUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地 Skill 注册表。
 *
 * <p>默认扫描 app.paths.skills 目录，目录结构示例：
 * skill_config/inspection-agent/skill.json + SKILL.md
 */
@Slf4j
@Service
public class SkillService {

    private static final String DEFAULT_ENTRY_FILE = "SKILL.md";
    private static final String DEFAULT_MANIFEST_FILE = "skill.json";
    private static final String PLUGIN_SKILL_ROOT_DIR = "plugins";
    private static final String DEFAULT_PLUGIN_AGENT_TYPE = "ask";
    private static final long MAX_SKILL_CONTENT_BYTES = 200 * 1024L;
    private static final int MAX_PROMPT_CHARS = 8000;
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$");
    private static final Pattern SAFE_PACKAGE_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,255}$");

    private final CustomWebConfig customWebConfig;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, SkillRecord> skillCache = new ConcurrentHashMap<>();

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
                .sorted(Comparator.comparing(SkillVo::getId))
                .collect(Collectors.toList());

        int page = Math.max(condition.getPage(), 1);
        int perPage = Math.max(condition.getPerPage(), 1);
        int from = Math.min((page - 1) * perPage, rows.size());
        int to = Math.min(from + perPage, rows.size());
        return new PageRowsVo<>(rows.subList(from, to), rows.size());
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
     * 将启用的 skill 汇总为 Agent 可注入的提示词片段。
     */
    public String buildEnabledSkillPrompt(String agentType) {
        return buildSkillPrompt(agentType, Set.of());
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

    private String buildSkillPrompt(String agentType, Set<String> requiredSkillIds) {
        StringBuilder prompt = new StringBuilder();
        List<SkillRecord> records = skillCache.values().stream()
                .filter(record -> shouldLoadSkill(record, agentType, requiredSkillIds))
                .sorted(Comparator.comparing(record -> record.getSkill().getId()))
                .toList();

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
            boolean pluginSkill = isPluginSkillDir(skillDir, root);
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
            log.warn("加载 Skill 失败: {}", skillDir, e);
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

    private boolean isPluginSkillDir(Path skillDir, Path root) {
        Path relativePath = root.toAbsolutePath().normalize()
                .relativize(skillDir.toAbsolutePath().normalize());
        return relativePath.getNameCount() > 0
                && PLUGIN_SKILL_ROOT_DIR.equals(relativePath.getName(0).toString());
    }

    private SkillVo readSkillManifest(Path manifestPath) throws IOException {
        if (!Files.exists(manifestPath)) {
            return new SkillVo();
        }
        return objectMapper.readerFor(SkillVo.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(manifestPath.toFile());
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
        detail.setAgentTypes(skill.getAgentTypes());
        detail.setTags(skill.getTags());
        detail.setEnabled(skill.getEnabled());
        detail.setEntry(skill.getEntry());
        detail.setPath(skill.getPath());
        detail.setUpdateTime(skill.getUpdateTime());
        return detail;
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
