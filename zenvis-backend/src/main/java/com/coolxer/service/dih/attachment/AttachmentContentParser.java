package com.coolxer.service.dih.attachment;

import com.coolxer.configuration.ai.AttachmentProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.Styles;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * PDF、现代 Office、表格和文本的流式抽取器。
 */
@Slf4j
@Service
public class AttachmentContentParser {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "jsonl", "sql", "log",
            "xml", "yaml", "yml", "ini", "conf", "properties", "java", "js", "ts",
            "tsx", "jsx", "vue", "py", "go", "rs", "c", "cpp", "h", "hpp", "sh",
            "bat", "ps1", "html", "css", "scss", "less"
    );

    private static final Set<String> OFFICE_LEGACY = Set.of("doc", "xls", "ppt");

    private static final Set<String> TABLE_FORMATS = Set.of("csv", "tsv", "xlsx");

    private static final int OUTLINE_LIMIT = 200;

    private final AttachmentProperties properties;

    public AttachmentContentParser(AttachmentProperties properties) {
        this.properties = properties;
    }

    public boolean isSupported(String fileName, String contentType) {
        String format = detectFormat(fileName, contentType);
        return "text".equals(format) || "pdf".equals(format) || "docx".equals(format)
                || "xlsx".equals(format) || "pptx".equals(format)
                || "csv".equals(format) || "tsv".equals(format);
    }

    public String detectFormat(String fileName, String contentType) {
        String extension = extensionOf(fileName);
        if (TEXT_EXTENSIONS.contains(extension)) {
            return "text";
        }
        if ("pdf".equals(extension)) {
            return "pdf";
        }
        if ("docx".equals(extension)) {
            return "docx";
        }
        if ("xlsx".equals(extension)) {
            return "xlsx";
        }
        if ("pptx".equals(extension)) {
            return "pptx";
        }
        if ("csv".equals(extension)) {
            return "csv";
        }
        if ("tsv".equals(extension)) {
            return "tsv";
        }
        if (OFFICE_LEGACY.contains(extension)) {
            return extension;
        }
        String mime = normalizedContentType(contentType);
        if (mime.startsWith("text/")) {
            return "text";
        }
        if (mime.contains("pdf")) {
            return "pdf";
        }
        if (mime.contains("wordprocessingml")) {
            return "docx";
        }
        if (mime.contains("spreadsheetml")) {
            return "xlsx";
        }
        if (mime.contains("presentationml")) {
            return "pptx";
        }
        if (mime.contains("csv")) {
            return "csv";
        }
        return extension.isBlank() ? "unsupported" : extension;
    }

    public AttachmentParsedContent parse(String fileId,
                                         String fileName,
                                         String contentType,
                                         Path path,
                                         AttachmentProgress progress) {
        String format = detectFormat(fileName, contentType);
        if (!isSupported(fileName, contentType)) {
            String warning = OFFICE_LEGACY.contains(format)
                    ? "暂不支持旧版 Office 格式，请另存为 docx、xlsx 或 pptx 后重新上传"
                    : "暂不支持该附件的正文解析，仅提供文件名、大小和类型";
            return AttachmentParsedContent.builder()
                    .format(format)
                    .status(AttachmentStatus.UNSUPPORTED)
                    .warning(warning)
                    .truncated(false)
                    .textChars(0L)
                    .chunkCount(0)
                    .build();
        }

        Path chunksPath;
        try {
            chunksPath = Files.createTempFile("zenvis-attachment-chunks-", ".jsonl");
        } catch (IOException e) {
            throw new AttachmentParseException("创建附件片段临时文件失败", e);
        }
        AttachmentChunkWriter writer = null;
        try {
            writer = new AttachmentChunkWriter(chunksPath, properties.getParse().getMaxChunks());
            AttachmentParsedContent parsed;
            switch (format) {
                case "pdf" -> parsed = parsePdf(fileId, fileName, path, writer, progress);
                case "docx" -> parsed = parseDocx(fileId, fileName, path, writer, progress);
                case "xlsx" -> parsed = parseXlsx(fileId, fileName, path, writer, progress);
                case "pptx" -> parsed = parsePptx(fileId, fileName, path, writer, progress);
                case "csv", "tsv" -> parsed = parseDelimited(fileId, fileName, path, writer,
                        "tsv".equals(format) ? '\t' : ',', format, progress);
                default -> parsed = parseText(fileId, fileName, path, writer, progress);
            }
            parsed.setFormat(format);
            parsed.setChunkCount(writer.count());
            parsed.setTruncated(Boolean.TRUE.equals(parsed.getTruncated()) || writer.truncated());
            if (writer.truncated()) {
                parsed.setStatus(AttachmentStatus.PARTIAL);
                parsed.setWarning(appendWarning(parsed.getWarning(),
                        "附件片段超过上限 " + properties.getParse().getMaxChunks() + "，仅保留前面已解析内容"));
            }
            if (!AttachmentStatus.UNSUPPORTED.equals(parsed.getStatus())
                    && parsed.getChunkCount() == 0) {
                parsed.setStatus(AttachmentStatus.PARTIAL);
                parsed.setWarning(appendWarning(parsed.getWarning(),
                        "未抽取到可用正文，可能为扫描件、空文件或仅包含图片"));
            }
            writer.close();
            writer = null;
            parsed.setChunksPath(chunksPath);
            return parsed;
        } catch (AttachmentParseException e) {
            deleteQuietly(chunksPath);
            throw e;
        } catch (Exception e) {
            deleteQuietly(chunksPath);
            throw new AttachmentParseException("解析 " + fileName + " 失败: " + safeMessage(e), e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private AttachmentParsedContent parseText(String fileId,
                                              String fileName,
                                              Path path,
                                              AttachmentChunkWriter writer,
                                              AttachmentProgress progress) throws IOException {
        progress.report(20, "正在读取文本内容");
        Charset charset = detectCharset(path);
        List<String> outline = new ArrayList<>();
        long textChars = 0;
        boolean truncated = false;
        int lineNumber = 0;
        int chunkStartLine = 1;
        StringBuilder buffer = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(path), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
                textChars += line.length() + 1;
                String normalized = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
                if (outline.size() < OUTLINE_LIMIT && isHeading(normalized)) {
                    outline.add("line:" + lineNumber + " " + truncate(normalized.trim(), 120));
                }
                int offset = 0;
                while (offset < normalized.length()) {
                    if (buffer.length() == 0) {
                        chunkStartLine = lineNumber;
                    }
                    int take = Math.min(properties.getParse().getChunkChars() - buffer.length(),
                            normalized.length() - offset);
                    buffer.append(normalized, offset, offset + take);
                    offset += take;
                    if (buffer.length() >= properties.getParse().getChunkChars()) {
                        String overlap = overlapTail(buffer, overlapChars());
                        if (!flushTextChunk(fileId, fileName, writer, buffer, chunkStartLine, lineNumber,
                                "text", "lines:" + chunkStartLine + "-" + lineNumber)) {
                            truncated = true;
                            break;
                        }
                        buffer = new StringBuilder(overlap);
                    }
                }
                if (truncated) {
                    break;
                }
                if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) != '\n') {
                    buffer.append('\n');
                }
            }
        }
        if (!truncated && buffer.length() > 0) {
            flushTextChunk(fileId, fileName, writer, buffer, chunkStartLine, Math.max(lineNumber, chunkStartLine),
                    "text", "lines:" + chunkStartLine + "-" + Math.max(lineNumber, chunkStartLine));
        }
        progress.report(90, "文本读取完成");
        return AttachmentParsedContent.builder()
                .status(AttachmentStatus.READY)
                .textChars(textChars)
                .truncated(truncated || writer.truncated())
                .outline(outline)
                .build();
    }

    private AttachmentParsedContent parsePdf(String fileId,
                                             String fileName,
                                             Path path,
                                             AttachmentChunkWriter writer,
                                             AttachmentProgress progress) throws IOException {
        List<String> outline = new ArrayList<>();
        long textChars = 0;
        int parsedPages = 0;
        boolean truncated = false;
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.isEncrypted()) {
                throw new AttachmentParseException("PDF 已加密，无法解析正文；请解除密码后重新上传");
            }
            int totalPages = document.getNumberOfPages();
            int limit = Math.min(totalPages, properties.getParse().getMaxPages());
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= limit; page++) {
                int currentPage = page;
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                parsedPages = page;
                if (text != null && !text.isBlank()) {
                    textChars += text.length();
                    if (outline.size() < OUTLINE_LIMIT) {
                        outline.add("page:" + page + " " + truncate(firstLine(text), 120));
                    }
                    if (!writeLongText(fileId, fileName, writer, text, "page:" + page,
                            chunk -> chunk.setPage(currentPage), chunk -> chunk.setKind("page"))) {
                        truncated = true;
                        break;
                    }
                }
                if (page % 10 == 0) {
                    progress.report(20 + Math.min(65, page * 60 / Math.max(limit, 1)),
                            "已解析 PDF 第 " + page + "/" + limit + " 页");
                }
            }
            if (totalPages > limit) {
                truncated = true;
            }
            progress.report(90, "PDF 解析完成");
            return AttachmentParsedContent.builder()
                    .status(truncated || textChars == 0 ? AttachmentStatus.PARTIAL : AttachmentStatus.READY)
                    .pageCount(parsedPages)
                    .textChars(textChars)
                    .truncated(truncated)
                    .warning(textChars == 0
                            ? "PDF 未包含可抽取文本层，可能为扫描件；当前不执行 OCR"
                            : (totalPages > limit
                            ? "PDF 超过 " + properties.getParse().getMaxPages() + " 页，仅解析前 " + limit + " 页"
                            : null))
                    .outline(outline)
                    .build();
        } catch (InvalidPasswordException e) {
            throw new AttachmentParseException("PDF 已加密或需要密码，无法解析正文", e);
        }
    }

    private AttachmentParsedContent parseDocx(String fileId,
                                              String fileName,
                                              Path path,
                                              AttachmentChunkWriter writer,
                                              AttachmentProgress progress) throws IOException {
        List<String> outline = new ArrayList<>();
        List<AttachmentManifest.TableInfo> tables = new ArrayList<>();
        long textChars = 0;
        boolean truncated = false;
        int[] headingCounters = new int[10];
        int headingDepth = 0;
        int paragraphIndex = 0;
        int tableIndex = 0;
        String sectionTitle = "正文";
        String sectionLocator = "section:0";
        StringBuilder buffer = new StringBuilder();

        try (InputStream input = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(input)) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    paragraphIndex++;
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    String style = paragraph.getStyle();
                    int headingLevel = wordHeadingLevel(style);
                    if (headingLevel > 0) {
                        flushBuffer(fileId, fileName, writer, buffer,
                                sectionLocator, sectionLocator + " " + sectionTitle,
                                "text", null, null, null);
                        buffer.setLength(0);
                        headingDepth = Math.min(headingLevel, 9);
                        for (int level = 1; level < headingDepth; level++) {
                            if (headingCounters[level] == 0) {
                                headingCounters[level] = 1;
                            }
                        }
                        headingCounters[headingDepth]++;
                        for (int level = headingDepth + 1; level < headingCounters.length; level++) {
                            headingCounters[level] = 0;
                        }
                        sectionLocator = "section:" + sectionNumber(headingCounters, headingDepth);
                        sectionTitle = text.trim();
                        if (outline.size() < OUTLINE_LIMIT) {
                            outline.add(sectionLocator + " " + truncate(sectionTitle, 120));
                        }
                    }
                    textChars += text.length() + 1;
                    buffer.append(text).append('\n');
                    if (buffer.length() >= properties.getParse().getChunkChars()) {
                        if (!flushBuffer(fileId, fileName, writer, buffer,
                                sectionLocator, sectionLocator + " " + sectionTitle,
                                "text", null, null, null)) {
                            truncated = true;
                            break;
                        }
                    }
                } else if (element instanceof XWPFTable table) {
                    tableIndex++;
                    if (!flushBuffer(fileId, fileName, writer, buffer,
                            sectionLocator, sectionLocator + " " + sectionTitle,
                            "text", null, null, null)) {
                        truncated = true;
                        break;
                    }
                    List<List<String>> rows = readDocxTable(table);
                    if (rows.isEmpty()) {
                        continue;
                    }
                    List<String> columns = rows.get(0);
                    int tableStart = 1;
                    for (int from = 1; from < rows.size(); from += properties.getParse().getTableRowsPerChunk()) {
                        int to = Math.min(rows.size(), from + properties.getParse().getTableRowsPerChunk());
                        String text = tableText(columns, rows.subList(from, to));
                        textChars += text.length();
                        AttachmentChunk chunk = baseChunk(fileId, fileName,
                                "table:" + tableIndex, "table:" + tableIndex, "table");
                        chunk.setText(text);
                        chunk.setColumns(columns);
                        chunk.setStartRow(from);
                        chunk.setEndRow(to);
                        if (!writer.add(chunk)) {
                            truncated = true;
                            break;
                        }
                    }
                    tables.add(AttachmentManifest.TableInfo.builder()
                            .sheet(sectionLocator)
                            .name("table-" + tableIndex)
                            .columns(columns)
                            .fieldTypes(inferTypes(columns, rows.stream().skip(1).limit(100).toList()))
                            .sampleRows(rows.stream().skip(1).limit(5).toList())
                            .startRow(tableStart)
                            .endRow(rows.size())
                            .rowCount((long) Math.max(rows.size() - 1, 0))
                            .build());
                    if (truncated) {
                        break;
                    }
                }
            }
            if (!truncated) {
                flushBuffer(fileId, fileName, writer, buffer,
                        sectionLocator, sectionLocator + " " + sectionTitle,
                        "text", null, null, null);
            }
        }
        progress.report(90, "Word 文档解析完成");
        return AttachmentParsedContent.builder()
                .status(truncated ? AttachmentStatus.PARTIAL : AttachmentStatus.READY)
                .textChars(textChars)
                .truncated(truncated)
                .warning(truncated ? "Word 文档内容超过片段上限，仅保留已解析部分" : null)
                .outline(outline)
                .tables(tables)
                .build();
    }

    private AttachmentParsedContent parseXlsx(String fileId,
                                              String fileName,
                                              Path path,
                                              AttachmentChunkWriter writer,
                                              AttachmentProgress progress) throws Exception {
        List<String> outline = new ArrayList<>();
        List<AttachmentManifest.TableInfo> tables = new ArrayList<>();
        boolean[] truncated = {false};
        long[] rowCount = {0};
        int[] sheetCount = {0};
        int[] textChars = {0};

        try (OPCPackage pkg = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sharedStrings;
            Styles styles;
            try {
                sharedStrings = reader.getSharedStringsTable();
            } catch (Exception e) {
                sharedStrings = null;
            }
            try {
                styles = reader.getStylesTable();
            } catch (Exception e) {
                styles = null;
            }
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetIterator();
            while (sheets.hasNext()) {
                InputStream sheetStream = sheets.next();
                String sheetName = sheets.getSheetName();
                sheetCount[0]++;
                if (sheetCount[0] > properties.getParse().getMaxSheets()) {
                    truncated[0] = true;
                    sheetStream.close();
                    break;
                }
                SheetTableBuilder builder = new SheetTableBuilder(fileId, fileName, sheetName, writer,
                        tables, truncated, rowCount, textChars);
                try {
                    XMLReader parser = SAXHelper.newXMLReader();
                    DataFormatter formatter = new DataFormatter(Locale.ROOT);
                    XSSFSheetXMLHandler handler = new XSSFSheetXMLHandler(
                            styles, null, sharedStrings, builder, formatter, false);
                    parser.setContentHandler(handler);
                    parser.parse(new InputSource(sheetStream));
                    builder.flush();
                } catch (LimitReachedException e) {
                    builder.flush();
                    truncated[0] = true;
                    break;
                } finally {
                    sheetStream.close();
                }
                if (sheetName != null) {
                    outline.add("sheet:" + sheetName + " " + builder.dataRows + " 行");
                }
                AttachmentManifest.TableInfo tableInfo = builder.tableInfo();
                if (tableInfo != null) {
                    tables.add(tableInfo);
                }
                progress.report(20 + Math.min(65, sheetCount[0] * 10), "已解析工作表 " + sheetName);
                if (truncated[0]) {
                    break;
                }
            }
        }
        progress.report(90, "Excel 解析完成");
        return AttachmentParsedContent.builder()
                .status(truncated[0] ? AttachmentStatus.PARTIAL : AttachmentStatus.READY)
                .sheetCount(sheetCount[0])
                .rowCount(rowCount[0])
                .textChars((long) textChars[0])
                .truncated(truncated[0])
                .warning(truncated[0] ? "Excel 行数或工作表数超过限制，仅保留已解析部分" : null)
                .outline(outline)
                .tables(tables)
                .build();
    }

    private AttachmentParsedContent parsePptx(String fileId,
                                              String fileName,
                                              Path path,
                                              AttachmentChunkWriter writer,
                                              AttachmentProgress progress) throws IOException {
        List<String> outline = new ArrayList<>();
        long textChars = 0;
        boolean truncated = false;
        int parsedSlides = 0;
        try (InputStream input = Files.newInputStream(path);
             XMLSlideShow slideShow = new XMLSlideShow(input)) {
            List<XSLFSlide> slides = slideShow.getSlides();
            int limit = Math.min(slides.size(), properties.getParse().getMaxSlides());
            for (int index = 0; index < limit; index++) {
                XSLFSlide slide = slides.get(index);
                parsedSlides = index + 1;
                String title = String.valueOf(slide.getTitle() == null ? "" : slide.getTitle()).trim();
                StringBuilder text = new StringBuilder();
                if (!title.isBlank()) {
                    if (outline.size() < OUTLINE_LIMIT) {
                        outline.add("slide:" + (index + 1) + " " + truncate(title, 120));
                    }
                }
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String value = textShape.getText();
                        if (value != null && !value.isBlank()) {
                            text.append(value).append('\n');
                        }
                    } else if (shape instanceof XSLFTable table) {
                        for (XSLFTableRow row : table.getRows()) {
                            List<String> cells = new ArrayList<>();
                            for (XSLFTableCell cell : row.getCells()) {
                                cells.add(cell.getText() == null ? "" : cell.getText().trim());
                            }
                            text.append(String.join("\t", cells)).append('\n');
                        }
                    }
                }
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    for (List<XSLFTextParagraph> paragraphs : notes.getTextParagraphs()) {
                        for (XSLFTextParagraph paragraph : paragraphs) {
                            String value = paragraph.getText();
                            if (value != null && !value.isBlank()) {
                                text.append("[备注] ").append(value).append('\n');
                            }
                        }
                    }
                }
                textChars += text.length();
                int slideNumber = index + 1;
                if (!writeLongText(fileId, fileName, writer, text.toString(), "slide:" + slideNumber,
                        chunk -> chunk.setSlide(slideNumber), chunk -> chunk.setKind("slide"))) {
                    truncated = true;
                    break;
                }
                progress.report(20 + Math.min(65, slideNumber * 60 / Math.max(limit, 1)),
                        "已解析幻灯片 " + slideNumber + "/" + limit);
            }
            if (slides.size() > limit) {
                truncated = true;
            }
        }
        progress.report(90, "PowerPoint 解析完成");
        return AttachmentParsedContent.builder()
                .status(truncated ? AttachmentStatus.PARTIAL : AttachmentStatus.READY)
                .slideCount(parsedSlides)
                .textChars(textChars)
                .truncated(truncated)
                .warning(truncated ? "PowerPoint 超过幻灯片上限，仅解析前 "
                        + properties.getParse().getMaxSlides() + " 张" : null)
                .outline(outline)
                .build();
    }

    private AttachmentParsedContent parseDelimited(String fileId,
                                                   String fileName,
                                                   Path path,
                                                   AttachmentChunkWriter writer,
                                                   char delimiter,
                                                   String format,
                                                   AttachmentProgress progress) throws IOException {
        Charset charset = detectCharset(path);
        List<String> outline = new ArrayList<>();
        List<AttachmentManifest.TableInfo> tables = new ArrayList<>();
        boolean truncated = false;
        long rowCount = 0;
        long textChars = 0;
        String[][] header = {null};
        List<String[]> rows = new ArrayList<>();
        int[] startRow = {1};
        int[] lastRow = {0};
        final boolean[] limitReached = {false};
        List<List<String>> sampleRows = new ArrayList<>();
        List<List<String>> typeSamples = new ArrayList<>();

        try (ReaderRows readerRows = new ReaderRows(path, charset, delimiter)) {
            List<String> row;
            while (!limitReached[0] && (row = readerRows.next()) != null) {
                int rowNumber = readerRows.rowNumber();
                lastRow[0] = rowNumber;
                rowCount++;
                if (header[0] == null) {
                    header[0] = row.toArray(String[]::new);
                    outline.add("sheet:" + format.toUpperCase(Locale.ROOT) + " columns: "
                            + truncate(String.join(", ", header[0]), 200));
                    startRow[0] = rowNumber + 1;
                    continue;
                }
                String[] padded = pad(row, header[0].length);
                rows.add(padded);
                if (sampleRows.size() < 5) {
                    sampleRows.add(List.of(padded));
                }
                if (typeSamples.size() < 100) {
                    typeSamples.add(List.of(padded));
                }
                textChars += row.stream().mapToLong(value -> value.length() + 1).sum();
                if (rows.size() >= properties.getParse().getTableRowsPerChunk()) {
                    if (!flushTableChunk(fileId, fileName, writer, format.toUpperCase(Locale.ROOT),
                            header[0], rows, startRow[0], rowNumber)) {
                        truncated = true;
                        break;
                    }
                    rows.clear();
                    startRow[0] = rowNumber + 1;
                }
                if (rowCount >= properties.getParse().getMaxTableRows()) {
                    limitReached[0] = true;
                    truncated = true;
                }
            }
        }
        if (!truncated && header[0] != null && !rows.isEmpty()) {
            flushTableChunk(fileId, fileName, writer, format.toUpperCase(Locale.ROOT),
                    header[0], rows, startRow[0], lastRow[0]);
        }
        if (header[0] != null) {
            tables.add(AttachmentManifest.TableInfo.builder()
                    .sheet(format.toUpperCase(Locale.ROOT))
                    .name(fileName)
                    .columns(List.of(header[0]))
                    .fieldTypes(inferTypes(List.of(header[0]), typeSamples))
                    .sampleRows(sampleRows)
                    .startRow(2)
                    .endRow(lastRow[0])
                    .rowCount(Math.max(rowCount - 1, 0))
                    .build());
        }
        progress.report(90, "表格解析完成");
        return AttachmentParsedContent.builder()
                .status(truncated ? AttachmentStatus.PARTIAL : AttachmentStatus.READY)
                .sheetCount(header[0] == null ? 0 : 1)
                .rowCount(Math.max(rowCount - 1, 0))
                .textChars(textChars)
                .truncated(truncated)
                .warning(truncated ? "表格行数超过限制，仅保留已解析部分" : null)
                .outline(outline)
                .tables(tables)
                .build();
    }

    private boolean flushTableChunk(String fileId,
                                    String fileName,
                                    AttachmentChunkWriter writer,
                                    String sheet,
                                    String[] header,
                                    List<String[]> rows,
                                    int startRow,
                                    int endRow) {
        if (rows.isEmpty()) {
            return true;
        }
        String text = tableText(List.of(header), rows);
        AttachmentChunk chunk = baseChunk(fileId, fileName,
                "sheet:" + sheet + "!rows:" + startRow + "-" + endRow,
                "sheet:" + sheet + " rows " + startRow + "-" + endRow, "table");
        chunk.setText(text);
        chunk.setSheet(sheet);
        chunk.setStartRow(startRow);
        chunk.setEndRow(endRow);
        chunk.setColumns(List.of(header));
        return writer.add(chunk);
    }

    private boolean flushTextChunk(String fileId,
                                   String fileName,
                                   AttachmentChunkWriter writer,
                                   StringBuilder buffer,
                                   int startLine,
                                   int endLine,
                                   String kind,
                                   String locator) {
        String text = buffer.toString().trim();
        if (text.isEmpty()) {
            buffer.setLength(0);
            return true;
        }
        AttachmentChunk chunk = baseChunk(fileId, fileName, locator,
                "lines " + startLine + "-" + endLine, kind);
        chunk.setText(text);
        chunk.setStartLine(startLine);
        chunk.setEndLine(endLine);
        boolean added = writer.add(chunk);
        buffer.setLength(0);
        return added;
    }

    private boolean flushBuffer(String fileId,
                                String fileName,
                                AttachmentChunkWriter writer,
                                StringBuilder buffer,
                                String locator,
                                String title,
                                String kind,
                                Consumer<AttachmentChunk> customizer,
                                Consumer<AttachmentChunk> kindCustomizer,
                                Consumer<AttachmentChunk> ignored) {
        String text = buffer.toString().trim();
        if (text.isEmpty()) {
            buffer.setLength(0);
            return true;
        }
        int size = Math.max(properties.getParse().getChunkChars(), 1);
        boolean added = true;
        for (int from = 0; from < text.length(); from += size) {
            int to = Math.min(text.length(), from + size);
            AttachmentChunk chunk = baseChunk(fileId, fileName, locator, title, kind);
            chunk.setText(text.substring(from, to));
            if (customizer != null) {
                customizer.accept(chunk);
            }
            if (kindCustomizer != null) {
                kindCustomizer.accept(chunk);
            }
            if (!writer.add(chunk)) {
                added = false;
                break;
            }
        }
        buffer.setLength(0);
        return added;
    }

    private boolean writeLongText(String fileId,
                                  String fileName,
                                  AttachmentChunkWriter writer,
                                  String text,
                                  String locator,
                                  Consumer<AttachmentChunk> customizer,
                                  Consumer<AttachmentChunk> kindCustomizer) {
        if (text == null || text.isBlank()) {
            return true;
        }
        int size = Math.max(properties.getParse().getChunkChars(), 1);
        for (int from = 0; from < text.length(); from += size) {
            int to = Math.min(text.length(), from + size);
            AttachmentChunk chunk = baseChunk(fileId, fileName, locator, locator, "text");
            chunk.setText(text.substring(from, to).trim());
            customizer.accept(chunk);
            kindCustomizer.accept(chunk);
            if (!writer.add(chunk)) {
                return false;
            }
        }
        return true;
    }

    private AttachmentChunk baseChunk(String fileId, String fileName, String locator,
                                      String title, String kind) {
        return AttachmentChunk.builder()
                .id(fileId + "-" + UUID.randomUUID())
                .fileId(fileId)
                .fileName(fileName)
                .locator(locator)
                .title(title)
                .kind(kind)
                .build();
    }

    private String overlapTail(StringBuilder buffer, int overlap) {
        if (overlap <= 0 || buffer.length() <= overlap) {
            return "";
        }
        return buffer.substring(buffer.length() - overlap);
    }

    private int overlapChars() {
        int chunkChars = Math.max(properties.getParse().getChunkChars(), 1);
        int configured = Math.max(properties.getParse().getChunkOverlapChars(), 0);
        return Math.min(configured, chunkChars / 2);
    }

    private List<List<String>> readDocxTable(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> values = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                values.add(cell.getText() == null ? "" : cell.getText().trim());
            }
            rows.add(values);
        }
        return rows;
    }

    private String tableText(List<String> header, List<?> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.join("\t", header)).append('\n');
        for (Object raw : rows) {
            List<?> row = raw instanceof List<?> list ? list : List.of(raw);
            builder.append(row.stream()
                    .map(value -> String.valueOf(value).replace('\t', ' ').replace('\n', ' '))
                    .toList().stream().reduce((left, right) -> left + "\t" + right).orElse(""))
                    .append('\n');
        }
        return builder.toString();
    }

    private String[] pad(List<String> row, int columns) {
        String[] padded = new String[Math.max(columns, row.size())];
        for (int index = 0; index < padded.length; index++) {
            padded[index] = index < row.size() ? row.get(index) : "";
        }
        return padded;
    }

    private Charset detectCharset(Path path) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) {
            header = input.readNBytes(64 * 1024);
        }
        if (header.length >= 3 && (header[0] & 0xFF) == 0xEF
                && (header[1] & 0xFF) == 0xBB && (header[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (header.length >= 2 && (header[0] & 0xFF) == 0xFE && (header[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(header));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return Charset.forName("GB18030");
        }
    }

    private int wordHeadingLevel(String style) {
        if (style == null) {
            return 0;
        }
        String normalized = style.toLowerCase(Locale.ROOT);
        if (normalized.equals("title")) {
            return 1;
        }
        if (!normalized.contains("heading") && !normalized.contains("标题")) {
            return 0;
        }
        for (int index = normalized.length() - 1; index >= 0; index--) {
            if (Character.isDigit(normalized.charAt(index))) {
                return Math.max(1, Math.min(normalized.charAt(index) - '0', 9));
            }
        }
        return 1;
    }

    private String sectionNumber(int[] counters, int depth) {
        StringBuilder value = new StringBuilder();
        for (int level = 1; level <= depth; level++) {
            if (value.length() > 0) {
                value.append('.');
            }
            value.append(Math.max(counters[level], 1));
        }
        return value.length() == 0 ? "1" : value.toString();
    }

    private List<String> inferTypes(List<String> columns, List<List<String>> rows) {
        List<String> types = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            boolean any = false;
            boolean integer = true;
            boolean numeric = true;
            for (List<String> row : rows) {
                String value = index < row.size() ? row.get(index) : "";
                if (value == null || value.isBlank()) {
                    continue;
                }
                any = true;
                if (number(value) == null) {
                    numeric = false;
                    integer = false;
                    break;
                }
                if (value.contains(".") || value.contains("e") || value.contains("E")) {
                    integer = false;
                }
            }
            types.add(!any ? "unknown" : numeric ? (integer ? "integer" : "number") : "string");
        }
        return types;
    }

    private java.math.BigDecimal number(String value) {
        try {
            return new java.math.BigDecimal(value.trim().replace(",", ""));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isHeading(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("#") || trimmed.matches("^\\d+([.、)]|\\s+).{0,100}$");
    }

    private String firstLine(String text) {
        String normalized = text.replace("\r", "").trim();
        int newline = normalized.indexOf('\n');
        return newline < 0 ? normalized : normalized.substring(0, newline);
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1
                ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizedContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon))
                .trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max) + "...";
    }

    private String appendWarning(String current, String next) {
        if (current == null || current.isBlank()) {
            return next;
        }
        return next == null || next.isBlank() ? current : current + "；" + next;
    }

    private String safeMessage(Throwable error) {
        Throwable current = error;
        String message = error == null ? null : error.getMessage();
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        return message == null || message.isBlank()
                ? (current == null ? "未知错误" : current.getClass().getSimpleName()) : message;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private final class SheetTableBuilder implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final String fileId;
        private final String fileName;
        private final String sheetName;
        private final AttachmentChunkWriter writer;
        private final List<AttachmentManifest.TableInfo> tables;
        private final boolean[] truncated;
        private final long[] totalRows;
        private final int[] textChars;
        private String[] header;
        private final List<String[]> rows = new ArrayList<>();
        private List<String> currentRow = new ArrayList<>();
        private int startRow = 1;
        private int firstDataRow = 1;
        private int lastRow;
        private int dataRows;
        private final List<List<String>> sampleRows = new ArrayList<>();
        private final List<List<String>> typeSamples = new ArrayList<>();

        private SheetTableBuilder(String fileId,
                                  String fileName,
                                  String sheetName,
                                  AttachmentChunkWriter writer,
                                  List<AttachmentManifest.TableInfo> tables,
                                  boolean[] truncated,
                                  long[] totalRows,
                                  int[] textChars) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.sheetName = sheetName == null ? "Sheet" : sheetName;
            this.writer = writer;
            this.tables = tables;
            this.truncated = truncated;
            this.totalRows = totalRows;
            this.textChars = textChars;
        }

        @Override
        public void startRow(int rowNum) {
            currentRow = new ArrayList<>();
            lastRow = rowNum + 1;
        }

        @Override
        public void endRow(int rowNum) {
            if (currentRow.stream().allMatch(String::isBlank)) {
                return;
            }
            totalRows[0]++;
            if (header == null) {
                header = currentRow.toArray(String[]::new);
                startRow = rowNum + 2;
                firstDataRow = startRow;
                return;
            }
            String[] padded = pad(currentRow, header.length);
            rows.add(padded);
            if (sampleRows.size() < 5) {
                sampleRows.add(List.of(padded));
            }
            if (typeSamples.size() < 100) {
                typeSamples.add(List.of(padded));
            }
            dataRows++;
            textChars[0] += currentRow.stream().mapToInt(value -> value.length() + 1).sum();
            if (rows.size() >= properties.getParse().getTableRowsPerChunk()) {
                flush();
                startRow = rowNum + 2;
            }
            if (totalRows[0] >= properties.getParse().getMaxTableRows()) {
                throw new LimitReachedException();
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (formattedValue == null) {
                return;
            }
            int column = columnIndex(cellReference);
            while (currentRow.size() <= column) {
                currentRow.add("");
            }
            currentRow.set(column, formattedValue);
        }

        private void flush() {
            if (header == null || rows.isEmpty()) {
                return;
            }
            int endRow = startRow + rows.size() - 1;
            String text = tableText(List.of(header), rows);
            AttachmentChunk chunk = baseChunk(fileId, fileName,
                    "sheet:" + sheetName + "!rows:" + startRow + "-" + endRow,
                    "sheet:" + sheetName + " rows " + startRow + "-" + endRow, "table");
            chunk.setText(text);
            chunk.setSheet(sheetName);
            chunk.setStartRow(startRow);
            chunk.setEndRow(endRow);
            chunk.setColumns(List.of(header));
            if (!writer.add(chunk)) {
                truncated[0] = true;
            }
            rows.clear();
        }

        private AttachmentManifest.TableInfo tableInfo() {
            if (header == null) {
                return null;
            }
            return AttachmentManifest.TableInfo.builder()
                    .sheet(sheetName)
                    .name(sheetName)
                    .columns(List.of(header))
                    .fieldTypes(inferTypes(List.of(header), typeSamples))
                    .sampleRows(sampleRows)
                    .startRow(firstDataRow)
                    .endRow(lastRow)
                    .rowCount((long) dataRows)
                    .build();
        }

        private int columnIndex(String reference) {
            if (reference == null || reference.isBlank()) {
                return 0;
            }
            int value = 0;
            for (int index = 0; index < reference.length(); index++) {
                char ch = reference.charAt(index);
                if (!Character.isLetter(ch)) {
                    break;
                }
                value = value * 26 + (Character.toUpperCase(ch) - 'A' + 1);
            }
            return Math.max(value - 1, 0);
        }
    }

    private static final class LimitReachedException extends RuntimeException {
    }

    private static final class ReaderRows implements AutoCloseable {
        private final BufferedReader reader;
        private final char delimiter;
        private int rowNumber;

        private ReaderRows(Path path, Charset charset, char delimiter) throws IOException {
            this.reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), charset));
            this.delimiter = delimiter;
        }

        private List<String> next() throws IOException {
            List<String> row = new ArrayList<>();
            StringBuilder value = new StringBuilder();
            boolean quoted = false;
            int current;
            boolean any = false;
            while ((current = reader.read()) != -1) {
                any = true;
                char ch = (char) current;
                if (quoted) {
                    if (ch == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') {
                            value.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) {
                                reader.reset();
                            }
                        }
                    } else {
                        value.append(ch);
                    }
                    continue;
                }
                if (ch == '"') {
                    quoted = true;
                } else if (ch == delimiter) {
                    row.add(value.toString());
                    value.setLength(0);
                } else if (ch == '\n' || ch == '\r') {
                    if (ch == '\r') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.reset();
                        }
                    }
                    row.add(value.toString());
                    rowNumber++;
                    return row;
                } else {
                    value.append(ch);
                }
            }
            if (!any) {
                return null;
            }
            row.add(value.toString());
            rowNumber++;
            return row;
        }

        private int rowNumber() {
            return rowNumber;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
