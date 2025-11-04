package ru.gildina.indexer.cli;

import java.util.HashMap;
import java.util.Map;

public class CommandLineParser {
    public static Map<String, String> parseArguments(String[] args) {
        Map<String, String> arguments = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--index":
                    if (i + 1 < args.length) {
                        arguments.put("index", args[++i]);
                    }
                    break;
                case "--search-name":
                    if (i + 1 < args.length) {
                        arguments.put("search-name", args[++i]);
                    }
                    break;
                case "--search-content":
                    if (i + 1 < args.length) {
                        arguments.put("search-content", args[++i]);
                    }
                    break;
                case "--clear":
                    arguments.put("clear", "true");
                    break;
                case "--help":
                    arguments.put("help", "true");
                    break;
            }
        }

        return arguments;
    }

    public static void printHelp() {
        System.out.println("File Indexer - Usage:");
        System.out.println("  --index <path>          Index directory");
        System.out.println("  --search-name <query>   Search by file name");
        System.out.println("  --search-content <query> Search by content");
        System.out.println("  --clear                 Clear index");
        System.out.println("  --help                  Show this help");
    }
}