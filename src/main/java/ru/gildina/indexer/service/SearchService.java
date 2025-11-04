
package ru.gildina.indexer.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gildina.indexer.database.DatabaseManager;
import ru.gildina.indexer.model.FileIndexEntry;

import java.sql.SQLException;
import java.util.List;

public class SearchService {
    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private final DatabaseManager databaseManager;

    public SearchService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<FileIndexEntry> search(String query, SearchType searchType) {
        try {
            switch (searchType) {
                case FILE_NAME:
                    return databaseManager.searchByFileName(query);
                case CONTENT:
                    return databaseManager.searchByContent(query);
                default:
                    throw new IllegalArgumentException("Неизвестный тип поиска: " + searchType);
            }
        } catch (SQLException e) {
            logger.error("Ошибка при поиске: {}", e.getMessage());
            throw new RuntimeException("Ошибка поиска в базе данных", e);
        }
    }
    /**
     * Загружает слова для файла из базы данных
     */
    public void loadWordsForFile(FileIndexEntry entry) {
        try {
            databaseManager.loadWordsForFile(entry);
        } catch (SQLException e) {
            logger.error("Ошибка при загрузке слов для файла: {}", e.getMessage());
            throw new RuntimeException("Ошибка загрузки слов", e);
        }
    }
    public void printSearchResultsWithTime(List<FileIndexEntry> results, String query, SearchType searchType) {
        if (results.isEmpty()) {
            System.out.println("По запросу '" + query + "' ничего не найдено.");
            return;
        }

        System.out.println("\n=== Результаты поиска: '" + query + "' ===");
        System.out.println("Найдено файлов: " + results.size());
        System.out.println("----------------------------------------");

        for (int i = 0; i < results.size(); i++) {
            FileIndexEntry entry = results.get(i);
            System.out.printf("%d. %s\n", i + 1, entry.getFileName());
            System.out.printf("   Путь: %s\n", entry.getPath());
            System.out.printf("   Размер: %,d байт\n", entry.getSize());
            System.out.printf("   Изменен: %s\n", entry.getShortLastModified());
            System.out.printf("   Расширение: %s\n", entry.getExtension());

            // Для поиска по содержимому
            if (searchType == SearchType.CONTENT && entry.containsWord(query.toLowerCase())) {
                int count = entry.getWordCount(query.toLowerCase());
                System.out.printf("   Релевантность: найдено %d совпадений\n", count);
            }
            System.out.println();
        }
    }
    public List<FileIndexEntry> searchByPartialPath(String partialPath) {
        try {
            List<FileIndexEntry> results = databaseManager.searchByPartialPath(partialPath);
            // Загружаем слова для найденных файлов
            for (FileIndexEntry entry : results) {
                databaseManager.loadWordsForFile(entry);
            }
            return results;
        } catch (SQLException e) {
            logger.error("Ошибка при поиске по пути: {}", e.getMessage());
            throw new RuntimeException("Ошибка поиска в базе данных", e);
        }
    }
    public FileIndexEntry findFileByPath(String filePath) {
        try {
            FileIndexEntry file = databaseManager.findFileByPath(filePath);
            if (file != null) {
                databaseManager.loadWordsForFile(file);
            }
            return file;
        } catch (SQLException e) {
            logger.error("Ошибка при поиске файла по пути: {}", e.getMessage());
            throw new RuntimeException("Ошибка поиска файла", e);
        }
    }

    public List<FileIndexEntry> searchByExactNormalizedPath(String filePath) {
        try {
            List<FileIndexEntry> results = databaseManager.searchByExactNormalizedPath(filePath);
            // Загружаем слова для найденных файлов
            for (FileIndexEntry entry : results) {
                databaseManager.loadWordsForFile(entry);
            }
            return results;
        } catch (SQLException e) {
            logger.error("Ошибка при точном поиске по пути: {}", e.getMessage());
            throw new RuntimeException("Ошибка поиска в базе данных", e);
        }
    }

    public List<FileIndexEntry> searchByFileNamePartial(String fileName) {
        try {
            return databaseManager.searchByFileNamePartial(fileName);
        } catch (SQLException e) {
            logger.error("Ошибка при поиске по имени: {}", e.getMessage());
            throw new RuntimeException("Ошибка поиска в базе данных", e);
        }
    }


    public enum SearchType {
        FILE_NAME,
        CONTENT
    }
}


