package com.coolxer.configuration.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DIH 附件上传、解析、检索和工具预算。
 */
@Component
@ConfigurationProperties(prefix = "app.ai.dih.attachment")
public class AttachmentProperties {

    private long maxBytes = 100L * 1024L * 1024L;
    private int maxFilesPerMessage = 10;
    private Context context = new Context();
    private Parse parse = new Parse();
    private Tool tool = new Tool();
    private Vector vector = new Vector();
    private Vision vision = new Vision();

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int getMaxFilesPerMessage() {
        return maxFilesPerMessage;
    }

    public void setMaxFilesPerMessage(int maxFilesPerMessage) {
        this.maxFilesPerMessage = maxFilesPerMessage;
    }

    public int getTopK() {
        return context.getTopK();
    }

    public void setTopK(int topK) {
        context.setTopK(topK);
    }

    public int getMaxContextChars() {
        return context.getMaxChars();
    }

    public void setMaxContextChars(int maxContextChars) {
        context.setMaxChars(maxContextChars);
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context == null ? new Context() : context;
    }

    public Parse getParse() {
        return parse;
    }

    public void setParse(Parse parse) {
        this.parse = parse == null ? new Parse() : parse;
    }

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        this.tool = tool == null ? new Tool() : tool;
    }

    public Vector getVector() {
        return vector;
    }

    public void setVector(Vector vector) {
        this.vector = vector == null ? new Vector() : vector;
    }

    public Vision getVision() {
        return vision;
    }

    public void setVision(Vision vision) {
        this.vision = vision == null ? new Vision() : vision;
    }

    public static class Context {
        private int topK = 8;
        private int maxChars = 24_000;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int maxChars) { this.maxChars = maxChars; }
    }

    public static class Parse {
        private int concurrency = 2;
        private int timeoutSeconds = 600;
        private int maxPages = 1_000;
        private int maxSlides = 500;
        private int maxSheets = 100;
        private long maxTableRows = 1_000_000L;
        private int maxChunks = 50_000;
        private int maxRetries = 2;
        private int chunkChars = 1_800;
        private int chunkOverlapChars = 180;
        private int tableRowsPerChunk = 300;

        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxPages() { return maxPages; }
        public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
        public int getMaxSlides() { return maxSlides; }
        public void setMaxSlides(int maxSlides) { this.maxSlides = maxSlides; }
        public int getMaxSheets() { return maxSheets; }
        public void setMaxSheets(int maxSheets) { this.maxSheets = maxSheets; }
        public long getMaxTableRows() { return maxTableRows; }
        public void setMaxTableRows(long maxTableRows) { this.maxTableRows = maxTableRows; }
        public int getMaxChunks() { return maxChunks; }
        public void setMaxChunks(int maxChunks) { this.maxChunks = maxChunks; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public int getChunkChars() { return chunkChars; }
        public void setChunkChars(int chunkChars) { this.chunkChars = chunkChars; }
        public int getChunkOverlapChars() { return chunkOverlapChars; }
        public void setChunkOverlapChars(int chunkOverlapChars) { this.chunkOverlapChars = chunkOverlapChars; }
        public int getTableRowsPerChunk() { return tableRowsPerChunk; }
        public void setTableRowsPerChunk(int tableRowsPerChunk) { this.tableRowsPerChunk = tableRowsPerChunk; }
    }

    public static class Tool {
        private int maxCalls = 6;
        private int maxResultChars = 8_000;
        private int maxAccumulatedChars = 24_000;

        public int getMaxCalls() { return maxCalls; }
        public void setMaxCalls(int maxCalls) { this.maxCalls = maxCalls; }
        public int getMaxResultChars() { return maxResultChars; }
        public void setMaxResultChars(int maxResultChars) { this.maxResultChars = maxResultChars; }
        public int getMaxAccumulatedChars() { return maxAccumulatedChars; }
        public void setMaxAccumulatedChars(int maxAccumulatedChars) { this.maxAccumulatedChars = maxAccumulatedChars; }
    }

    public static class Vector {
        private int maxChunks = 5_000;
        private String index = "zenvis_attachment_vector";
        private String prefix = "zenvis_attachment_vector:";

        public int getMaxChunks() { return maxChunks; }
        public void setMaxChunks(int maxChunks) { this.maxChunks = maxChunks; }
        public String getIndex() { return index; }
        public void setIndex(String index) { this.index = index; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
    }

    public static class Vision {
        /**
         * 单张图片进入视觉模型前的原始字节上限。超过上限仍可下载和识别元信息，
         * 但不会把图片编码成 data URI 注入模型请求，避免堆内存随上传上限放大。
         */
        private long maxBytes = 20L * 1024L * 1024L;

        public long getMaxBytes() { return maxBytes; }
        public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
    }
}
