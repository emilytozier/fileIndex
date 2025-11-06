package ru.gildina.indexer.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gildina.indexer.model.FileIndexEntry;
import ru.gildina.indexer.monitor.MemoryMonitor;
import ru.gildina.indexer.util.DocxTextExtractor;
import ru.gildina.indexer.util.PDFTextExtractor;
import ru.gildina.indexer.util.PathUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileWalker {
    private static final Logger logger = LoggerFactory.getLogger(FileWalker.class);

    private final List<String> supportedExtensions;
    private final AtomicInteger processedFiles = new AtomicInteger(0);
    private final AtomicInteger totalFiles = new AtomicInteger(0);
    private final AtomicInteger skippedFiles = new AtomicInteger(0);

    public FileWalker(List<String> supportedExtensions) {
        // Нормализуем расширения - убираем точку если есть и приводим к нижнему регистру
        this.supportedExtensions = new ArrayList<>();
        for (String ext : supportedExtensions) {
            String normalizedExt = ext.startsWith(".") ? ext.substring(1) : ext;
            this.supportedExtensions.add(normalizedExt.toLowerCase());
        }

        logger.info("Поддерживаемые расширения: {}", this.supportedExtensions);
    }

    public List<FileIndexEntry> walkDirectory(String directoryPath) throws IOException {
        // Нормализуем путь перед использованием
        Path startDir;
        try {
            startDir = PathUtils.validateAndGetPath(directoryPath);
        } catch (java.nio.file.InvalidPathException e) {
            throw new IOException("Invalid path: " + directoryPath + "\n" + e.getReason());
        }

        // Проверяем существование директории
        if (!Files.exists(startDir)) {
            throw new IOException("Директория не существует: " + directoryPath);
        }
        if (!Files.isDirectory(startDir)) {
            throw new IOException("Указанный путь не является директорией: " + directoryPath);
        }

        List<FileIndexEntry> fileEntries = new ArrayList<>();
        logger.info("Начинаем обход директории: {}", startDir.toAbsolutePath());

        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                totalFiles.incrementAndGet();
                // Периодически вызываем GC каждые 50 файлов
                if (totalFiles.get() % 50 == 0) {
                    System.gc();
                }
                String fileName = file.getFileName().toString();

                logger.debug("Найден файл: {}", file);

                if (isSupportedFile(file)) {
                    try {
                        logger.debug("Обрабатываем файл: {}", file);
                        FileIndexEntry entry = new FileIndexEntry(file, attrs);
                        processFileContent(file, entry);
                        fileEntries.add(entry);
                        processedFiles.incrementAndGet();

                        if (processedFiles.get() % 10 == 0) {
                            System.out.printf("Обработано: %d/%d файлов. ",
                                    processedFiles.get(), totalFiles.get());
                            MemoryMonitor.printHeapInfo();
                        }
                    } catch (Exception e) {
                        logger.error("Ошибка при обработке файла {}: {}", file, e.getMessage());
                        skippedFiles.incrementAndGet();
                    }
                } else {
                    logger.debug("Файл не поддерживается: {}", file);
                    skippedFiles.incrementAndGet();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                logger.error("Не удалось файл {}: {}", file, exc.getMessage());
                skippedFiles.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }
        });

        logger.info("Обход завершен. Обработано {}/{} файлов, пропущено: {}",
                processedFiles.get(), totalFiles.get(), skippedFiles.get());
        return fileEntries;
    }


    private boolean isSupportedFile(Path file) {
        // Если список расширений пустой - обрабатываем все файлы
        if (supportedExtensions.isEmpty()) {
            return true;
        }

        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();
            boolean supported = supportedExtensions.contains(extension);
            if (!supported) {
                logger.debug("Расширение '{}' не поддерживается для файла: {}", extension, file);
            }
            return supported;
        }

        logger.debug("Файл без расширения: {}", file);
        return false;
    }

    public void processFileContent(Path file, FileIndexEntry entry) throws IOException {
        try {
            // Проверяем размер файла
            long fileSize = Files.size(file);
            if (fileSize > 50 * 1024 * 1024) { // 50MB
                logger.warn("Файл слишком большой ({} bytes), пропускаем: {}", fileSize, file);
                return;
            }

            if (fileSize == 0) {
                logger.debug("Пустой файл: {}", file);
                return;
            }

            logger.debug("Чтение файла: {} ({} bytes)", file, fileSize);

            // Обрабатываем разные типы файлов
            String fileName = file.getFileName().toString().toLowerCase();

            if (fileName.endsWith(".docx") || fileName.endsWith(".docm")) {
                processDocxFile(file, entry);
            } else if (fileName.endsWith(".pdf")) {
                processPdfFile(file, entry);
            } else {
                // Текстовые файлы
                readFileWithFallbackEncoding(file, entry);
            }

            logger.debug("Обработан файл: {}, слов: {}", file, entry.getTotalWords());

        } catch (IOException e) {
            logger.warn("Не удалось прочитать файл {}: {}", file, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Неожиданная ошибка при обработке файла {}: {}", file, e.getMessage());
            throw new IOException("Ошибка обработки файла", e);
        }
    }

    public void processDocxFile(Path file, FileIndexEntry entry) {
        System.out.println(" Обработка DOCX: " + file.getFileName());

        try {
            String text = DocxTextExtractor.extractTextFromDocx(file);
            System.out.println("Извлечено символов: " + (text != null ? text.length() : 0));

            if (text != null && !text.trim().isEmpty()) {
                processTextContent(text, entry);
                System.out.println("DOCX обработан: " + entry.getTotalWords() + " слов, " +
                        entry.getUniqueWords() + " уникальных");

                // Покажем примеры слов
                if (entry.getTotalWords() > 0) {
                    System.out.println(" Примеры слов:");
                    entry.getWordCounts().entrySet().stream()
                            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                            .limit(5)
                            .forEach(e -> System.out.println("   - " + e.getKey() + " (" + e.getValue() + ")"));
                }
            } else {
                System.out.println(" Не удалось извлечь текст из DOCX");
            }
        } catch (Exception e) {
            System.out.println("Ошибка при обработке DOCX: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Альтернативный метод извлечения текста из DOCX
     */
    private void tryAlternativeDocxExtraction(Path file, FileIndexEntry entry) {
        logger.info("Пробуем альтернативный метод извлечения для: {}", file);

        try {
            // Простая проверка - читаем файл как бинарный и ищем текстовые последовательности
            byte[] fileContent = Files.readAllBytes(file);
            String binaryContent = new String(fileContent, java.nio.charset.StandardCharsets.ISO_8859_1);

            // Ищем русские и английские слова в бинарном содержимом
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[a-zA-Zа-яА-Я]{3,}");
            java.util.regex.Matcher matcher = pattern.matcher(binaryContent);

            int wordCount = 0;
            while (matcher.find()) {
                String word = matcher.group().toLowerCase();
                if (isValidWord(word)) {
                    entry.addWord(word);
                    wordCount++;
                }
            }

            if (wordCount > 0) {
                logger.info(" Альтернативный метод нашел {} слов в: {}", wordCount, file);
            } else {
                logger.warn(" Альтернативный метод тоже не нашел слов в: {}", file);
            }

        } catch (Exception e) {
            logger.error("Ошибка в альтернативном методе: {}", e.getMessage());
        }
    }

    /**
     * Обрабатывает PDF файлы с подробным логированием
     */
    public void processPdfFile(Path file, FileIndexEntry entry) {
        logger.info("Начинаем обработку PDF файла: {}", file);

        try {
            String text = PDFTextExtractor.extractTextFromPdf(file);
            logger.info("Извлеченный текст из PDF: {} символов", text != null ? text.length() : 0);

            if (text != null && !text.trim().isEmpty()) {
                logger.info("Текст успешно извлечен, начинаем обработку...");
                processTextContent(text, entry);
                logger.info("Успешно обработан PDF: {} ({} символов, {} слов)",
                        file.getFileName(), text.length(), entry.getTotalWords());
            } else {
                logger.warn("Не удалось извлечь текст из PDF файла: {}", file);
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке PDF файла {}: {}", file, e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Обрабатывает текстовое содержимое с улучшенной очисткой
     */
    private void processTextContent(String text, FileIndexEntry entry) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        // Очистка текста от лишних пробелов и переносов строк
        text = text.replaceAll("\\s+", " ").trim();

        // Ограничиваем длину текста для очень больших файлов
        if (text.length() > 100000) {
            text = text.substring(0, 100000);
            logger.debug("Текст обрезан до 100000 символов для файла");
        }

        String[] lines = text.split("\\r?\\n");
        int totalLines = lines.length;
        int processedLines = 0;

        for (String line : lines) {
            processedLines++;
            if (processedLines % 1000 == 0) {
                logger.debug("Обработано {}/{} строк", processedLines, totalLines);
            }
            processLine(line, entry);
        }

        logger.debug("Обработано всего строк: {}", processedLines);
    }
    private void processLine(String line, FileIndexEntry entry) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }

        // Ограничиваем длину обрабатываемой строки
        if (line.length() > 10000) {
            line = line.substring(0, 10000);
        }

        // Улучшенное разбиение на слова - поддерживает русский, английский и специальные символы
        String[] words = line.split("[^a-zA-Zа-яА-Я0-9_-]+");

        for (String word : words) {
            String cleanedWord = word.trim().toLowerCase();

            // Фильтруем короткие слова, числа и шум
            if (isValidWord(cleanedWord)) {
                entry.addWord(cleanedWord);
            }
        }
    }

    /**
     * Проверяет, является ли слово валидным для индексации
     */
    private boolean isValidWord(String word) {
        if (word.isEmpty() || word.length() < 2) {
            return false;
        }

        // Исключаем числа
        if (word.matches("\\d+")) {
            return false;
        }

        // Исключаем common noise слова
        String[] noiseWords = {
                "nbsp", "amp", "lt", "gt", "quot", "apos",
                "http", "https", "www", "com", "org", "net",
                "xml", "html", "body", "div", "span", "class"
        };

        for (String noise : noiseWords) {
            if (word.equals(noise)) {
                return false;
            }
        }

        // Исключаем слишком длинные "слова" (вероятно, ошибки)
        if (word.length() > 50) {
            return false;
        }

        // Исключаем слова только из повторяющихся символов
        if (word.matches("(.)\\1{2,}")) { // aaaa, bbbb, etc
            return false;
        }

        return true;
    }

    /**
     * Проверяет, является ли строка числом
     */
    private boolean isNumber(String word) {
        return word.matches("\\d+");
    }

    /**
     * Проверяет, является ли слово common noise (html теги, etc)
     */
    private boolean isCommonNoise(String word) {
        return word.matches("</?[a-z]+>") || // HTML теги
                word.equals("nbsp") ||
                word.equals("amp") ||
                word.length() > 50; // Слишком длинные "слова"
    }
    private void processLargeFile(Path file, FileIndexEntry entry) throws IOException {
        // Для больших файлов читаем построчно с ограничением
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineCount = 0;
            int maxLines = 10000; // Ограничиваем количество строк

            while ((line = reader.readLine()) != null && lineCount < maxLines) {
                processLine(line, entry);
                lineCount++;

                // Периодически вызываем GC для больших файлов
                if (lineCount % 1000 == 0) {
                    System.gc();
                }
            }

            if (lineCount >= maxLines) {
                logger.warn("Файл {} слишком большой, обработано только первых {} строк", file, maxLines);
            }
        } catch (IOException e) {
            // Пробуем другие кодировки
            readFileWithFallbackEncoding(file, entry);
        }
    }

    private void readFileWithFallbackEncoding(Path file, FileIndexEntry entry) throws IOException {
        Charset[] charsetsToTry = {
                StandardCharsets.UTF_8,
                Charset.forName("Windows-1251"),
                Charset.forName("KOI8-R"),
                StandardCharsets.ISO_8859_1
        };

        for (Charset charset : charsetsToTry) {
            try {
                readFileWithCharset(file, entry, charset);
                logger.debug("Файл {} успешно прочитан в кодировке {}", file, charset);
                return; // Успешно прочитали
            } catch (IOException e) {
                logger.debug("Не удалось прочитать файл {} в кодировке {}: {}",
                        file, charset, e.getMessage());
                // Пробуем следующую кодировку
            }
        }

        // Если все кодировки не подошли, пробуем бинарное чтение для текстовых файлов
        logger.warn("Не удалось прочитать файл {} ни в одной кодировке, пробуем бинарное чтение", file);
        readFileAsBinary(file, entry);
    }

    private void readFileWithCharset(Path file, FileIndexEntry entry, Charset charset) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, charset)) {
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line, entry);
            }
        }
    }

    private void readFileAsBinary(Path file, FileIndexEntry entry) throws IOException {
        try (InputStream is = Files.newInputStream(file);
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {

            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line, entry);
            }
        }
    }


    public int getProcessedFilesCount() {
        return processedFiles.get();
    }

    public int getTotalFilesCount() {
        return totalFiles.get();
    }

    public int getSkippedFilesCount() {
        return skippedFiles.get();
    }
}