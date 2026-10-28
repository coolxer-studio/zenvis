package com.coolxer.service.dih.attachment;

import com.coolxer.configuration.ai.AttachmentProperties;
import com.coolxer.utils.JacksonUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentContentParserTest {

    @TempDir
    Path tempDir;

    private final AttachmentContentParser parser =
            new AttachmentContentParser(new AttachmentProperties());

    @Test
    void parsesUtf8BomMarkdownWithoutLeakingBomIntoContent() throws Exception {
        byte[] content = "\uFEFF# 标题\n第一段内容".getBytes(StandardCharsets.UTF_8);

        AttachmentParsedContent parsed = parse("说明.md", "text/markdown", content);
        List<AttachmentChunk> chunks = readChunks(parsed);

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.READY);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getLocator()).isEqualTo("lines:1-2");
        assertThat(chunks.get(0).getText()).doesNotContain("\uFEFF").contains("# 标题", "第一段内容");
        assertThat(parsed.getOutline()).anyMatch(item -> item.contains("# 标题"));
    }

    @Test
    void fallsBackToGb18030ForNonUtf8Text() throws Exception {
        byte[] content = "中文日志：查询成功".getBytes(Charset.forName("GB18030"));

        AttachmentParsedContent parsed = parse("日志.txt", "text/plain", content);

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.READY);
        assertThat(readChunks(parsed).get(0).getText()).contains("中文日志：查询成功");
    }

    @Test
    void streamsCsvIntoBoundedTableChunksWithRowLocators() throws Exception {
        StringBuilder csv = new StringBuilder("region,name,amount\n");
        for (int index = 1; index <= 350; index++) {
            csv.append("East,item-").append(index).append(',').append(index).append('\n');
        }

        AttachmentParsedContent parsed = parse("sales.csv", "text/csv",
                csv.toString().getBytes(StandardCharsets.UTF_8));
        List<AttachmentChunk> chunks = readChunks(parsed);

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.READY);
        assertThat(parsed.getSheetCount()).isEqualTo(1);
        assertThat(parsed.getRowCount()).isEqualTo(350);
        assertThat(chunks).hasSize(2);
        assertThat(chunks).allMatch(chunk -> "table".equals(chunk.getKind()));
        assertThat(chunks.get(0).getLocator()).startsWith("sheet:CSV!rows:2-");
        assertThat(parsed.getTables().get(0).getColumns()).containsExactly("region", "name", "amount");
    }

    @Test
    void extractsDocxHeadingAndTable() throws Exception {
        Path file = tempDir.resolve("report.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章");
            document.createParagraph().createRun().setText("正文段落");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("name");
            table.getRow(0).getCell(1).setText("amount");
            table.getRow(1).getCell(0).setText("A");
            table.getRow(1).getCell(1).setText("10");
            try (OutputStream output = Files.newOutputStream(file)) {
                document.write(output);
            }
        }

        AttachmentParsedContent parsed = parser.parse("file-1", "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                file, (percent, message) -> { });
        List<AttachmentChunk> chunks = readChunks(parsed);

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.READY);
        assertThat(parsed.getOutline()).anyMatch(item -> item.contains("section:1 第一章"));
        assertThat(chunks).anyMatch(chunk -> "text".equals(chunk.getKind()) && chunk.getText().contains("正文段落"));
        assertThat(chunks).anyMatch(chunk -> "table".equals(chunk.getKind()) && chunk.getText().contains("amount"));
    }

    @Test
    void extractsMultipleXlsxSheets() throws Exception {
        Path file = tempDir.resolve("sales.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            fillSheet(workbook, "Summary", 3);
            fillSheet(workbook, "Detail", 4);
            try (OutputStream output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }

        AttachmentParsedContent parsed = parser.parse("file-xlsx", "sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                file, (percent, message) -> { });

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.READY);
        assertThat(parsed.getSheetCount()).isEqualTo(2);
        assertThat(parsed.getOutline()).anyMatch(item -> item.startsWith("sheet:Summary"))
                .anyMatch(item -> item.startsWith("sheet:Detail"));
    }

    @Test
    void extractsPptxSlidesWithSlideLocators() throws Exception {
        Path file = tempDir.resolve("deck.pptx");
        try (XMLSlideShow slideShow = new XMLSlideShow()) {
            for (int index = 1; index <= 2; index++) {
                XSLFSlide slide = slideShow.createSlide();
                XSLFTextBox box = slide.createTextBox();
                box.setText("幻灯片 " + index + " 内容");
            }
            try (OutputStream output = Files.newOutputStream(file)) {
                slideShow.write(output);
            }
        }

        AttachmentParsedContent parsed = parser.parse("file-pptx", "deck.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                file, (percent, message) -> { });
        List<AttachmentChunk> chunks = readChunks(parsed);

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.READY);
        assertThat(parsed.getSlideCount()).isEqualTo(2);
        assertThat(chunks).extracting(AttachmentChunk::getLocator)
                .containsExactly("slide:1", "slide:2");
    }

    @Test
    void extractsTextPdfPageByPage() throws Exception {
        Path file = tempDir.resolve("report.pdf");
        createPdf(file, "Quarterly revenue report", false);

        AttachmentParsedContent parsed = parser.parse("file-pdf", "report.pdf", "application/pdf",
                file, (percent, message) -> { });
        List<AttachmentChunk> chunks = readChunks(parsed);

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.READY);
        assertThat(parsed.getPageCount()).isEqualTo(2);
        assertThat(chunks).extracting(AttachmentChunk::getPage).contains(1, 2);
        assertThat(chunks).allMatch(chunk -> chunk.getLocator().startsWith("page:"));
    }

    @Test
    void marksPdfWithoutTextLayerAsPartialWithoutClaimingOcr() throws Exception {
        Path file = tempDir.resolve("scan.pdf");
        createPdf(file, null, true);

        AttachmentParsedContent parsed = parser.parse("file-scan", "scan.pdf", "application/pdf",
                file, (percent, message) -> { });

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.PARTIAL);
        assertThat(parsed.getWarning()).contains("扫描件", "不执行 OCR");
        assertThat(parsed.getChunkCount()).isZero();
    }

    @Test
    void rejectsCorruptedPdf() throws Exception {
        Path file = tempDir.resolve("broken.pdf");
        Files.writeString(file, "%PDF-not-a-real-document", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse("file-broken", "broken.pdf", "application/pdf",
                file, (percent, message) -> { }))
                .isInstanceOf(AttachmentParseException.class);
    }

    @Test
    void carriesConfiguredOverlapAcrossTextChunks() throws Exception {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getParse().setChunkChars(20);
        properties.getParse().setChunkOverlapChars(5);
        AttachmentContentParser overlappedParser = new AttachmentContentParser(properties);
        Path file = tempDir.resolve("overlap.txt");
        Files.writeString(file, "abcdefghijklmnopqrstuvwxyz0123456789", StandardCharsets.UTF_8);

        AttachmentParsedContent parsed = overlappedParser.parse("file-overlap", "overlap.txt",
                "text/plain", file, (percent, message) -> { });
        List<AttachmentChunk> chunks = readChunks(parsed);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0).getText()).isEqualTo("abcdefghijklmnopqrst");
        assertThat(chunks.get(1).getText()).startsWith("pqrst");
    }

    @Test
    void streamsHundredMegabyteTextWithoutLoadingWholeFileIntoHeap() throws Exception {
        Path file = tempDir.resolve("large.log");
        long targetBytes = 100L * 1024L * 1024L;
        byte[] line = ("0123456789abcdef".repeat(63) + "\n").getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = Files.newOutputStream(file)) {
            long written = 0;
            while (written < targetBytes) {
                output.write(line);
                written += line.length;
            }
        }
        assertThat(Files.size(file)).isGreaterThanOrEqualTo(targetBytes);

        AttachmentProperties properties = new AttachmentProperties();
        properties.getParse().setChunkChars(2_000_000);
        properties.getParse().setChunkOverlapChars(0);
        AttachmentContentParser largeParser = new AttachmentContentParser(properties);

        AttachmentParsedContent parsed = largeParser.parse("file-large", "large.log",
                "text/plain", file, (percent, message) -> { });
        try (Stream<String> chunks = Files.lines(parsed.getChunksPath(), StandardCharsets.UTF_8)) {
            assertThat(chunks.filter(lineText -> !lineText.isBlank()).count())
                    .isBetween(50L, 60L);
        }

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.READY);
        assertThat(parsed.getTextChars()).isGreaterThan(targetBytes - line.length * 2);
        assertThat(parsed.getTruncated()).isFalse();
        assertThat(Files.size(parsed.getChunksPath())).isGreaterThan(targetBytes / 2);
    }

    @Test
    void marksLargeCsvPartialWhenConfiguredRowLimitIsReached() throws Exception {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getParse().setMaxTableRows(10_000);
        properties.getParse().setTableRowsPerChunk(1_000);
        AttachmentContentParser limitedParser = new AttachmentContentParser(properties);
        Path file = tempDir.resolve("large.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("region,amount\n");
            for (int index = 1; index <= 20_000; index++) {
                writer.write("East,");
                writer.write(Integer.toString(index));
                writer.write('\n');
            }
        }

        AttachmentParsedContent parsed = limitedParser.parse("file-large-csv", "large.csv",
                "text/csv", file, (percent, message) -> { });

        assertThat(parsed.getStatus()).isEqualTo(AttachmentStatus.PARTIAL);
        assertThat(parsed.getRowCount()).isEqualTo(9_999L);
        assertThat(parsed.getTruncated()).isTrue();
        assertThat(parsed.getWarning()).contains("行数");
    }

    private AttachmentParsedContent parse(String fileName, String contentType, byte[] content)
            throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.write(file, content);
        return parser.parse("file-" + fileName, fileName, contentType, file,
                (percent, message) -> { });
    }

    private List<AttachmentChunk> readChunks(AttachmentParsedContent parsed) throws IOException {
        return Files.readAllLines(parsed.getChunksPath(), StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .map(line -> JacksonUtil.toObject(line, AttachmentChunk.class))
                .toList();
    }

    private void fillSheet(XSSFWorkbook workbook, String name, int rows) {
        var sheet = workbook.createSheet(name);
        var header = sheet.createRow(0);
        header.createCell(0).setCellValue("region");
        header.createCell(1).setCellValue("amount");
        for (int index = 1; index < rows; index++) {
            var row = sheet.createRow(index);
            row.createCell(0).setCellValue("East");
            row.createCell(1).setCellValue(index);
        }
    }

    private void createPdf(Path file, String text, boolean blank) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int pageNumber = 1; pageNumber <= 2; pageNumber++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (blank) {
                    continue;
                }
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(text + " page " + pageNumber);
                    stream.endText();
                }
            }
            document.save(file.toFile());
        }
    }
}
