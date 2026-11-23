package com.library.server.service;

import com.library.common.model.Book;
import com.library.common.model.BorrowRecord;
import com.library.common.model.BorrowStatus;
import com.library.common.model.ReservationRecord;
import com.library.common.model.ReservationStatus;
import com.library.common.model.Role;
import com.library.common.model.User;
import com.library.server.storage.DataStorage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Toàn bộ nghiệp vụ thư viện. Các phương thức synchronized: một khóa chung cho cả
 * server là đủ với quy mô đồ án; nâng cấp lên ReentrantLock theo từng sách khi cần
 * nhiều sinh viên mượn song song thật sự.
 */
public class LibraryService {
    public static final int MAX_BORROW = 3;
    /** Số ngày giữ sách cho người đặt trước kể từ lúc sách về. */
    public static final int HOLD_DAYS = 2;
    private static final int DEFAULT_DAYS = 14;

    private final DataStorage storage;
    private final List<User> users;
    private final List<Book> books;
    private final List<BorrowRecord> borrows;
    private final List<ReservationRecord> reservations;
    private int nextBookId;
    private long nextBorrowId;
    private long nextReservationId;

    public LibraryService(DataStorage storage) {
        this.storage = storage;
        this.users = storage.loadUsers();
        this.books = storage.loadBooks();
        this.borrows = storage.loadBorrows();
        this.reservations = storage.loadReservations();
        this.nextBookId = books.stream().mapToInt(Book::getId).max().orElse(0) + 1;
        this.nextBorrowId = borrows.stream().mapToLong(BorrowRecord::getId).max().orElse(0) + 1;
        this.nextReservationId = reservations.stream().mapToLong(ReservationRecord::getId).max().orElse(0) + 1;
    }

