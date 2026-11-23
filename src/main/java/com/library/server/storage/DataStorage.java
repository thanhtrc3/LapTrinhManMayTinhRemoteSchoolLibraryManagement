package com.library.server.storage;

import com.library.common.model.Book;
import com.library.common.model.BorrowRecord;
import com.library.common.model.ReservationRecord;
import com.library.common.model.Role;
import com.library.common.model.User;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Lưu dữ liệu bằng file nhị phân .dat thuần Java (không dùng database). */
public class DataStorage {
    private final Path dir;

    public DataStorage(String dirPath) {
        this.dir = Paths.get(dirPath);
    }

    public List<User> loadUsers() {
        List<User> users = load("users.dat", DataStorage::seedUsers);
        // Bổ sung tài khoản mẫu mới nếu dữ liệu cũ chưa có (idempotent).
        boolean changed = false;
        for (User seed : seedUsers()) {
            if (users.stream().noneMatch(u -> u.getUsername().equals(seed.getUsername()))) {
                users.add(seed);
                changed = true;
            }
        }
        if (changed) {
            saveUsers(users);
        }
        return users;
    }

    public List<Book> loadBooks() {
        return load("books.dat", DataStorage::seedBooks);
    }

    public List<BorrowRecord> loadBorrows() {
        return load("borrows.dat", ArrayList::new);
    }

    public List<ReservationRecord> loadReservations() {
        return load("reservations.dat", ArrayList::new);
    }

    public void saveUsers(List<User> users) {
        save("users.dat", users);
    }

    public void saveBooks(List<Book> books) {
        save("books.dat", books);
    }

    public void saveBorrows(List<BorrowRecord> borrows) {
        save("borrows.dat", borrows);
    }

    public void saveReservations(List<ReservationRecord> reservations) {
        save("reservations.dat", reservations);
    }

    private <T> List<T> load(String fileName, java.util.function.Supplier<List<T>> seed) {
        Path file = dir.resolve(fileName);
        if (!Files.exists(file)) {
            List<T> fresh = seed.get();
            save(fileName, fresh);
            return fresh;
        }
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            @SuppressWarnings("unchecked")
            List<T> data = (List<T>) in.readObject();
            return data;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Không đọc được " + file + ": " + e.getMessage(), e);
        }
    }

    private void save(String fileName, List<?> data) {
        try {
            Files.createDirectories(dir);
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(dir.resolve(fileName)))) {
                out.writeObject(data);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không ghi được " + dir.resolve(fileName) + ": " + e.getMessage(), e);
        }
    }

    private static List<User> seedUsers() {
        List<User> users = new ArrayList<>();
        users.add(new User("admin", "admin123", "Thủ thư Trung tâm", Role.LIBRARIAN));
        users.add(new User("sv001", "123", "Nguyễn Văn An", Role.STUDENT));
        users.add(new User("sv002", "123", "Trần Thị Bình", Role.STUDENT));
        users.add(new User("sv003", "123", "Lê Minh Châu", Role.STUDENT));
        users.add(new User("sv004", "123", "Vũ Thị Hương", Role.STUDENT));
        users.add(new User("sv005", "123", "Phạm Minh Đức", Role.STUDENT));
        return users;
    }

    private static List<Book> seedBooks() {
        List<Book> books = new ArrayList<>();
        books.add(new Book(1, "Lập trình mạng máy tính", "Nguyễn Gia Hiếu", "Mạng máy tính", 2021, 4));
        books.add(new Book(2, "Giáo trình Cấu trúc dữ liệu và Giải thuật", "Đỗ Xuân Lôi", "Cấu trúc dữ liệu", 2019, 3));
        books.add(new Book(3, "Lập trình Java cơ bản", "Phạm Quang Dũng", "Lập trình", 2020, 5));
        books.add(new Book(4, "Cơ sở dữ liệu quan hệ", "Lê Văn Phùng", "Cơ sở dữ liệu", 2018, 1));
        books.add(new Book(5, "Toán rời rạc ứng dụng", "Nguyễn Hữu Điển", "Toán học", 2017, 3));
        return books;
    }
}
