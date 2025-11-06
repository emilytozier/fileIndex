package ru.gildina.indexer.model;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FileIndexEntry {
    private Long id;
    private String path;
    private String fileName;
    private long size;
    private long lastModifiedTime;
    private String extension;
    private Map<String, Integer> wordCounts = new HashMap<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLastModifiedTime() {
        return lastModifiedTime;
    }

    public void setLastModifiedTime(long lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public Map<String, Integer> getWordCounts() {
        return wordCounts;
    }

    public void setWordCounts(Map<String, Integer> wordCounts) {
        this.wordCounts = wordCounts;
    }

    public FileIndexEntry(String filePath, String fileName, long fileSize,
                          long lastModified, String extension) {
        this.path = filePath;
        this.fileName = fileName;
        this.size = fileSize;
        this.lastModifiedTime = lastModified;
        this.extension = extension;
    }
    public String getFormattedLastModified() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(lastModifiedTime));
    }

    /**
     * Метод для отладки - проверяет состояние объекта
     */
    public void debugInfo() {
        System.out.println("FileIndexEntry Debug:");
        System.out.println("   File: " + fileName);
        System.out.println("   Path: " + path);
        System.out.println("   WordCounts reference: " + wordCounts);
        System.out.println("   WordCounts is null: " + (wordCounts == null));
        if (wordCounts != null) {
            System.out.println("   WordCounts size: " + wordCounts.size());
            System.out.println("   Total words: " + getTotalWords());
        }
    }

    /**
     * Возвращает отформатированную дату в коротком формате
     */
    public String getShortLastModified() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new Date(lastModifiedTime));
    }

    /**
     * Возвращает относительное время (например, "2 дня назад")
     */
    public String getRelativeLastModified() {
        long now = System.currentTimeMillis();
        long diff = now - lastModifiedTime;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " дней назад";
        } else if (hours > 0) {
            return hours + " часов назад";
        } else if (minutes > 0) {
            return minutes + " минут назад";
        } else {
            return "только что";
        }
    }

    public FileIndexEntry(Path filePath, BasicFileAttributes attrs) {
        this.path = filePath.toString();
        this.fileName = filePath.getFileName().toString();
        this.size = attrs.size();
        this.lastModifiedTime = attrs.lastModifiedTime().toMillis();

        String filename = filePath.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        //extract extension, including hidden files
        this.extension = (dotIndex > 0) ? filename.substring(dotIndex + 1) : "";
    }

    public void addWord(String word) {
        addWord(word, 1);
    }

    public void addWord(String word, int count) {
        word = word.toLowerCase();
        // Ограничиваем количество уникальных слов на файл (предотвращаем переполнение)
        if (wordCounts.size() < 100000) { // Максимум 100,000 уникальных слов на файл
            wordCounts.put(word, wordCounts.getOrDefault(word, 0) + count);
        }
        wordCounts.put(word, wordCounts.getOrDefault(word, 0) + count);
    }

    public int getWordCount(String word) {
        return wordCounts.getOrDefault(word.toLowerCase(), 0);
    }
    public boolean containsWord(String word) {
        return wordCounts.containsKey(word.toLowerCase());
    }
    public int getTotalWords() {
        return wordCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getUniqueWords() {
        return wordCounts.size();
    }

    public String getDirectory() {
        if (path == null) return "";
        int lastSeparator = path.lastIndexOf('/');
        if (lastSeparator == -1) lastSeparator = path.lastIndexOf('\\');
        return (lastSeparator > 0) ? path.substring(0, lastSeparator) : "";
    }

    // equals и hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileIndexEntry that = (FileIndexEntry) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    // toString
    @Override
    public String toString() {
        return "FileIndexEntry{" +
                "filePath='" + path + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + size +
                ", words=" + getTotalWords() +
                ", uniqueWords=" + getUniqueWords() +
                '}';
    }


}
