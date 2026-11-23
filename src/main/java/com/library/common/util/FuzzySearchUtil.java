package com.library.common.util;

import java.text.Normalizer;
import java.util.Locale;

public class FuzzySearchUtil {

    private FuzzySearchUtil() {
    }

    /**
     * Chuẩn hoá chuỗi:
     * - Dùng java.text.Normalizer Form.NFD
     * - replaceAll("\\p{InCombiningDiacriticalMarks}+","")
     * - đ/Đ → d
     * - lowercase
     * - gộp khoảng trắng (trim + replaceAll("\\s+", " "))
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.replace('đ', 'd').replace('Đ', 'd');
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = normalized.trim().replaceAll("\\s+", " ");
        return normalized;
    }

    /**
     * Thuật toán Levenshtein tính khoảng cách chỉnh sửa giữa 2 chuỗi.
     */
    public static int levenshtein(String s1, String s2) {
        if (s1 == null && s2 == null) {
            return 0;
        }
        if (s1 == null) {
            return s2.length();
        }
        if (s2 == null) {
            return s1.length();
        }

        int len1 = s1.length();
        int len2 = s2.length();

        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            System.arraycopy(curr, 0, prev, 0, len2 + 1);
        }

        return prev[len2];
    }

    /**
     * So khớp mờ:
     * Chuẩn hoá keyword và từng field, trả về true nếu:
     * - keyword rỗng: khớp tất cả
     * - 1 field chứa keyword
     * - tiền tố khớp (field bắt đầu bằng keyword hoặc có từ trong field bắt đầu bằng keyword)
     * - Levenshtein(keyword, field) <= 2
     * - Levenshtein(keyword, từ bất kỳ trong field) <= 2
     * - Hoặc Levenshtein(keyword, cụm từ con cùng số từ trong field) <= 2 (sliding window)
     */
    public static boolean matches(String keyword, String... fields) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        String normKw = normalize(keyword);
        if (normKw.isEmpty()) {
            return true;
        }

        if (fields == null || fields.length == 0) {
            return false;
        }

        String[] kwWords = normKw.split(" ");
        int kwWordCount = kwWords.length;

        for (String field : fields) {
            if (field == null) {
                continue;
            }
            String normField = normalize(field);
            if (normField.isEmpty()) {
                continue;
            }

            // 1. Chứa keyword hoặc tiền tố khớp toàn chuỗi
            if (normField.contains(normKw) || normField.startsWith(normKw)) {
                return true;
            }

            // 2. Toàn bộ field so với keyword có khoảng cách Levenshtein <= 2
            if (levenshtein(normKw, normField) <= 2) {
                return true;
            }

            String[] fieldWords = normField.split(" ");

            // 3. Tiền tố khớp hoặc Levenshtein <= 2 với từng từ trong field
            for (String word : fieldWords) {
                if (word.startsWith(normKw) || normKw.startsWith(word)) {
                    return true;
                }
                if (levenshtein(normKw, word) <= 2) {
                    return true;
                }
            }

            // 4. Nếu keyword gồm nhiều từ, kiểm tra sliding window có cùng số từ trong field
            if (kwWordCount > 1 && fieldWords.length >= kwWordCount) {
                for (int i = 0; i <= fieldWords.length - kwWordCount; i++) {
                    StringBuilder window = new StringBuilder();
                    for (int j = 0; j < kwWordCount; j++) {
                        if (j > 0) window.append(" ");
                        window.append(fieldWords[i + j]);
                    }
                    String windowStr = window.toString();
                    if (levenshtein(normKw, windowStr) <= 2) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
