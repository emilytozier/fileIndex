package ru.gildina.indexer.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TextAnalyzer {
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by"
    );

    public static List<String> extractWords(String text) {
        return Arrays.stream(text.split("\\W+"))
                .map(String::toLowerCase)
                .filter(word -> word.length() > 2)
                .filter(word -> !STOP_WORDS.contains(word))
                .collect(Collectors.toList());
    }

    public static boolean isRelevantWord(String word) {
        return word != null &&
                word.length() > 2 &&
                !STOP_WORDS.contains(word.toLowerCase()) &&
                word.matches("[a-zA-Zа-яА-Я0-9]+");
    }
}