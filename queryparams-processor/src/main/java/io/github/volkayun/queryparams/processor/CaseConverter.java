package io.github.volkayun.queryparams.processor;

import io.github.volkayun.queryparams.annotations.constant.Case;

import java.util.ArrayList;
import java.util.List;

/**
 * 케이스 컨버터
 */
final class CaseConverter {
    static String convertCase(String name, Case targetCase) {
        List<String> words = splitIntoWords(name);

        return switch (targetCase) {
            case CAMEL -> toCamel(words);
            case PASCAL -> toPascal(words);
            case SNAKE -> String.join("_", words);
            case KEBAB -> String.join("-", words);
            case UPPER_SNAKE -> String.join("_", words).toUpperCase();
            case UPPER_KEBAB -> String.join("-", words).toUpperCase();
        };
    }

    private static List<String> splitIntoWords(String name) {
        if (name == null || name.isEmpty()) {
            return List.of();
        }

        // snake
        if (name.contains("_")) {
            String[] arr = name.split("_");
            List<String> list = new ArrayList<>(arr.length);
            for (String s : arr) {
                if (!s.isEmpty()) list.add(s.toLowerCase());
            }
            return list;
        }

        // kebab
        if (name.contains("-")) {
            String[] arr = name.split("-");
            List<String> list = new ArrayList<>(arr.length);
            for (String s : arr) {
                if (!s.isEmpty()) list.add(s.toLowerCase());
            }
            return list;
        }

        // camel / Pascal
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char[] chars = name.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (i > 0 && Character.isUpperCase(c) && !current.isEmpty()) {
                // 새 토큰 경계
                result.add(current.toString().toLowerCase());
                current.setLength(0);
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            result.add(current.toString().toLowerCase());
        }
        return result;
    }

    private static String toCamel(List<String> words) {
        if (words.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(words.get(0));
        for (int i = 1; i < words.size(); i++) {
            sb.append(cap(words.get(i)));
        }
        return sb.toString();
    }

    private static String toPascal(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(cap(w));
        }
        return sb.toString();
    }

    private static String cap(String w) {
        if (w == null || w.isEmpty()) return "";
        if (w.length() == 1) return w.toUpperCase();
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }
}
