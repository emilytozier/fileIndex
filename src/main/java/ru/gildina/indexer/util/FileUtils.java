package ru.gildina.indexer.util;

import java.nio.file.Path;
import java.util.Set;

public class FileUtils {
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "java", "xml", "json", "csv", "md", "properties",
            "html", "htm", "css", "js", "py", "cpp", "c", "h",
            "sql", "log", "cfg", "conf", "ini", "docx", "pdf", "rtf",
            "doc", "odt", "epub", "fb2"
    );

    public static boolean isTextFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = fileName.substring(dotIndex + 1);
            return TEXT_EXTENSIONS.contains(extension);
        }
        return false;
    }

    public static String getFileExtension(Path file) {
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex > 0) ? fileName.substring(dotIndex + 1) : "";
    }
}