    public synchronized User login(String username, String password) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username) && u.getPassword().equals(password))
                .findFirst()
                .orElseThrow(() -> new ServiceException("Sai tên đăng nhập hoặc mật khẩu."));
    }

    public synchronized void requireLibrarian(String username) {
        if (findUser(username).getRole() != Role.LIBRARIAN) {
            throw new ServiceException("Chỉ thủ thư mới được thực hiện thao tác này.");
        }
    }

    public synchronized List<Book> getBooks() {
        refreshReservations(LocalDate.now());
        for (Book book : books) {
            book.setHeldCopies((int) reservations.stream()
                    .filter(r -> r.getBookId() == book.getId() && r.getStatus() == ReservationStatus.READY)
                    .count());
        }
        return new ArrayList<>(books);
    }

    public synchronized BorrowRecord borrow(String username, int bookId, int days) {
        User user = findUser(username);
        if (user.getRole() != Role.STUDENT) {
            throw new ServiceException("Tài khoản thủ thư không mượn sách.");
        }
        Book book = findBook(bookId);
        LocalDate today = LocalDate.now();
        refreshOverdue(today);
        refreshReservations(today);

        long active = borrows.stream()
                .filter(b -> b.getStudentId().equals(username) && b.getStatus() != BorrowStatus.RETURNED)
                .count();
        if (active >= MAX_BORROW) {
            throw new ServiceException("Bạn đã mượn tối đa " + MAX_BORROW + " cuốn. Hãy trả bớt trước.");
        }
        boolean hasOverdue = borrows.stream()
                .anyMatch(b -> b.getStudentId().equals(username) && b.getStatus() == BorrowStatus.OVERDUE);
        if (hasOverdue) {
            throw new ServiceException("Bạn đang có sách quá hạn chưa trả, không thể mượn thêm.");
        }
        boolean alreadyBorrowed = borrows.stream()
                .anyMatch(b -> b.getStudentId().equals(username)
                        && b.getBookId() == bookId
                        && b.getStatus() != BorrowStatus.RETURNED);
        if (alreadyBorrowed) {
            throw new ServiceException("Bạn đang mượn cuốn sách này rồi.");
        }

        // Sách đang được giữ riêng cho chính người này thì lấy bản giữ đó.
        ReservationRecord ready = reservations.stream()
                .filter(r -> r.getBookId() == bookId
                        && r.getStudentId().equals(username)
                        && r.getStatus() == ReservationStatus.READY)
                .findFirst()
                .orElse(null);
        if (ready == null && book.getAvailableCopies() <= 0) {
            throw new ServiceException("Sách đã hết bản sao khả dụng. Bạn có thể bấm \"Đặt trước\" để xếp hàng.");
        }

        int borrowDays = (days < 1 || days > 30) ? DEFAULT_DAYS : days;
        BorrowRecord record = new BorrowRecord(nextBorrowId++, book.getId(), book.getTitle(),
                username, today, today.plusDays(borrowDays));
        borrows.add(record);
        if (ready != null) {
            ready.setStatus(ReservationStatus.FULFILLED);
        } else {
            book.setAvailableCopies(book.getAvailableCopies() - 1);
        }
        saveAll();
        return record;
    }

    public synchronized BorrowRecord returnBook(long recordId, String username) {
        BorrowRecord record = findBorrow(recordId);
        if (!record.getStudentId().equals(username)) {
            throw new ServiceException("Đây không phải phiếu mượn của bạn.");
        }
        if (record.getStatus() == BorrowStatus.RETURNED) {
            throw new ServiceException("Phiếu mượn này đã được trả rồi.");
        }
        LocalDate today = LocalDate.now();
        refreshReservations(today);
        record.setStatus(BorrowStatus.RETURNED);
        record.setReturnDate(today);
        // Bản sao vừa trả: nhường cho người đặt trước đầu tiên, không thì trả về kho.
        releaseCopy(record.getBookId(), today);
        saveAll();
        return record;
    }

    public synchronized List<BorrowRecord> myBorrows(String username) {
        refreshOverdue(LocalDate.now());
        List<BorrowRecord> result = new ArrayList<>();
        for (BorrowRecord b : borrows) {
            if (b.getStudentId().equals(username)) {
                result.add(b);
            }
        }
        result.sort(Comparator.comparingLong(BorrowRecord::getId).reversed());
        return result;
    }

    public synchronized List<BorrowRecord> allBorrows() {
        refreshOverdue(LocalDate.now());
        List<BorrowRecord> result = new ArrayList<>(borrows);
        result.sort(Comparator.comparingLong(BorrowRecord::getId).reversed());
        return result;
    }

    public synchronized ReservationRecord reserve(String username, int bookId) {
        User user = findUser(username);
        if (user.getRole() != Role.STUDENT) {
            throw new ServiceException("Tài khoản thủ thư không đặt trước sách.");
        }
        Book book = findBook(bookId);
        LocalDate today = LocalDate.now();
        refreshReservations(today);
        if (book.getAvailableCopies() > 0) {
            throw new ServiceException("Sách còn bản sao khả dụng, hãy mượn trực tiếp.");
        }
        boolean activeReservation = reservations.stream()
                .anyMatch(r -> r.getBookId() == bookId
                        && r.getStudentId().equals(username)
                        && (r.getStatus() == ReservationStatus.PENDING || r.getStatus() == ReservationStatus.READY));
        if (activeReservation) {
            throw new ServiceException("Bạn đã đặt trước cuốn này rồi.");
        }
        boolean borrowing = borrows.stream()
                .anyMatch(b -> b.getBookId() == bookId
                        && b.getStudentId().equals(username)
                        && b.getStatus() != BorrowStatus.RETURNED);
        if (borrowing) {
            throw new ServiceException("Bạn đang mượn cuốn sách này rồi.");
        }
        ReservationRecord reservation = new ReservationRecord(nextReservationId++, book.getId(),
                book.getTitle(), username, today);
        reservations.add(reservation);
        saveAll();
        return reservation;
    }

    public synchronized void cancelReservation(long reservationId, String username) {
        ReservationRecord reservation = findReservation(reservationId);
        if (!reservation.getStudentId().equals(username)) {
            throw new ServiceException("Đây không phải phiếu đặt trước của bạn.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.READY) {
            throw new ServiceException("Phiếu đặt trước này không còn hiệu lực.");
        }
        boolean wasHoldingCopy = reservation.getStatus() == ReservationStatus.READY;
        reservation.setStatus(ReservationStatus.CANCELLED);
        if (wasHoldingCopy) {
            releaseCopy(reservation.getBookId(), LocalDate.now());
        }
        saveAll();
    }

    public synchronized List<ReservationRecord> myReservations(String username) {
        refreshReservations(LocalDate.now());
        List<ReservationRecord> result = new ArrayList<>();
        for (ReservationRecord r : reservations) {
            if (r.getStudentId().equals(username)) {
                result.add(r);
            }
        }
        result.sort(Comparator.comparingLong(ReservationRecord::getId).reversed());
        return result;
    }

    public synchronized Book addBook(String title, String author, String category, int year, int copies) {
        if (title == null || title.isBlank()) {
            throw new ServiceException("Tựa sách không được để trống.");
        }
        if (copies < 1) {
            throw new ServiceException("Số bản sao phải từ 1 trở lên.");
        }
        Book book = new Book(nextBookId++, title.trim(), author.trim(), category.trim(), year, copies);
        books.add(book);
        saveAll();
        return book;
    }

    public synchronized Book updateBook(int bookId, String title, String author, String category, int year, int totalCopies) {
        Book book = findBook(bookId);
        if (title == null || title.isBlank()) {
            throw new ServiceException("Tựa sách không được để trống.");
        }
        long borrowed = borrows.stream()
                .filter(b -> b.getBookId() == bookId && b.getStatus() != BorrowStatus.RETURNED)
                .count();
        long held = reservations.stream()
                .filter(r -> r.getBookId() == bookId && r.getStatus() == ReservationStatus.READY)
                .count();
        if (totalCopies < borrowed + held) {
            throw new ServiceException("Không thể giảm còn " + totalCopies + " bản vì đang có "
                    + borrowed + " bản được mượn và " + held + " bản đang giữ cho người đặt trước.");
        }
        int delta = totalCopies - book.getTotalCopies();
        book.setTitle(title.trim());
        book.setAuthor(author.trim());
        book.setCategory(category.trim());
        book.setPublishYear(year);
        book.setTotalCopies(totalCopies);
        book.setAvailableCopies(book.getAvailableCopies() + delta);
        saveAll();
        return book;
    }

    public synchronized void deleteBook(int bookId) {
        Book book = findBook(bookId);
        boolean borrowed = borrows.stream()
                .anyMatch(b -> b.getBookId() == bookId && b.getStatus() != BorrowStatus.RETURNED);
        if (borrowed) {
            throw new ServiceException("Không thể xóa: vẫn còn bản sao đang được mượn.");
        }
        boolean reserved = reservations.stream()
                .anyMatch(r -> r.getBookId() == bookId
                        && (r.getStatus() == ReservationStatus.PENDING || r.getStatus() == ReservationStatus.READY));
        if (reserved) {
            throw new ServiceException("Không thể xóa: vẫn còn người đặt trước cuốn này.");
        }
        books.remove(book);
        saveAll();
    }

    private void refreshOverdue(LocalDate today) {
        for (BorrowRecord b : borrows) {
            if (b.getStatus() == BorrowStatus.BORROWING && b.getDueDate().isBefore(today)) {
                b.setStatus(BorrowStatus.OVERDUE);
            }
        }
    }

    /** Hết hạn giữ sách thì nhường bản sao cho người kế tiếp hoặc trả về kho. */
    private void refreshReservations(LocalDate today) {
        for (ReservationRecord r : new ArrayList<>(reservations)) {
            if (r.getStatus() == ReservationStatus.READY
                    && r.getExpiryDate() != null
                    && r.getExpiryDate().isBefore(today.atStartOfDay())) {
                r.setStatus(ReservationStatus.EXPIRED);
                releaseCopy(r.getBookId(), today);
            }
        }
    }

    private void releaseCopy(int bookId, LocalDate today) {
        Book book = findBook(bookId);
        ReservationRecord next = reservations.stream()
                .filter(r -> r.getBookId() == bookId && r.getStatus() == ReservationStatus.PENDING)
                .min(Comparator.comparing(ReservationRecord::getReservationDate)
                        .thenComparingLong(ReservationRecord::getId))
                .orElse(null);
        if (next != null) {
            next.setStatus(ReservationStatus.READY);
            next.setExpiryDate(today.plusDays(HOLD_DAYS));
        } else {
            book.setAvailableCopies(Math.min(book.getTotalCopies(), book.getAvailableCopies() + 1));
        }
    }

    private User findUser(String username) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new ServiceException("Không tìm thấy tài khoản " + username));
    }

    private Book findBook(int bookId) {
        return books.stream()
                .filter(b -> b.getId() == bookId)
                .findFirst()
                .orElseThrow(() -> new ServiceException("Không tìm thấy sách id=" + bookId));
    }

    private BorrowRecord findBorrow(long recordId) {
        return borrows.stream()
                .filter(b -> b.getId() == recordId)
                .findFirst()
                .orElseThrow(() -> new ServiceException("Không tìm thấy phiếu mượn id=" + recordId));
    }

    private ReservationRecord findReservation(long reservationId) {
        return reservations.stream()
                .filter(r -> r.getId() == reservationId)
                .findFirst()
                .orElseThrow(() -> new ServiceException("Không tìm thấy phiếu đặt trước id=" + reservationId));
    }

    private void saveAll() {
        storage.saveBooks(books);
        storage.saveBorrows(borrows);
        storage.saveReservations(reservations);
    }
}
