package ru.gildina.indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gildina.indexer.database.ConnectSQLLite;
import ru.gildina.indexer.database.DatabaseManager;
import ru.gildina.indexer.model.FileIndexEntry;
import ru.gildina.indexer.monitor.MemoryMonitor;
import ru.gildina.indexer.service.FileWalker;
import ru.gildina.indexer.service.SearchService;
import ru.gildina.indexer.util.PDFTextExtractor;
import ru.gildina.indexer.util.PathUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class FileIndexerApp {
    private static final Logger logger = LoggerFactory.getLogger(FileIndexerApp.class);

    private final DatabaseManager databaseManager;
    private final SearchService searchService;
    static {
        // Устанавливаем UTF-8 кодировку через системные свойства
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");

        // Безопасная установка кодировки для вывода
        try {
            System.out.flush();
            if (System.out.checkError()) {
                // Пересоздаем System.out с правильной кодировкой
                System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
            }
        } catch (Exception e) {
            // Игнорируем ошибки, просто логируем
            logger.debug("Could not reconfigure System.out encoding: {}", e.getMessage());
        }
    }

    public FileIndexerApp() {
        ApplicationContext context = ApplicationContext.getInstance();
        this.databaseManager = context.getBean(DatabaseManager.class);
        this.searchService = context.getBean(SearchService.class);
    }

    public static void main(String[] args) {

        logger.info("Launch File Indexer Application");
        setupEncoding();
        FileIndexerApp app = new FileIndexerApp();

        if (args.length > 0) {
            app.handleCommandLineArgs(args);
        } else {
            app.runInteractiveMode();
        }
    }
    private static void setupEncoding() {
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            System.setErr(new PrintStream(System.err, true, "UTF-8"));
            System.setProperty("console.encoding", "UTF-8");
            Field outField = System.class.getDeclaredField("out");
            outField.setAccessible(true);

            Field errField = System.class.getDeclaredField("err");
            errField.setAccessible(true);

        } catch (Exception e) {
            System.err.println("Warning: Could not set UTF-8 encoding: " + e.getMessage());
        }
    }

    private void handleCommandLineArgs(String[] args) {
        if (args.length == 0) {
            runInteractiveMode();
            return;
        }

        switch (args[0]) {
            case "--index":
                if (args.length < 2) {
                    System.out.println("Error: Specify directory for indexing: --index <path>");
                    return;
                }
                List<String> extensions = Arrays.asList(
                        "txt", "java", "xml", "json", "csv", "md", "properties",
                        "html", "htm", "css", "js", "py", "cpp", "c", "h",
                        "sql", "log", "cfg", "conf", "ini", "docx", "pdf", "rtf",
                        "doc", "odt", "epub", "fb2"
                );
                indexDirectory(args[1], extensions);
                break;
            case "--search-name":
                if (args.length < 2) {
                    System.out.println("Error: Specify search query: --search-name <query>");
                    return;
                }
                search(args[1], SearchService.SearchType.FILE_NAME);
                break;
            case "--search-content":
                if (args.length < 2) {
                    System.out.println("Error: Specify search query: --search-content <query>");
                    return;
                }
                search(args[1], SearchService.SearchType.CONTENT);
                break;
            case "--clear":
                clearIndex();
                break;
            case "--stats":
            case "--statistics":
                showStatistics();
                break;
            case "--help":
            case "-h":
            case "/?":
                printHelp();
                break;
            default:
                System.out.println("Unknown command: " + args[0]);
                printHelp();
        }
    }
    private void printHelp() {
        System.out.println("File Indexer and Search Engine");
        System.out.println("Version 1.0.0");
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("  java -jar file-indexer.jar [COMMAND] [OPTIONS]");
        System.out.println();
        System.out.println("COMMANDS:");
        System.out.println("  --index <path>              Index directory and all subdirectories");
        System.out.println("  --search-name <query>       Search files by name (supports partial matching)");
        System.out.println("  --search-content <query>    Search files by content (full-text search)");
        System.out.println("  --clear                     Clear all indexed data");
        System.out.println("  --stats                     Show indexing statistics");
        System.out.println("  --help, -h                  Show this help message");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("  java -jar file-indexer.jar --index /path/to/documents");
        System.out.println("  java -jar file-indexer.jar --search-name \"report\"");
        System.out.println("  java -jar file-indexer.jar --search-content \"database\"");
        System.out.println("  java -jar file-indexer.jar --clear");
        System.out.println("  java -jar file-indexer.jar --stats");
        System.out.println();
        System.out.println("INTERACTIVE MODE:");
        System.out.println("  java -jar file-indexer.jar                    # Launch interactive mode");
        System.out.println();
        System.out.println("MEMORY SETTINGS FOR LARGE DIRECTORIES:");
        System.out.println("  Small directories (<1000 files):");
        System.out.println("    java -Xmx2g -jar file-indexer.jar --index /path");
        System.out.println();
        System.out.println("  Medium directories (1000-5000 files):");
        System.out.println("    java -Xmx4g -jar file-indexer.jar --index /path");
        System.out.println();
        System.out.println("  Large directories (5000+ files):");
        System.out.println("    java -Xmx8g -XX:+UseG1GC -jar file-indexer.jar --index /path");
        System.out.println();
        System.out.println("SUPPORTED FILE FORMATS:");
        System.out.println("  Text files: .txt, .java, .xml, .json, .csv, .md, .properties");
        System.out.println("  Documents: .docx, .pdf, .rtf, .doc, .odt, .epub, .fb2");
        System.out.println("  Code: .html, .css, .js, .py, .cpp, .c, .h, .sql");
        System.out.println("  Configs: .ini, .cfg, .conf, .yml, .yaml");
        System.out.println();
        System.out.println("NOTES:");
        System.out.println("  - Files larger than 50MB are skipped");
        System.out.println("  - Temporary files (starting with ~$) are ignored");
        System.out.println("  - Binary files (.exe, .dll, .zip, etc.) are not indexed");
        System.out.println("  - Database is stored in 'file_indexer.db' file");
        System.out.println();
        System.out.println("ENCODING SUPPORT:");
        System.out.println("  For proper Russian text display, use:");
        System.out.println("    java -Dfile.encoding=UTF-8 -jar file-indexer.jar");
    }

    private void runInteractiveMode() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            printMenu();
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    handleIndexingInput(scanner);
                    break;
                case "2":
                    System.out.print("Enter the file name to search for: ");
                    String fileName = scanner.nextLine().trim();
                    search(fileName, SearchService.SearchType.FILE_NAME);
                    break;
                case "3":
                    System.out.print("Enter text to search in content: ");
                    String content = scanner.nextLine().trim();
                    search(content, SearchService.SearchType.CONTENT);
                    break;
                case "4":
                    clearIndex();
                    break;
                case "5":
                    showStatistics();
                    break;
                case "6":
                    checkDatabase();
                    break;
                case "7":
                    showFileDetails();
                    break;
                case "8":
                    testFileProcessing();
                    break;
                case "9":
                    MemoryMonitor.printHeapInfo();
                    break;
                case "10":
                    MemoryMonitor.forceGC();
                    break;
                case "11":
                    MemoryMonitor.printMemoryStats();
                    break;
                case "0":
                    System.out.println("Exit.");
                    return;
                default:
                    System.out.println("Wrong choice. Try again.");
            }

            System.out.println("\nPress Enter to continue...");
            scanner.nextLine();
        }
    }
    private void showFileDetails() {
        System.out.print("Введите путь или имя файла для просмотра деталей: ");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();

        if (input.isEmpty()) {
            System.out.println(" Путь не может быть пустым!");
            return;
        }

        try {
            // Нормализуем ввод пользователя
            String normalizedInput = input.replace('\\', '/');
            System.out.println("Поиск: " + normalizedInput);

            // Пробуем разные стратегии поиска через SearchService
            List<FileIndexEntry> results = new ArrayList<>();

            // 1. Точный поиск по нормализованному пути
            try {
                List<FileIndexEntry> exactResults = searchService.searchByExactNormalizedPath(normalizedInput);
                results.addAll(exactResults);
            } catch (Exception e) {
                System.out.println("Точный поиск не дал результатов");
            }

            // 2. Поиск по частичному пути (если точный не нашел)
            if (results.isEmpty()) {
                try {
                    List<FileIndexEntry> partialResults = searchService.searchByPartialPath(normalizedInput);
                    results.addAll(partialResults);
                } catch (Exception e) {
                    System.out.println("Поиск по частичному пути не дал результатов");
                }
            }

            // 3. Поиск по имени файла (извлекаем имя из пути)
            if (results.isEmpty()) {
                try {
                    String fileName = extractFileNameFromPath(normalizedInput);
                    List<FileIndexEntry> nameResults = searchService.searchByFileNamePartial(fileName);
                    results.addAll(nameResults);
                } catch (Exception e) {
                    System.out.println("Поиск по имени файла не дал результатов");
                }
            }

            // Обрабатываем результаты
            if (results.isEmpty()) {
                showDetailedNotFoundMessage(input, normalizedInput);
            } else if (results.size() == 1) {
                printFileDetails(results.get(0));
            }

        } catch (Exception e) {
            System.out.println(" Ошибка: " + e.getMessage());
        }
    }
    private void showDetailedNotFoundMessage(String originalInput, String normalizedInput) {
        System.out.println("\nФайл не найден в индексе!");
        System.out.println("════════════════════════════════════════");
        System.out.println("Оригинальный запрос: " + originalInput);
        System.out.println("Нормализованный запрос: " + normalizedInput);

        // Проверяем существование файла в файловой системе
        File file = new File(originalInput);
        System.out.println("\nПроверка файловой системы:");
        if (file.exists()) {
            System.out.println(" Файл существует в файловой системе");
            System.out.println("   Размер: " + formatFileSize(file.length()));
            System.out.println("   Последнее изменение: " + new java.util.Date(file.lastModified()));
            System.out.println("   Путь: " + file.getAbsolutePath());
            System.out.println("   Можно читать: " + file.canRead());
        } else {
            System.out.println(" Файл не существует в файловой системе");
            System.out.println("   Проверьте правильность пути и имя файла");
        }

        // Проверяем директорию
        File parentDir = file.getParentFile();
        if (parentDir != null && parentDir.exists()) {
            System.out.println(" Директория существует: " + parentDir.getAbsolutePath());

        } else if (parentDir != null) {
            System.out.println(" Директория не существует: " + parentDir.getAbsolutePath());
        }


        System.out.println("\n Возможные решения:");
        System.out.println("1. Проиндексируйте директорию заново (опция 1)");
        System.out.println("2. Проверьте, что файл имеет поддерживаемое расширение");
        System.out.println("3. Убедитесь, что файл не слишком большой (>50MB)");
        System.out.println("4. Проверьте права доступа к файлу");
        System.out.println("5. Используйте поиск по части имени файла (опция 2)");

        // Показываем статистику индекса
        try {
            long totalFiles = databaseManager.getIndexedFilesCount();
            System.out.println("Текущая статистика индекса:");
            System.out.println("   Всего проиндексировано файлов: " + totalFiles);
            if (totalFiles == 0) {
                System.out.println("  Индекс пуст! Сначала проиндексируйте директорию.");
            }
        } catch (SQLException e) {
            System.out.println("   Не удалось получить статистику: " + e.getMessage());
        }
    }

    /**
     * Извлекает имя файла из пути
     * Пример: "D:/мои книги/документ.txt" -> "документ.txt"
     */
    private String extractFileNameFromPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return path;
        }

        // Нормализуем путь - заменяем обратные слеши на прямые
        String normalizedPath = path.replace('\\', '/');

        // Ищем последний слеш
        int lastSlashIndex = normalizedPath.lastIndexOf('/');

        if (lastSlashIndex >= 0 && lastSlashIndex < normalizedPath.length() - 1) {
            // Возвращаем часть после последнего слеша
            return normalizedPath.substring(lastSlashIndex + 1);
        }

        // Если слешей нет, возвращаем весь путь как имя файла
        return normalizedPath;
    }
    /**
     * Обрабатывает ввод пути для индексации с улучшенными сообщениями об ошибках
     */
    private void handleIndexingInput(Scanner scanner) {
        System.out.print("Введите директорию для индексирования файлов: ");
        String directory = scanner.nextLine().trim();

        if (directory.isEmpty()) {
            System.out.println("Путь не может быть пустым!");
            return;
        }

        try {
            // Показываем нормализованный путь
            String normalizedPath = PathUtils.normalizePath(directory);
            if (!normalizedPath.equals(directory)) {
                System.out.println("Нормализованный путь: " + normalizedPath);
            }

            List<String> extensions = Arrays.asList(
                    "txt", "java", "xml", "json", "csv", "md", "properties",
                    "html", "htm", "css", "js", "py", "cpp", "c", "h",
                    "sql", "log", "cfg", "conf", "ini", "docx", "pdf", "rtf",
                    "doc", "odt", "epub", "fb2"
            );

            indexDirectory(directory, extensions);

        } catch (IllegalArgumentException e) {
            System.out.println("" + e.getMessage());
        }
    }
    private void printFileDetails(FileIndexEntry file) {
        try {
            // ЗАГРУЖАЕМ СЛОВА ИЗ БАЗЫ ДАННЫХ!
            System.out.println("Загрузка слов из базы данных...");
            searchService.loadWordsForFile(file);

        } catch (Exception e) {
            System.out.println("Не удалось загрузить слова из базы: " + e.getMessage());
        }

        System.out.println("\n=== Детали файла ===");
        System.out.println("Имя: " + file.getFileName());
        System.out.println("Путь: " + file.getPath());
        System.out.println("Размер: " + formatFileSize(file.getSize()));
        System.out.println("Расширение: " + file.getExtension());
        System.out.println("Директория: " + file.getDirectory());
        System.out.println("Последний раз изменено: " + file.getFormattedLastModified());
        System.out.println("Относительное время изменения: " + file.getRelativeLastModified());
        System.out.println("Всего слов: " + file.getTotalWords());
        System.out.println("Уникальных слов: " + file.getUniqueWords());
        System.out.println("ID БД: " + (file.getId() != null ? file.getId() : "Not saved"));

        // Дополнительная информация для специфичных форматов
        if (file.getExtension().equalsIgnoreCase("pdf")) {
            String pdfInfo = PDFTextExtractor.getPdfInfo(java.nio.file.Paths.get(file.getPath()));
            System.out.println("Информация о PDF: " + pdfInfo);
        }

        // Показываем самые частые слова
        if (file.getTotalWords() > 0) {
            System.out.println("\nТоп-10 самых частых слов:");
            file.getWordCounts().entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(10)
                    .forEach(entry ->
                            System.out.printf("   %s: %d раз\n", entry.getKey(), entry.getValue())
                    );
        } else {
            System.out.println("\nСлов не найдено в базе данных");

            // Проверяем, есть ли слова в таблице file_contents
            checkWordsInDatabase(file);
        }
    }

    /**
     * Проверяет наличие слов в базе данных для файла
     */
    private void checkWordsInDatabase(FileIndexEntry file) {
        try {
            String sql = "SELECT COUNT(*) as word_count FROM file_contents WHERE file_id = ?";

            try (Connection conn = ConnectSQLLite.getInstance().getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setLong(1, file.getId());
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    int wordCount = rs.getInt("word_count");
                    System.out.println(" В таблице file_contents записей: " + wordCount);

                    if (wordCount > 0) {
                        System.out.println(" Слова есть в БД, но не загружены в объект!");

                        // Показываем примеры слов из БД
                        String examplesSql = "SELECT word, word_count FROM file_contents WHERE file_id = ? LIMIT 5";
                        try (PreparedStatement examplesStmt = conn.prepareStatement(examplesSql)) {
                            examplesStmt.setLong(1, file.getId());
                            ResultSet examplesRs = examplesStmt.executeQuery();

                            System.out.println("Примеры слов из БД:");
                            while (examplesRs.next()) {
                                System.out.println("   " + examplesRs.getString("word") + ": " + examplesRs.getInt("word_count"));
                            }
                        }
                    } else {
                        System.out.println("Слов нет в таблице file_contents - файл не был проиндексирован!");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Ошибка при проверке БД: " + e.getMessage());
        }
    }

    private void indexDirectory(String directoryPath, List<String> extensions) {
        try {
            System.out.println("Начало индексации директории: " + directoryPath);
            // Показываем состояние памяти до начала
            MemoryMonitor.printHeapInfo();
            FileWalker fileWalker = new FileWalker(extensions);
            List<FileIndexEntry> entries = fileWalker.walkDirectory(directoryPath);

            System.out.println("Найдено файлов для индексации: " + entries.size());
            // Показываем состояние памяти после обхода файлов
            MemoryMonitor.printHeapInfo();

            if (entries.isEmpty()) {
                System.out.println("Файлы не найдены!");
                return;
            }

            // Показываем информацию о каждом файле перед сохранением
            for (FileIndexEntry entry : entries) {
                System.out.println("Файл: " + entry.getFileName() +
                        ", слов: " + entry.getTotalWords() +
                        ", размер: " + entry.getSize() + " байт");
            }

            System.out.println(" Сохранение в базу данных...");
            debugFileEntries(entries);

            // Используем пакетное сохранение
            databaseManager.saveFileEntriesBatch(entries);
            // Показываем финальное состояние памяти
            MemoryMonitor.printHeapInfo();
            MemoryMonitor.printGCInfo();

            System.out.println("Индексация завершена! Сохранено файлов: " + entries.size());

        } catch (IOException e) {
            System.err.println("Ошибка при индексации директории: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println(" Ошибка при сохранении в базу данных: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Неожиданная ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void search(String query, SearchService.SearchType searchType) {
        try {
            List<FileIndexEntry> results = searchService.search(query, searchType);

            // Показываем время модификации в результатах поиска
            searchService.printSearchResultsWithTime(results, query, searchType);

            // Предлагаем посмотреть детали если найден один файл
            if (results.size() == 1) {
                System.out.println("Нашли один файл. Показать описание? (y/n): ");
                Scanner scanner = new Scanner(System.in);
                String answer = scanner.nextLine().trim().toLowerCase();
                if (answer.equals("y") || answer.equals("yes")) {
                    printFileDetails(results.get(0));
                }
            }

        } catch (Exception e) {
            System.out.println("Error during search: " + e.getMessage());
        }
    }
    /**
     * Форматирует размер файла в читаемом виде
     */
    private String formatFileSize(long size) {
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


    private void showSomeIndexedFiles() {
        try {
            // Показываем несколько файлов из индекса для примера
            List<FileIndexEntry> someFiles = searchService.searchByFileNamePartial("");
            if (!someFiles.isEmpty()) {
                System.out.println("\n Примеры файлов в индексе:");
                for (int i = 0; i < Math.min(someFiles.size(), 5); i++) {
                    FileIndexEntry entry = someFiles.get(i);
                    System.out.println("  " + entry.getFileName() + " -> " + entry.getPath());
                }
                System.out.println("... и еще " + (someFiles.size() - 5) + " файлов");
            }
        } catch (Exception e) {
            System.out.println("Не удалось показать примеры файлов: " + e.getMessage());
        }
    }

    private void clearIndex() {
        try {
            databaseManager.clearIndex();
            System.out.println("Индекс очищен.");
        } catch (SQLException e) {
            System.out.println("Ошибка очистки индекса: " + e.getMessage());
        }
    }
    private void checkDatabase() {
        try {
            System.out.println("\n=== Проверка БД ===");

            // Проверяем таблицу files
            String filesSQL = "SELECT id, file_name, file_path FROM files LIMIT 5";
            try (Connection conn = ConnectSQLLite.getInstance().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(filesSQL)) {

                System.out.println("Первые 5 слов в БД:");
                System.out.println("ID | Имя файла | Путь");
                System.out.println("---|-----------|-----");
                while (rs.next()) {
                    System.out.printf("%d | %s | %s\n",
                            rs.getInt("id"),
                            rs.getString("file_name"),
                            rs.getString("file_path"));
                }
            }

            // Проверяем таблицу file_contents
            String contentsSQL = """
            SELECT f.file_name, fc.word, fc.word_count 
            FROM file_contents fc 
            JOIN files f ON fc.file_id = f.id 
            LIMIT 10
            """;

            try (Connection conn = ConnectSQLLite.getInstance().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(contentsSQL)) {

                System.out.println("\nПервые 10 слов в индексе:");
                System.out.println("Файл | Слово | Кол-во");
                System.out.println("-----|------|------");
                while (rs.next()) {
                    System.out.printf("%s | %s | %d\n",
                            rs.getString("file_name"),
                            rs.getString("word"),
                            rs.getInt("word_count"));
                }
            }

        } catch (SQLException e) {
            System.out.println("Ошибка проверки БД: " + e.getMessage());
        }
    }

    private void showStatistics() {
        try {
            long fileCount = databaseManager.getIndexedFilesCount();
            System.out.println("\n=== Статистика ===");
            System.out.println("Проиндексированные файлы: " + fileCount);

            // Дополнительная статистика (опционально)
            if (fileCount > 0) {
                System.out.println("Файл бд: file_indexer.db");
                System.out.println("Последнее обновление: " + new java.util.Date());
            }

        } catch (SQLException e) {
            System.out.println("Ошибка в получении  статистики: " + e.getMessage());
        }
    }

    private void printMenu() {
        System.out.println("\n=== Индексатор файлов ===");
        System.out.println("1. Проиндексировать директорию");
        System.out.println("2. Искать по имени файла");
        System.out.println("3. Искать по содержимому");
        System.out.println("4. Очистить индекс");
        System.out.println("5. Статистика");
        System.out.println("6. Проверить БД");
        System.out.println("7. Описание файла");
        System.out.println("8. Протестировать файл");
        System.out.println("9. Мониторинг памяти");
        System.out.println("10. Принудительный GC");
        System.out.println("11. Детальная статистика памяти");
        System.out.println("0. Выход");
        System.out.print("Выберите действие: ");
    }
    private void debugFileEntries(List<FileIndexEntry> entries) {
        System.out.println("\n🔍 ДЕБАГ: Проверка объектов перед сохранением");


        for (int i = 0; i < entries.size(); i++) {
            FileIndexEntry entry = entries.get(i);
            System.out.println((i + 1) + ". " + entry.getFileName());
            System.out.println("   ID: " + entry.getId());
            System.out.println("   WordCounts is null: " + (entry.getWordCounts() == null));

            if (entry.getWordCounts() != null) {
                System.out.println("   Размер WordCounts: " + entry.getWordCounts().size());
                System.out.println("   Всего слов: " + entry.getTotalWords());
                System.out.println("   Уникальных слов: " + entry.getUniqueWords());

                // Покажем несколько слов
                if (entry.getTotalWords() > 0) {
                    System.out.println("   Примеры слов:");
                    entry.getWordCounts().entrySet().stream()
                            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                            .limit(3)
                            .forEach(e -> System.out.println("     - " + e.getKey() + " (" + e.getValue() + ")"));
                }
            } else {
                System.out.println("   WordCounts IS NULL!");
            }
            System.out.println();
        }

    }
    private void testFileProcessing() {
        System.out.print("Введите путь к файлу для тестирования обработки: ");
        Scanner scanner = new Scanner(System.in);
        String filePath = scanner.nextLine().trim();

        try {
            Path path = java.nio.file.Paths.get(filePath);

            if (!Files.exists(path)) {
                System.out.println(" Файл не существует: " + filePath);
                return;
            }

            System.out.println(" Тестируем обработку файла: " + path.getFileName());
            System.out.println(" Размер: " + Files.size(path) + " байт");
            System.out.println(" Расширение: " + getFileExtension(path));

            // Создаем временную запись для тестирования
            FileIndexEntry testEntry = new FileIndexEntry(path, Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class));

            // Обрабатываем файл
            FileWalker fileWalker = new FileWalker(java.util.Arrays.asList("docx", "pdf", "txt"));

            if (path.getFileName().toString().toLowerCase().endsWith(".docx")) {
                fileWalker.processDocxFile(path, testEntry);
            } else if (path.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                fileWalker.processPdfFile(path, testEntry);
            } else {
                fileWalker.processFileContent(path, testEntry);
            }

            System.out.println("Результат обработки:");
            System.out.println("   Слов найдено: " + testEntry.getTotalWords());
            System.out.println("   Уникальных слов: " + testEntry.getUniqueWords());

            if (testEntry.getTotalWords() > 0) {
                System.out.println("   Примеры слов:");
                testEntry.getWordCounts().entrySet().stream()
                        .limit(10)
                        .forEach(entry -> System.out.println("     - " + entry.getKey() + " (" + entry.getValue() + ")"));
            }

        } catch (Exception e) {
            System.out.println(" Ошибка при тестировании: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getFileExtension(Path file) {
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex > 0) ? fileName.substring(dotIndex + 1) : "";
    }



}
