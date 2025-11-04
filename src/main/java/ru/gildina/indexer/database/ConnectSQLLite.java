package ru.gildina.indexer.database;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectSQLLite {
    private static final String DB_URL = "jdbc:sqlite:file_indexer.db";
    private static ConnectSQLLite instance;
    private Connection connection;

    // Паттерн Singleton для гарантии одного экземпляра менеджера
    private ConnectSQLLite() {
        initializeDatabase();
    }

    public static synchronized ConnectSQLLite getInstance() {
        if (instance == null) {
            instance = new ConnectSQLLite();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
        }
        return connection;
    }

    private void initializeDatabase() {
        // SQL для создания таблиц
        String createFilesTableSQL = """
                    CREATE TABLE IF NOT EXISTS files (
                        id INTEGER PRIMARY KEY, 
                        file_path TEXT NOT NULL UNIQUE,
                        file_name TEXT NOT NULL,
                        file_size INTEGER NOT NULL,
                        last_modified INTEGER NOT NULL,
                        extension TEXT NOT NULL
                    );
                """;

        String createFileContentsTableSQL = """
                    CREATE TABLE IF NOT EXISTS file_contents (
                        id INTEGER PRIMARY KEY,
                        file_id INTEGER NOT NULL,
                        word TEXT NOT NULL,
                        word_count INTEGER NOT NULL,
                        FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE
                    );
                """;

        // Создаем индексы для ускорения поиска
        String createIndexesSQL = """
                    CREATE INDEX IF NOT EXISTS idx_files_path ON files(file_path);
                    CREATE INDEX IF NOT EXISTS idx_contents_word ON file_contents(word);
                    CREATE INDEX IF NOT EXISTS idx_contents_file ON file_contents(file_id);
                """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // Выполняем SQL для создания таблиц
            stmt.execute(createFilesTableSQL);
            stmt.execute(createFileContentsTableSQL);
            stmt.execute(createIndexesSQL);

            System.out.println("База данных инициализирована успешно.");

        } catch (SQLException e) {
            System.err.println("Ошибка при инициализации базы данных: " + e.getMessage());
        }
    }
}

