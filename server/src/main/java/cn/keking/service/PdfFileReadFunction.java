package cn.keking.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

public interface PdfFileReadFunction {
    void read(PDDocument asyncDoc, PDFRenderer asyncPdfRenderer, int asyncPageCount);
}