package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.vo.ReportWorkspaceVo;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportExportService {

    private static final String DOCUMENT_FONT = "Microsoft YaHei";

    private final ReportDocumentService reportDocumentService;

    public ReportExportService(ReportDocumentService reportDocumentService) {
        this.reportDocumentService = reportDocumentService;
    }

    public ExportedReport export(Long sessionId, String targetFormat, User currentUser) {
        ReportWorkspaceVo workspace = reportDocumentService.workspace(sessionId, currentUser);
        Map<String, Object> document = workspace.getCurrentDocument();
        if (document == null || document.isEmpty()
                || !StringUtils.hasText(String.valueOf(document.getOrDefault("content", "")))) {
            throw new IllegalArgumentException("当前没有可导出的已保存报表。");
        }
        String title = String.valueOf(document.getOrDefault("title", "报表文档"));
        String sourceFormat = String.valueOf(document.getOrDefault("format", "markdown"));
        String content = String.valueOf(document.get("content"));
        String safeName = safeFileName(title);
        return switch (StringUtils.hasText(targetFormat)
                ? targetFormat.toLowerCase(Locale.ROOT) : "") {
            case "docx" -> new ExportedReport(
                    safeName + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    exportDocx(title, toSafeHtml(content, sourceFormat)));
            case "pdf" -> new ExportedReport(
                    safeName + ".pdf",
                    "application/pdf",
                    exportPdf(title, toSafeHtml(content, sourceFormat)));
            default -> throw new IllegalArgumentException("导出格式仅支持 docx 或 pdf。");
        };
    }

    private byte[] exportPdf(String title, String bodyHtml) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            firstAvailableFont().ifPresent(font ->
                    builder.useFont(font, "ZenVis CJK"));
            builder.withHtmlContent(pdfDocument(title, bodyHtml), null);
            builder.toStream(output);
            builder.useFastMode();
            builder.run();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF 导出失败。", e);
        }
    }

    private byte[] exportDocx(String title, String bodyHtml) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(document);
            configureHeaderAndFooter(document, title);
            Document html = Jsoup.parseBodyFragment(bodyHtml);
            for (Element element : html.body().children()) {
                appendBlock(document, element, 0);
            }
            document.write(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("DOCX 导出失败。", e);
        }
    }

    private void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        section.addNewPgSz().setOrient(STPageOrientation.PORTRAIT);
        section.getPgSz().setW(BigInteger.valueOf(11906));
        section.getPgSz().setH(BigInteger.valueOf(16838));
        section.addNewPgMar().setTop(BigInteger.valueOf(1134));
        section.getPgMar().setBottom(BigInteger.valueOf(1134));
        section.getPgMar().setLeft(BigInteger.valueOf(1276));
        section.getPgMar().setRight(BigInteger.valueOf(1276));
    }

    private void configureHeaderAndFooter(XWPFDocument document, String title) {
        XWPFHeader header = document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph headerParagraph = header.createParagraph();
        headerParagraph.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun headerRun = headerParagraph.createRun();
        headerRun.setText(title);
        headerRun.setFontFamily(DOCUMENT_FONT);
        headerRun.setFontSize(9);
        headerRun.setColor("7A7A7A");

        XWPFFooter footer = document.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph footerParagraph = footer.createParagraph();
        footerParagraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun prefix = footerParagraph.createRun();
        prefix.setText("第 ");
        prefix.setFontFamily(DOCUMENT_FONT);
        footerParagraph.getCTP().addNewFldSimple().setInstr("PAGE");
        XWPFRun suffix = footerParagraph.createRun();
        suffix.setText(" 页");
        suffix.setFontFamily(DOCUMENT_FONT);
    }

    private void appendBlock(XWPFDocument document, Element element, int depth) {
        String tag = element.tagName();
        if (tag.matches("h[1-6]")) {
            int level = Integer.parseInt(tag.substring(1));
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setStyle("Heading" + level);
            paragraph.setSpacingAfter(120);
            appendInline(paragraph, element);
        } else if ("table".equals(tag)) {
            appendTable(document, element);
        } else if ("ul".equals(tag) || "ol".equals(tag)) {
            appendList(document, element, depth);
        } else if ("pre".equals(tag)) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(360);
            paragraph.setSpacingAfter(120);
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("JetBrains Mono");
            run.setFontSize(9);
            run.setText(element.text());
        } else if ("blockquote".equals(tag)) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(360);
            XWPFRun run = paragraph.createRun();
            run.setItalic(true);
            run.setColor("606266");
            run.setText(element.text());
            run.setFontFamily(DOCUMENT_FONT);
        } else if ("img".equals(tag)) {
            appendImage(document.createParagraph(), element);
        } else {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingAfter(100);
            appendInline(paragraph, element);
        }
    }

    private void appendList(XWPFDocument document, Element list, int depth) {
        boolean ordered = "ol".equals(list.tagName());
        int index = 1;
        for (Element item : list.children()) {
            if (!"li".equals(item.tagName())) {
                continue;
            }
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(360 + depth * 300);
            paragraph.setIndentationHanging(240);
            XWPFRun marker = paragraph.createRun();
            marker.setText(ordered ? index++ + ". " : "• ");
            marker.setFontFamily(DOCUMENT_FONT);
            for (org.jsoup.nodes.Node child : item.childNodes()) {
                if (child instanceof Element nested
                        && ("ul".equals(nested.tagName()) || "ol".equals(nested.tagName()))) {
                    continue;
                }
                appendInlineNode(paragraph, child);
            }
            item.children().stream()
                    .filter(child -> "ul".equals(child.tagName()) || "ol".equals(child.tagName()))
                    .forEach(child -> appendList(document, child, depth + 1));
        }
    }

    private void appendTable(XWPFDocument document, Element tableElement) {
        List<Element> rows = tableElement.select("tr");
        int columnCount = rows.stream()
                .mapToInt(row -> tableCells(row).size())
                .max().orElse(1);
        XWPFTable table = document.createTable(Math.max(rows.size(), 1), columnCount);
        table.setWidth("100%");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            List<Element> cells = tableCells(rows.get(rowIndex));
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                XWPFTableCell cell = row.getCell(cellIndex);
                cell.removeParagraph(0);
                XWPFParagraph paragraph = cell.addParagraph();
                appendInline(paragraph, cells.get(cellIndex));
                if ("th".equals(cells.get(cellIndex).tagName())) {
                    paragraph.getRuns().forEach(run -> run.setBold(true));
                }
            }
        }
    }

    private List<Element> tableCells(Element row) {
        return row.children().stream()
                .filter(cell -> "th".equals(cell.tagName()) || "td".equals(cell.tagName()))
                .toList();
    }

    private void appendInline(XWPFParagraph paragraph, Element element) {
        for (org.jsoup.nodes.Node child : element.childNodes()) {
            appendInlineNode(paragraph, child);
        }
    }

    private void appendInlineNode(XWPFParagraph paragraph, org.jsoup.nodes.Node node) {
        if (node instanceof TextNode textNode) {
            XWPFRun run = paragraph.createRun();
            run.setText(textNode.text());
            run.setFontFamily(DOCUMENT_FONT);
            run.setFontSize(10);
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }
        if ("br".equals(element.tagName())) {
            paragraph.createRun().addBreak();
            return;
        }
        if ("img".equals(element.tagName())) {
            appendImage(paragraph, element);
            return;
        }
        if ("a".equals(element.tagName()) && StringUtils.hasText(element.attr("href"))) {
            XWPFHyperlinkRun run = paragraph.createHyperlinkRun(element.attr("href"));
            run.setText(element.text());
            run.setColor("0563C1");
            run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
            run.setFontFamily(DOCUMENT_FONT);
            return;
        }
        int before = paragraph.getRuns().size();
        for (org.jsoup.nodes.Node child : element.childNodes()) {
            appendInlineNode(paragraph, child);
        }
        List<XWPFRun> runs = paragraph.getRuns();
        for (int index = before; index < runs.size(); index++) {
            XWPFRun run = runs.get(index);
            if ("strong".equals(element.tagName()) || "b".equals(element.tagName())) {
                run.setBold(true);
            }
            if ("em".equals(element.tagName()) || "i".equals(element.tagName())) {
                run.setItalic(true);
            }
            if ("code".equals(element.tagName())) {
                run.setFontFamily("JetBrains Mono");
            }
        }
    }

    private void appendImage(XWPFParagraph paragraph, Element image) {
        String source = image.attr("src");
        if (!source.startsWith("data:image/")) {
            XWPFRun fallback = paragraph.createRun();
            fallback.setText("[图片：" + image.attr("alt") + "] " + source);
            fallback.setFontFamily(DOCUMENT_FONT);
            return;
        }
        try {
            int comma = source.indexOf(',');
            String mediaType = source.substring(5, source.indexOf(';'));
            byte[] bytes = Base64.getDecoder().decode(source.substring(comma + 1));
            int pictureType = mediaType.contains("png")
                    ? XWPFDocument.PICTURE_TYPE_PNG
                    : mediaType.contains("gif")
                    ? XWPFDocument.PICTURE_TYPE_GIF
                    : XWPFDocument.PICTURE_TYPE_JPEG;
            XWPFRun run = paragraph.createRun();
            run.addPicture(
                    new ByteArrayInputStream(bytes),
                    pictureType,
                    StringUtils.hasText(image.attr("alt")) ? image.attr("alt") : "report-image",
                    Units.toEMU(500),
                    Units.toEMU(300));
        } catch (Exception e) {
            XWPFRun fallback = paragraph.createRun();
            fallback.setText("[图片解析失败：" + image.attr("alt") + "]");
        }
    }

    private String toSafeHtml(String content, String format) {
        String html = "html".equalsIgnoreCase(format)
                ? content
                : renderMarkdown(content);
        Safelist safelist = Safelist.relaxed()
                .addTags("table", "thead", "tbody", "tfoot", "tr", "th", "td",
                        "pre", "code", "details", "summary")
                .addAttributes(":all", "class")
                .addAttributes("th", "colspan", "rowspan")
                .addAttributes("td", "colspan", "rowspan")
                .addProtocols("img", "src", "data", "http", "https");
        String cleaned = Jsoup.clean(
                html, "", safelist, new Document.OutputSettings().prettyPrint(false));
        Document safeDocument = Jsoup.parseBodyFragment(cleaned);
        for (Element image : safeDocument.select("img")) {
            String source = image.attr("src");
            if (!source.startsWith("data:image/")) {
                image.replaceWith(new TextNode(
                        "[外部图片：" + image.attr("alt") + "] " + source));
            }
        }
        return safeDocument.body().html();
    }

    private String renderMarkdown(String markdown) {
        List<Extension> extensions = List.of(TablesExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        Node document = parser.parse(markdown);
        return HtmlRenderer.builder().extensions(extensions).escapeHtml(true).build().render(document);
    }

    private String pdfDocument(String title, String body) {
        return """
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <title>%s</title>
                  <style>
                    @page {
                      size: A4;
                      margin: 18mm 18mm 20mm;
                      @top-right { content: "%s"; color: #777; font-size: 9pt; }
                      @bottom-center { content: "第 " counter(page) " 页 / 共 " counter(pages) " 页"; color: #777; font-size: 9pt; }
                    }
                    body { font-family: "ZenVis CJK", "Microsoft YaHei", sans-serif; color: #202124; font-size: 10.5pt; line-height: 1.65; }
                    h1, h2, h3, h4 { page-break-after: avoid; }
                    table { width: 100%%; border-collapse: collapse; page-break-inside: avoid; }
                    th, td { border: 1px solid #cfd3dc; padding: 6px 8px; }
                    img { max-width: 100%%; height: auto; }
                    pre { padding: 10px; background: #f5f7fa; white-space: pre-wrap; }
                    blockquote { margin-left: 0; padding-left: 12px; border-left: 3px solid #409eff; color: #606266; }
                  </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(escapeHtml(title), escapeCss(title), body);
    }

    private java.util.Optional<File> firstAvailableFont() {
        return List.of(
                        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
                        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                        "/System/Library/Fonts/PingFang.ttc",
                        "C:/Windows/Fonts/msyh.ttc")
                .stream()
                .map(File::new)
                .filter(File::isFile)
                .findFirst();
    }

    private String escapeHtml(String value) {
        return org.jsoup.nodes.Entities.escape(value == null ? "" : value);
    }

    private String escapeCss(String value) {
        return escapeHtml(value).replace("\"", "\\\"");
    }

    private String safeFileName(String title) {
        String safe = (title == null ? "report" : title)
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "-")
                .replaceAll("^-+|-+$", "");
        return StringUtils.hasText(safe) ? safe : "report";
    }

    public record ExportedReport(String fileName, String contentType, byte[] content) {
    }
}
