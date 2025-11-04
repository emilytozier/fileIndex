package ru.gildina.indexer.util;



import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtils {

    /**
     * Нормализует путь, исправляя слеши и проверяя валидность
     */
    public static String normalizePath(String inputPath) {
        if (inputPath == null || inputPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Путь не может быть пустым");
        }

        String normalized = inputPath.trim();

        // Заменяем обратные слеши на прямые для единообразия
        normalized = normalized.replace('\\', '/');

        // Убираем лишние двойные слеши
        normalized = normalized.replace("//", "/");

        // Для Windows путей типа "C:" добавляем слеш
        if (normalized.length() == 2 && normalized.charAt(1) == ':') {
            normalized += "/";
        }

        return normalized;
    }

    /**
     * Проверяет валидность пути и возвращает нормализованный Path
     */
    public static Path validateAndGetPath(String inputPath) throws InvalidPathException {
        String normalizedPath = normalizePath(inputPath);

        try {
            Path path = Paths.get(normalizedPath);
            return path.toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            // Создаем понятное сообщение об ошибке
            String message = createUserFriendlyErrorMessage(inputPath, e);
            throw new InvalidPathException(inputPath, message);
        }
    }

    /**
     * Создает понятное пользователю сообщение об ошибке
     */
    private static String createUserFriendlyErrorMessage(String invalidPath, InvalidPathException originalException) {
        StringBuilder sb = new StringBuilder();
        sb.append("Некорректный путь: '").append(invalidPath).append("'\n");

        // Анализируем common ошибки
        if (invalidPath.contains("\\") && !invalidPath.contains("/")) {
            sb.append("• Обнаружены обратные слеши '\\'. Рекомендуется использовать прямые слеши '/'\n");
            sb.append("• Попробуйте: ").append(invalidPath.replace('\\', '/')).append("\n");
        }

        if (invalidPath.contains("//")) {
            sb.append("• Обнаружены двойные слеши. Уберите лишние слеши\n");
        }

        if (invalidPath.endsWith("\\") || invalidPath.endsWith("/")) {
            sb.append("• Путь заканчивается слешем. Уберите слеш в конце\n");
        }

        if (invalidPath.matches(".*[<>:\"|?*].*")) {
            sb.append("• Путь содержит недопустимые символы: < > : \" | ? *\n");
        }

        sb.append("• Пример правильного формата: D:/мои книги/документы\n");
        sb.append("• Или используйте: D:\\мои книги\\документы (с двойными обратными слешами)");

        return sb.toString();
    }

    /**
     * Проверяет существование директории с понятными сообщениями
     */
    public static void validateDirectory(String inputPath) throws IOException {
        Path path = validateAndGetPath(inputPath);

        if (!java.nio.file.Files.exists(path)) {
            throw new IOException("Директория не существует: " + path +
                    "\nПроверьте правильность пути и существование директории");
        }

        if (!java.nio.file.Files.isDirectory(path)) {
            throw new IOException("Указанный путь не является директорией: " + path +
                    "\nЭто файл, а не папка");
        }

        if (!java.nio.file.Files.isReadable(path)) {
            throw new IOException("Нет прав на чтение директории: " + path +
                    "\nПроверьте права доступа");
        }
    }

    /**
     * Предлагает исправления для пути
     */
    public static String suggestPathCorrection(String invalidPath) {
        if (invalidPath == null) return "";

        String suggestion = invalidPath;

        // Заменяем обратные слеши
        suggestion = suggestion.replace('\\', '/');

        // Убираем слеш в конце если есть
        if (suggestion.endsWith("/")) {
            suggestion = suggestion.substring(0, suggestion.length() - 1);
        }

        // Убираем двойные слеши
        suggestion = suggestion.replace("//", "/");

        return suggestion;
    }
}