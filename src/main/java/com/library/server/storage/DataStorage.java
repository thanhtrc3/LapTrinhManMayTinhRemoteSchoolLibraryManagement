package com.library.server.storage;

import com.library.common.model.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Lưu trữ dữ liệu bằng file nhị phân .dat thuần Java (không dùng database ngoài).
 */
public class DataStorage {
    private final Path dataDir;
    private final Path ebooksDir;

    public DataStorage() {
        this(Paths.get("data"));
    }

    public DataStorage(Path dataDir) {
        this.dataDir = dataDir;
        this.ebooksDir = dataDir.resolve("ebooks");
        initDirectoriesAndSeed();
    }

    public DataStorage(String dirPath) {
        this(Paths.get(dirPath));
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getEbooksDir() {
        return ebooksDir;
    }

    private void initDirectoriesAndSeed() {
        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(ebooksDir);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo thư mục lưu trữ: " + dataDir, e);
        }

        // Tự động nạp dữ liệu mẫu ban đầu nếu users.dat chưa tồn tại
        Path usersFile = dataDir.resolve("users.dat");
        if (!Files.exists(usersFile)) {
            seedInitialData();
        }
    }

    public synchronized void resetSeed() {
        try {
            // Xóa toàn bộ file .dat và file pdf cũ
            if (Files.exists(dataDir)) {
                try (var stream = Files.walk(dataDir)) {
                    stream.sorted((a, b) -> b.compareTo(a))
                            .filter(p -> !p.equals(dataDir))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
            Files.createDirectories(dataDir);
            Files.createDirectories(ebooksDir);
            seedInitialData();
        } catch (IOException e) {
            throw new IllegalStateException("Lỗi khi reset dữ liệu mẫu: " + e.getMessage(), e);
        }
    }

    private void seedInitialData() {
        saveUsers(seedUsers());
        saveBooks(seedBooks());
        saveCopies(seedCopies());
        saveBorrows(new ArrayList<>());
        saveReservations(new ArrayList<>());
        saveAuditLogs(new ArrayList<>());
        saveSearchHistory(new ArrayList<>());
        seedEbooks();
    }

    public synchronized List<User> loadUsers() {
        return load("users.dat");
    }

    public synchronized void saveUsers(List<User> users) {
        save("users.dat", users);
    }

    public synchronized List<Book> loadBooks() {
        return load("books.dat");
    }

    public synchronized void saveBooks(List<Book> books) {
        save("books.dat", books);
    }

    public synchronized List<BookCopy> loadCopies() {
        return load("copies.dat");
    }

    public synchronized void saveCopies(List<BookCopy> copies) {
        save("copies.dat", copies);
    }

    public synchronized List<BorrowRecord> loadBorrows() {
        return load("borrows.dat");
    }

    public synchronized void saveBorrows(List<BorrowRecord> borrows) {
        save("borrows.dat", borrows);
    }

    public synchronized List<ReservationRecord> loadReservations() {
        return load("reservations.dat");
    }

    public synchronized void saveReservations(List<ReservationRecord> reservations) {
        save("reservations.dat", reservations);
    }

    public synchronized List<AuditLog> loadAuditLogs() {
        return load("audit.dat");
    }

    public synchronized void saveAuditLogs(List<AuditLog> auditLogs) {
        save("audit.dat", auditLogs);
    }

    public synchronized List<SearchHistoryItem> loadSearchHistory() {
        return load("search_history.dat");
    }

    public synchronized void saveSearchHistory(List<SearchHistoryItem> items) {
        save("search_history.dat", items);
    }

    public byte[] getEbookBytes(int bookId, boolean siteLocal) {
        String fileName = siteLocal ? (bookId + "_full.pdf") : (bookId + "_preview.pdf");
        Path file = ebooksDir.resolve(fileName);
        if (!Files.exists(file)) {
            // Thử file dự phòng <id>.pdf nếu không có _full
            file = ebooksDir.resolve(bookId + ".pdf");
        }
        if (!Files.exists(file)) {
            return new byte[0];
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> load(String fileName) {
        Path file = dataDir.resolve(fileName);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof List) {
                return new ArrayList<>((List<T>) obj);
            }
            return new ArrayList<>();
        } catch (IOException | ClassNotFoundException e) {
            return new ArrayList<>();
        }
    }

    private <T> void save(String fileName, List<T> list) {
        Path file = dataDir.resolve(fileName);
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(file))) {
            oos.writeObject(new ArrayList<>(list));
        } catch (IOException e) {
            throw new IllegalStateException("Lỗi ghi dữ liệu ra file " + fileName + ": " + e.getMessage(), e);
        }
    }

