package ru.gildina.indexer.util;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class PDFTextExtractor {
    private static final Logger logger = LoggerFactory.getLogger(PDFTextExtractor.class);

    /**
     * Извлекает текст из PDF файла
     */
    public static String extractTextFromPdf(Path filePath) {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {

            if (document.isEncrypted()) {
                logger.warn("PDF файл зашифрован, пропускаем: {}", filePath);
                return "";
            }

            PDFTextStripper pdfStripper = new PDFTextStripper();

            // Настраиваем извлечение текста
            pdfStripper.setSortByPosition(true);
            pdfStripper.setStartPage(1);
            pdfStripper.setEndPage(document.getNumberOfPages());

            String text = pdfStripper.getText(document);
            logger.debug("Извлечено {} символов из PDF: {}", text.length(), filePath);

            return text;

        } catch (IOException e) {
            logger.error("Ошибка при чтении PDF файла {}: {}", filePath, e.getMessage());
            return "";
        } catch (Exception e) {
            logger.error("Неожиданная ошибка при обработке PDF файла {}: {}", filePath, e.getMessage());
            return "";
        }
    }

    /**
     * Проверяет, является ли файл PDF
     */
    public static boolean isPdfFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf");
    }

    /**
     * Получает информацию о PDF файле
     */
    public static String getPdfInfo(Path filePath) {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            if (document.isEncrypted()) {
                return "Зашифрованный PDF";
            }

            int pageCount = document.getNumberOfPages();
            long fileSize = filePath.toFile().length();

            return String.format("PDF: %d страниц, %s",
                    pageCount,
                    formatFileSize(fileSize));

        } catch (Exception e) {
            return "Ошибка чтения PDF";
        }
    }

    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " байт";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f КБ", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f МБ", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f ГБ", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
