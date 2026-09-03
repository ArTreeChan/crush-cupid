package cn.yzfy.crushcupidserver.common;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * @className DocumentTextExtractor
 * @description 对话消息附件的内容解析器：按文件类型分发——
 * pdf 走 PDFBox 抽取文本，docx 走 POI XWPF 抽取文本，其余（txt/md/csv/json 等）回退 {@link TextExtractor} 按编码读取。
 * <p>
 * 注意职责边界：这是「对话发送消息」场景的实时解析（用户发附件给 crush 当场看），
 * 与「补充材料」上传（CrushSourceController 入库构建人设）互不相干；后者维持原有文本读取。
 * @author 一朝风月
 * @code util
 * @createTime 2026-08-27
 */
public final class DocumentTextExtractor {

    private DocumentTextExtractor() {
    }

    /**
     * 按文件名后缀分发解析。
     *
     * @param fileName 文件名（用于识别类型，可空——空时按纯文本处理）
     * @param bytes    文件原始字节
     * @return 抽取的文本；解析失败抛异常由调用方决定降级策略
     */
    public static String extract(String fileName, byte[] bytes) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return extractPdf(bytes);
        }
        if (name.endsWith(".docx")) {
            return extractDocx(bytes);
        }
        return TextExtractor.extract(bytes);
    }

    /**
     * PDF 文本抽取（PDFBox 3：Loader.loadPDF）。
     */
    private static String extractPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        } catch (Exception e) {
            throw new IllegalStateException("PDF 解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * docx 文本抽取（POI XWPFWordExtractor）。
     */
    private static String extractDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (Exception e) {
            throw new IllegalStateException("Word 文档解析失败：" + e.getMessage(), e);
        }
    }
}
