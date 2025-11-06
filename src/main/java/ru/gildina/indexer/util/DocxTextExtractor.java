package ru.gildina.indexer.util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DocxTextExtractor {
    private static final Logger logger = LoggerFactory.getLogger(DocxTextExtractor.class);

    /**
     * Упрощенный метод извлечения текста из DOCX
     */
    public static String extractTextFromDocx(Path filePath) {
        logger.info("🔍 Попытка извлечь текст из: {}", filePath);

        try {
            // Проверяем, что файл существует и доступен для чтения
            if (!Files.exists(filePath)) {
                logger.error(" Файл не существует: {}", filePath);
                return "";
            }

            if (!Files.isReadable(filePath)) {
                logger.error(" Файл недоступен для чтения: {}", filePath);
                return "";
            }

            long fileSize = Files.size(filePath);
            logger.info(" Размер файла: {} байт", fileSize);

            // Простая проверка - читаем первые 1000 байт чтобы убедиться что это DOCX
            byte[] header = Files.readAllBytes(filePath);
            if (header.length < 4) {
                logger.error("Файл слишком маленький: {}", filePath);
                return "";
            }

            // Проверяем сигнатуру DOCX (PK zip header)
            if (header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
                logger.error(" Это не DOCX файл (неверная сигнатура): {}", filePath);
                return "not_a_docx_file";
            }

            logger.info("Файл похож на DOCX (правильная сигнатура)");

            // Пробуем извлечь текст
            try (ZipFile zipFile = new ZipFile(filePath.toFile())) {
                logger.info("ZIP архив открыт успешно");

                // Получаем список всех entries для отладки
                java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
                int entryCount = 0;
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    logger.debug("Entry: {}", entry.getName());
                    entryCount++;
                }
                logger.info("Всего entries в архиве: {}", entryCount);

                // Ищем document.xml
                ZipEntry documentEntry = zipFile.getEntry("word/document.xml");
                if (documentEntry != null) {
                    logger.info("Найден word/document.xml");

                    try (InputStream is = zipFile.getInputStream(documentEntry)) {
                        String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        logger.info(" Размер document.xml: {} символов", content.length());

                        String extractedText = extractTextFromXml(content);
                        logger.info(" Извлечено текста: {} символов", extractedText.length());

                        return extractedText;
                    }
                } else {
                    logger.warn(" word/document.xml не найден в архиве");
                    // Пробуем другие возможные расположения
                    String[] possiblePaths = {
                            "word/document.xml",
                            "Document.xml",
                            "document.xml"
                    };

                    for (String path : possiblePaths) {
                        ZipEntry altEntry = zipFile.getEntry(path);
                        if (altEntry != null) {
                            logger.info("Найден альтернативный путь: {}", path);
                            try (InputStream is = zipFile.getInputStream(altEntry)) {
                                String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                return extractTextFromXml(content);
                            }
                        }
                    }

                    logger.error(" Не найден ни один document.xml в архиве");
                    return "no_document_xml_found";
                }

            } catch (Exception e) {
                logger.error(" Ошибка при чтении ZIP архива: {}", e.getMessage());
                return "zip_read_error";
            }

        } catch (Exception e) {
            logger.error(" Критическая ошибка при обработке DOCX: {}", e.getMessage());
            e.printStackTrace();
            return "critical_error";
        }
    }

    /**
     * Упрощенный метод извлечения текста из XML
     */
    private static String extractTextFromXml(String xml) {
        logger.info(" Начинаем извлечение текста из XML...");

        StringBuilder text = new StringBuilder();
        int start = 0;
        int textCount = 0;

        while (true) {
            int tagStart = xml.indexOf("<w:t", start);
            if (tagStart == -1) break;

            int textStart = xml.indexOf('>', tagStart) + 1;
            if (textStart == 0) break;

            int textEnd = xml.indexOf("</w:t>", textStart);
            if (textEnd == -1) break;

            String textContent = xml.substring(textStart, textEnd);
            text.append(textContent).append(" ");
            textCount++;

            start = textEnd + 6;
        }

        logger.info(" Найдено {} текстовых блоков", textCount);
        logger.info("Итоговый текст: '{}'", text.toString().trim());

        return text.toString().trim();
    }
}