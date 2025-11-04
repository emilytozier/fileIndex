package ru.gildina.indexer.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gildina.indexer.model.FileIndexEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private final ConnectSQLLite connectionManager;

    public DatabaseManager() {
        this.connectionManager = ConnectSQLLite.getInstance();
    }
    public List<FileIndexEntry> searchByPartialPath(String partialPath) throws SQLException {
        // Нормализуем путь для поиска
        String normalizedPath = partialPath.replace('\\', '/');

        String sql = "SELECT * FROM files WHERE file_path LIKE ? OR file_name LIKE ?";
        List<FileIndexEntry> results = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + normalizedPath + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, "%" + normalizedPath + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                FileIndexEntry entry = resultSetToFileEntry(rs);
                results.add(entry);
            }
        }

        System.out.println("Найдено " + results.size() + " файлов по пути: " + normalizedPath);
        return results;
    }

    /**
     * Сохраняет один файл в базу данных
     */
    public void saveFileEntry(FileIndexEntry entry) throws SQLException {
        System.out.println("Сохранение одного файла: " + entry.getFileName());
        System.out.println(" Слов для сохранения: " + entry.getTotalWords());

        List<FileIndexEntry> singleEntryList = new ArrayList<>();
        singleEntryList.add(entry);
        saveFileEntriesBatch(singleEntryList);
    }
    public void saveFileEntriesBatch(List<FileIndexEntry> entries) throws SQLException {
        if (entries.isEmpty()) {
            System.out.println("Нет файлов для сохранения");
            return;
        }

        System.out.println("Начало пакетного сохранения " + entries.size() + " файлов");


        String insertFileSQL = """
        INSERT OR REPLACE INTO files (file_path, file_name, file_size, last_modified, extension) 
        VALUES (?, ?, ?, ?, ?)
    """;

        String insertContentSQL = """
        INSERT INTO file_contents (file_id, word, word_count) 
        VALUES (?, ?, ?)
    """;

        Connection conn = null;
        try {
            conn = connectionManager.getConnection();
            conn.setAutoCommit(false);

            // Шаг 1: Детальная проверка всех файлов перед сохранением
            System.out.println("ПРОВЕРКА ФАЙЛОВ ПЕРЕД СОХРАНЕНИЕМ:");
            for (int i = 0; i < entries.size(); i++) {
                FileIndexEntry entry = entries.get(i);
                System.out.println((i + 1) + ". " + entry.getFileName());
                System.out.println("   Путь: " + entry.getPath());
                System.out.println("   Размер: " + entry.getSize() + " байт");
                System.out.println("    WordCounts: " + (entry.getWordCounts() == null ? "NULL " : "OK "));

                if (entry.getWordCounts() != null) {
                    System.out.println("   Слов всего: " + entry.getTotalWords());
                    System.out.println("    Уникальных слов: " + entry.getUniqueWords());

                    if (entry.getTotalWords() > 0) {
                        System.out.println("    Примеры слов:");
                        entry.getWordCounts().entrySet().stream()
                                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                                .limit(3)
                                .forEach(e -> System.out.println("     - " + e.getKey() + " (" + e.getValue() + ")"));
                    }
                } else {
                    System.out.println("    ВНИМАНИЕ: WordCounts is NULL!");
                }
                System.out.println();
            }

            // Шаг 2: Сохраняем файлы по одному, чтобы точно получить ID
            System.out.println("СОХРАНЕНИЕ ИНФОРМАЦИИ О ФАЙЛАХ:");
            List<Long> fileIds = new ArrayList<>();

            try (PreparedStatement fileStmt = conn.prepareStatement(insertFileSQL, Statement.RETURN_GENERATED_KEYS)) {
                for (FileIndexEntry entry : entries) {
                    fileStmt.setString(1, entry.getPath());
                    fileStmt.setString(2, entry.getFileName());
                    fileStmt.setLong(3, entry.getSize());
                    fileStmt.setLong(4, entry.getLastModifiedTime());
                    fileStmt.setString(5, entry.getExtension());

                    int affectedRows = fileStmt.executeUpdate();
                    System.out.println("   ✅ " + entry.getFileName() + " - сохранен в files (" + affectedRows + " строк)");

                    // Получаем ID сразу после вставки
                    try (ResultSet rs = fileStmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            long fileId = rs.getLong(1);
                            fileIds.add(fileId);
                            entry.setId(fileId);
                            System.out.println("    Присвоен ID: " + fileId);
                        } else {
                            System.out.println("   Не удалось получить ID для: " + entry.getFileName());
                            fileIds.add(null);
                        }
                    }
                }
            }

            // Шаг 3: Сохраняем слова для файлов, у которых они есть
            System.out.println("СОХРАНЕНИЕ СЛОВ:");
            int totalWordsSaved = 0;
            int filesWithWords = 0;

            try (PreparedStatement contentStmt = conn.prepareStatement(insertContentSQL)) {
                for (int i = 0; i < entries.size(); i++) {
                    FileIndexEntry entry = entries.get(i);
                    Long fileId = fileIds.get(i);

                    if (fileId == null) {
                        System.out.println("   Пропускаем " + entry.getFileName() + " - нет ID");
                        continue;
                    }

                    if (entry.getWordCounts() == null || entry.getWordCounts().isEmpty()) {
                        System.out.println("   ⚠️ Пропускаем " + entry.getFileName() + " - нет слов");
                        continue;
                    }

                    System.out.println("    Сохраняем слова для: " + entry.getFileName() + " (ID: " + fileId + ")");
                    System.out.println("      Количество слов: " + entry.getWordCounts().size());

                    // Удаляем старые записи
                    try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM file_contents WHERE file_id = ?")) {
                        deleteStmt.setLong(1, fileId);
                        int deleted = deleteStmt.executeUpdate();
                        System.out.println("       Удалено старых записей: " + deleted);
                    }

                    // Сохраняем новые слова
                    int wordCountForFile = 0;
                    for (Map.Entry<String, Integer> wordEntry : entry.getWordCounts().entrySet()) {
                        contentStmt.setLong(1, fileId);
                        contentStmt.setString(2, wordEntry.getKey());
                        contentStmt.setInt(3, wordEntry.getValue());
                        contentStmt.addBatch();
                        wordCountForFile++;
                        totalWordsSaved++;

                        // Выполняем batch каждые 500 слов
                        if (totalWordsSaved % 500 == 0) {
                            contentStmt.executeBatch();
                            System.out.println("      Сохранено " + totalWordsSaved + " слов...");
                        }
                    }

                    // Выполняем batch для этого файла
                    int[] results = contentStmt.executeBatch();
                    filesWithWords++;
                    System.out.println("      Сохранено слов для файла: " + results.length);

                    // Покажем несколько сохраненных слов для проверки
                    System.out.println("      Примеры сохраненных слов:");
                    entry.getWordCounts().entrySet().stream()
                            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                            .limit(3)
                            .forEach(e -> System.out.println("        - " + e.getKey() + " (" + e.getValue() + ")"));
                }
            }

            conn.commit();

            System.out.println("════════════════════════════════════════");
            System.out.println("ПАКЕТНОЕ СОХРАНЕНИЕ ЗАВЕРШЕНО!");
            System.out.println(" Файлов обработано: " + entries.size());
            System.out.println(" Файлов со словами: " + filesWithWords);
            System.out.println(" Всего слов сохранено: " + totalWordsSaved);

            // Финальная проверка
            System.out.println("\nФИНАЛЬНАЯ ПРОВЕРКА:");
            for (int i = 0; i < entries.size(); i++) {
                FileIndexEntry entry = entries.get(i);
                System.out.println((i + 1) + ". " + entry.getFileName() +
                        " - ID: " + entry.getId() +
                        ", слов: " + (entry.getWordCounts() != null ? entry.getTotalWords() : "NULL"));
            }

        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            System.out.println("ОШИБКА при пакетном сохранении: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
            }
        }
    }
    /**
     * Находит файл по точному пути
     */
    public FileIndexEntry findFileByPath(String filePath) throws SQLException {
        String normalizedPath = filePath.replace('\\', '/');
        String sql = "SELECT * FROM files WHERE file_path = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, normalizedPath);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                FileIndexEntry entry = resultSetToFileEntry(rs);

                // Сразу загружаем слова для файла
                loadWordsForFile(entry);

                return entry;
            }
            return null;
        }
    }

    public List<FileIndexEntry> searchByFileNamePartial(String fileName) throws SQLException {
        String sql = "SELECT * FROM files WHERE file_name LIKE ?";
        List<FileIndexEntry> results = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + fileName + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                FileIndexEntry entry = resultSetToFileEntry(rs);
                results.add(entry);
            }
        }

        System.out.println("Найдено " + results.size() + " файлов по имени: " + fileName);
        return results;
    }
    /**
     * Загружает слова для файла из базы данных
     */
    public void loadWordsForFile(FileIndexEntry entry) throws SQLException {
        if (entry.getId() == null) {
            return;
        }

        String sql = "SELECT word, word_count FROM file_contents WHERE file_id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, entry.getId());
            ResultSet rs = pstmt.executeQuery();

            Map<String, Integer> wordCounts = new HashMap<>();
            while (rs.next()) {
                String word = rs.getString("word");
                int count = rs.getInt("word_count");
                wordCounts.put(word, count);
            }

            entry.setWordCounts(wordCounts);
        }

        System.out.println("Загружено слов для файла " + entry.getFileName() + ": " + entry.getTotalWords());
    }
    public List<FileIndexEntry> searchByFileName(String fileName) throws SQLException {
        String sql = "SELECT * FROM files WHERE file_name LIKE ?";
        List<FileIndexEntry> results = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + fileName + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                FileIndexEntry entry = resultSetToFileEntry(rs);
                results.add(entry);
            }
        }

        logger.info("Найдено {} файлов по имени: {}", results.size(), fileName);
        return results;
    }
    public List<FileIndexEntry> searchByExactNormalizedPath(String filePath) throws SQLException {
        // Нормализуем путь для точного поиска
        String normalizedPath = filePath.replace('\\', '/');

        String sql = "SELECT * FROM files WHERE file_path = ?";
        List<FileIndexEntry> results = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, normalizedPath);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                FileIndexEntry entry = resultSetToFileEntry(rs);
                results.add(entry);
            }
        }

        System.out.println("Точный поиск: найдено " + results.size() + " файлов по пути: " + normalizedPath);
        return results;
    }

    public List<FileIndexEntry> searchByContent(String searchWord) throws SQLException {
        String sql = """
            SELECT f.*, SUM(fc.word_count) as relevance 
            FROM files f 
            JOIN file_contents fc ON f.id = fc.file_id 
            WHERE fc.word LIKE ? 
            GROUP BY f.id 
            ORDER BY relevance DESC
        """;

        List<FileIndexEntry> results = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + searchWord.toLowerCase() + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                FileIndexEntry entry = resultSetToFileEntry(rs);
                results.add(entry);
            }
        }

        logger.info("Найдено {} файлов по содержимому: {}", results.size(), searchWord);
        return results;
    }

    public void clearIndex() throws SQLException {
        String deleteContentsSQL = "DELETE FROM file_contents";
        String deleteFilesSQL = "DELETE FROM files";

        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(deleteContentsSQL);
            stmt.execute(deleteFilesSQL);
            logger.info("Индекс очищен");
        }
    }

    public long getIndexedFilesCount() throws SQLException {
        String sql = "SELECT COUNT(*) as file_count FROM files";

        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong("file_count");
            }
            return 0;
        }
    }

    private FileIndexEntry resultSetToFileEntry(ResultSet rs) throws SQLException {
        FileIndexEntry entry = new FileIndexEntry(
                rs.getString("file_path"),
                rs.getString("file_name"),
                rs.getLong("file_size"),
                rs.getLong("last_modified"),
                rs.getString("extension")
        );
        entry.setId(rs.getLong("id"));
        return entry;
    }
}