    private static List<User> seedUsers() {
        List<User> list = new ArrayList<>();
        list.add(new User("admin", "admin123", "Thủ thư Trung tâm", Role.LIBRARIAN, "admin@library.edu.vn", 3, 0, false));
        list.add(new User("sv001", "123", "Nguyễn Văn An", Role.STUDENT, "sv001@student.edu.vn", 3, 0, false));
        list.add(new User("sv002", "123", "Trần Thị Bình", Role.STUDENT, "sv002@student.edu.vn", 3, 0, false));
        list.add(new User("sv003", "123", "Lê Minh Châu", Role.STUDENT, "sv003@student.edu.vn", 3, 0, false));
        return list;
    }

    private static List<Book> seedBooks() {
        List<Book> list = new ArrayList<>();
        list.add(new Book(1, "978-604-0-01", "Lập trình mạng máy tính", "Nguyễn Gia Hiếu", "Mạng máy tính", 2021, 3, 3, true, "1_full.pdf"));
        list.add(new Book(2, "978-604-0-02", "Cấu trúc dữ liệu và Giải thuật", "Đỗ Xuân Lôi", "Cấu trúc dữ liệu", 2020, 3, 3, true, "2_full.pdf"));
        list.add(new Book(3, "978-604-0-03", "Lập trình Java căn bản", "Phạm Quang Dũng", "Lập trình", 2022, 3, 3, true, "3_full.pdf"));
        list.add(new Book(4, "978-604-0-04", "Cơ sở dữ liệu nâng cao", "Lê Văn Phùng", "Cơ sở dữ liệu", 2019, 3, 3, true, "4_full.pdf"));
        list.add(new Book(5, "978-604-0-05", "Toán rời rạc ứng dụng", "Nguyễn Hữu Điển", "Toán học", 2018, 3, 3, true, "5_full.pdf"));
        return list;
    }

    private static List<BookCopy> seedCopies() {
        List<BookCopy> list = new ArrayList<>();
        String[] titles = {
                "Lập trình mạng máy tính",
                "Cấu trúc dữ liệu và Giải thuật",
                "Lập trình Java căn bản",
                "Cơ sở dữ liệu nâng cao",
                "Toán rời rạc ứng dụng"
        };
        for (int b = 1; b <= 5; b++) {
            String prefix = String.format("BK%02d", b);
            for (int c = 1; c <= 3; c++) {
                String copyId = String.format("%s-C%02d", prefix, c);
                String barcode = "BAR-" + copyId;
                String qrData = "QR:" + copyId + ":" + titles[b - 1];
                list.add(new BookCopy(copyId, b, barcode, qrData, CopyStatus.AVAILABLE));
            }
        }
        return list;
    }

    private void seedEbooks() {
        for (int i = 1; i <= 5; i++) {
            byte[] fullPdf = createSamplePdf("Tài liệu đầy đủ E-Book ID: " + i, true);
            byte[] previewPdf = createSamplePdf("Bản xem trước Preview E-Book ID: " + i, false);
            byte[] defaultPdf = createSamplePdf("Tài liệu chuẩn E-Book ID: " + i, true);

            try {
                Files.write(ebooksDir.resolve(i + "_full.pdf"), fullPdf);
                Files.write(ebooksDir.resolve(i + "_preview.pdf"), previewPdf);
                Files.write(ebooksDir.resolve(i + ".pdf"), defaultPdf);
            } catch (IOException e) {
                throw new IllegalStateException("Lỗi khi ghi file PDF mẫu cho sách " + i, e);
            }
        }
    }

    private static byte[] createSamplePdf(String title, boolean isFull) {
        String content = "%PDF-1.4\n" +
                "% Title: " + title + " (Full=" + isFull + ")\n" +
                "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n" +
                "3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<<>>>>endobj\n" +
                "xref\n0 4\n" +
                "0000000000 65535 f \n" +
                "0000000009 00000 n \n" +
                "0000000052 00000 n \n" +
                "0000000101 00000 n \n" +
                "trailer<</Size 4/Root 1 0 R>>\n" +
                "startxref\n178\n%%EOF\n";
        return content.getBytes(StandardCharsets.ISO_8859_1);
    }
}
