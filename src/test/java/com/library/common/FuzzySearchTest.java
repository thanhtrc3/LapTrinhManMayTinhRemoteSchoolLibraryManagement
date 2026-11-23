package com.library.common;

import com.library.common.util.FuzzySearchUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FuzzySearchTest {

    @Test
    @DisplayName("Case 1: Chuẩn hoá chuỗi tiếng Việt có dấu, ký tự đ/Đ và gộp khoảng trắng")
    void testNormalizeDiacriticsAndSpaces() {
        assertEquals("lap trinh mang", FuzzySearchUtil.normalize("Lập Trình Mạng"));
        assertEquals("lap trinh mang da phuong tien",
                FuzzySearchUtil.normalize("   LẬP   Trình   MẠNG   Đa   Phương   Tiện   "));
        assertEquals("do xuan loi", FuzzySearchUtil.normalize("Đỗ Xuân Lôi"));
        assertEquals("", FuzzySearchUtil.normalize(null));
        assertEquals("", FuzzySearchUtil.normalize("   "));
    }

    @Test
    @DisplayName("Case 2: Khớp chính xác hoặc khớp chuỗi con không dấu")
    void testExactAndSubstringMatch() {
        assertTrue(FuzzySearchUtil.matches("lap trinh", "Lập trình mạng máy tính"));
        assertTrue(FuzzySearchUtil.matches("Mang", "Lập trình mạng"));
        assertTrue(FuzzySearchUtil.matches("cau truc du lieu", "Giáo trình Cấu trúc dữ liệu và Giải thuật"));
    }

    @Test
    @DisplayName("Case 3: Khớp với khoảng cách Levenshtein <= 2 (sai chính tả/gõ thiếu)")
    void testLevenshteinTypoMatch() {
        // "lap trinh mag" cách "lap trinh mang" khoảng cách Levenshtein = 1
        assertTrue(FuzzySearchUtil.matches("lap trinh mag", "Lập trình mạng"));
        // "jawa" cách "java" khoảng cách Levenshtein = 1
        assertTrue(FuzzySearchUtil.matches("jawa", "Lập trình Java cơ bản"));
    }

    @Test
    @DisplayName("Case 4: Khớp theo tiền tố từ (Prefix matching)")
    void testPrefixMatch() {
        assertTrue(FuzzySearchUtil.matches("lap tri", "Lập trình mạng"));
        assertTrue(FuzzySearchUtil.matches("toan", "Toán rời rạc ứng dụng"));
        assertTrue(FuzzySearchUtil.matches("giai th", "Cấu trúc dữ liệu và Giải thuật"));
    }

    @Test
    @DisplayName("Case 5: Không khớp khi nội dung hoàn toàn khác biệt")
    void testNegativeMatch() {
        assertFalse(FuzzySearchUtil.matches("toan roi rac", "Lập trình mạng"));
        assertFalse(FuzzySearchUtil.matches("vat ly dai cuong", "Lập trình Java"));
        assertFalse(FuzzySearchUtil.matches("tri tue nhan tao", "Cơ sở dữ liệu quan hệ"));
        // Không nhận nhầm từ 1-2 ký tự ("va", "co", "so") thành khớp
        assertFalse(FuzzySearchUtil.matches("vat ly dai cuong", "Cấu trúc dữ liệu và Giải thuật"));
        assertFalse(FuzzySearchUtil.matches("sql", "Cơ sở dữ liệu"));
        assertFalse(FuzzySearchUtil.matches("ai", "Cơ sở dữ liệu"));
        assertFalse(FuzzySearchUtil.matches("ml", "Cơ sở dữ liệu và Giải thuật"));
    }

    @Test
    @DisplayName("Case 6: Tìm kiếm trên nhiều trường (tựa sách, tác giả, thể loại) và xử lý keyword rỗng")
    void testMultipleFieldsAndEdgeCases() {
        String title = "Lập trình mạng máy tính";
        String author = "Nguyễn Gia Hiếu";
        String category = "Mạng máy tính";

        // Khớp tác giả
        assertTrue(FuzzySearchUtil.matches("gia hieu", title, author, category));
        // Khớp thể loại
        assertTrue(FuzzySearchUtil.matches("mang may tinh", title, author, category));
        // Keyword rỗng hoặc null khớp mọi sách
        assertTrue(FuzzySearchUtil.matches("", title, author, category));
        assertTrue(FuzzySearchUtil.matches(null, title, author, category));
        // Không khớp trường nào
        assertFalse(FuzzySearchUtil.matches("kinh te", title, author, category));
    }
}
