package ru.gildina.indexer;

import ru.gildina.indexer.database.ConnectSQLLite;
import ru.gildina.indexer.database.DatabaseManager;
import ru.gildina.indexer.service.SearchService;

import java.util.HashMap;
import java.util.Map;

public class ApplicationContext {
    private static ApplicationContext instance;
    private final Map<Class<?>, Object> beans = new HashMap<>();

    private ApplicationContext() {
        initializeBeans();
    }

    public static synchronized ApplicationContext getInstance() {
        if (instance == null) {
            instance = new ApplicationContext();
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> beanClass) {
        return (T) beans.get(beanClass);
    }

    private void initializeBeans() {
        // Инициализируем бины в правильном порядке
        ConnectSQLLite connectSQLLite = ConnectSQLLite.getInstance();
        DatabaseManager databaseManager = new DatabaseManager();
        SearchService searchService = new SearchService(databaseManager);

        beans.put(ConnectSQLLite.class, connectSQLLite);
        beans.put(DatabaseManager.class, databaseManager);
        beans.put(SearchService.class, searchService);
    }
}
