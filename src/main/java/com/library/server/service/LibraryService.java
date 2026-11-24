package com.library.server.service;

import com.library.common.model.*;
import com.library.common.net.EventPublisher;
import com.library.common.net.EventType;
import com.library.common.net.RealtimeEvent;
import com.library.common.net.ResponseStatus;
import com.library.common.util.FuzzySearchUtil;
import com.library.server.storage.DataStorage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Lớp nghiệp vụ trung tâm của hệ thống Thư viện (LibraryService).
 * Phục vụ toàn bộ các chức năng từ D3 đến D6 theo đặc tả.
 */
public class LibraryService {
    private final DataStorage storage;
    private EventPublisher publisher;

    // Khóa hạt mịn ReentrantLock theo từng cuốn sách (bookId)
    private final ConcurrentHashMap<Integer, ReentrantLock> bookLocks = new ConcurrentHashMap<>();
    private final Object globalLock = new Object();

    // Lưu các reservationId đã phát cảnh báo HOLD_EXPIRING (chỉ phát đúng 1 lần khi còn <= 6h)
    private final Set<Long> expiringNotifiedIds = ConcurrentHashMap.newKeySet();

    private final AtomicLong nextBorrowId = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong nextReservationId = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong nextAuditId = new AtomicLong(System.currentTimeMillis());

    public LibraryService(DataStorage storage) {
        this(storage, null);
    }

    public LibraryService(DataStorage storage, EventPublisher publisher) {
        this.storage = storage;
        this.publisher = publisher;
    }

    public void setPublisher(EventPublisher publisher) {
        this.publisher = publisher;
    }

    public EventPublisher getPublisher() {
        return publisher;
    }

    public DataStorage getStorage() {
        return storage;
    }

    public ReentrantLock getLock(int bookId) {
        return bookLocks.computeIfAbsent(bookId, id -> new ReentrantLock());
    }

    private void publishEvent(RealtimeEvent event) {
        if (publisher != null && event != null) {
            publisher.publish(event);
        }
    }

    private void addAudit(AuditAction action, String actor, String details) {
        synchronized (globalLock) {
            List<AuditLog> list = storage.loadAuditLogs();
            list.add(new AuditLog(nextAuditId.incrementAndGet(), LocalDateTime.now(), action, actor, details));
            storage.saveAuditLogs(list);
        }
    }

    // ==========================================
    // D3: XÁC THỰC, TÌM KIẾM MỜ, GỢI Ý (C3)
    // ==========================================

    public User authenticate(String username, String password) throws LibraryException {
        if (username == null || password == null) {
            throw new LibraryException(ResponseStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu");
        }
        String u = username.trim();
        List<User> users = storage.loadUsers();
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(u) && user.getPassword().equals(password)) {
                return user;
            }
        }
        throw new LibraryException(ResponseStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu");
    }

    public List<Book> searchBooks(String keyword, String category, boolean onlyAvailable) {
        List<Book> all = storage.loadBooks();
        return all.stream().filter(b -> {
            // Lọc theo thể loại
            if (category != null && !category.isBlank() && !category.equalsIgnoreCase("Tất cả")) {
                if (b.getCategory() == null || !b.getCategory().equalsIgnoreCase(category.trim())) {
                    return false;
                }
            }
            // Lọc theo trạng thái còn sách
            if (onlyAvailable && b.getAvailableCopies() <= 0) {
                return false;
            }
            // Lọc tìm kiếm mờ (Fuzzy Search)
            if (keyword != null && !keyword.isBlank()) {
                return FuzzySearchUtil.matches(keyword, b.getTitle(), b.getAuthor(), b.getCategory(), b.getIsbn());
            }
            return true;
        }).collect(Collectors.toList());
    }

    public void recordSearch(String studentId, String keyword, String category) {
        if (studentId == null || studentId.isBlank()) return;
        synchronized (globalLock) {
            List<SearchHistoryItem> list = storage.loadSearchHistory();
            list.add(new SearchHistoryItem(studentId, keyword != null ? keyword.trim() : "", category != null ? category.trim() : "", LocalDateTime.now()));

            // Giữ tối đa 50 lượt tìm kiếm gần nhất cho mỗi sinh viên
            Map<String, List<SearchHistoryItem>> byStudent = list.stream()
                    .collect(Collectors.groupingBy(SearchHistoryItem::getStudentId));

            List<SearchHistoryItem> updated = new ArrayList<>();
            for (List<SearchHistoryItem> studentList : byStudent.values()) {
                studentList.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
                updated.addAll(studentList.stream().limit(50).collect(Collectors.toList()));
            }
            storage.saveSearchHistory(updated);
        }
    }

    public List<Book> getRecommendations(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return storage.loadBooks().stream().limit(5).collect(Collectors.toList());
        }

        List<SearchHistoryItem> history = storage.loadSearchHistory().stream()
                .filter(h -> studentId.equals(h.getStudentId()))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(20)
                .collect(Collectors.toList());

        // Lấy sách sinh viên đang mượn
        Set<Integer> borrowedBookIds = storage.loadBorrows().stream()
                .filter(b -> studentId.equals(b.getStudentId()) && b.getStatus() == BorrowStatus.BORROWING)
                .map(BorrowRecord::getBookId)
                .collect(Collectors.toSet());

        // Đếm 3 thể loại tìm kiếm nhiều nhất
        Map<String, Long> categoryCounts = history.stream()
                .map(SearchHistoryItem::getSearchCategory)
                .filter(c -> c != null && !c.isBlank() && !c.equalsIgnoreCase("Tất cả"))
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));

        List<String> topCategories = categoryCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Book> allBooks = storage.loadBooks();
        List<Book> recommended = new ArrayList<>();

        if (!topCategories.isEmpty()) {
            for (Book b : allBooks) {
                if (!borrowedBookIds.contains(b.getId()) && topCategories.contains(b.getCategory())) {
                    recommended.add(b);
                    if (recommended.size() >= 5) break;
                }
            }
        }

        // Bổ sung thêm sách nếu chưa đủ 5
        if (recommended.size() < 5) {
            for (Book b : allBooks) {
                if (!borrowedBookIds.contains(b.getId()) && !recommended.contains(b)) {
                    recommended.add(b);
                    if (recommended.size() >= 5) break;
                }
            }
        }

        return recommended;
    }

    // ==========================================
    // D4: MƯỢN / TRẢ / KHÓA HẠT MỊN / PHẠT (C4)
    // ==========================================

    public BorrowRecord borrowBook(String studentId, int bookId, int days) throws LibraryException {
        ReentrantLock lock = getLock(bookId);
        lock.lock();
        try {
            Book updatedBook;
            BorrowRecord record;
            String auditCopyId;
            synchronized (globalLock) {
                List<User> users = storage.loadUsers();
                User user = users.stream().filter(u -> u.getUsername().equals(studentId)).findFirst()
                        .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy thông tin sinh viên"));

                if (user.isBlocked()) {
                    throw new LibraryException(ResponseStatus.BLOCKED, "Tài khoản đang bị khóa do vi phạm");
                }

                List<BorrowRecord> borrows = storage.loadBorrows();
                // Kiểm tra xem có sách nào quá hạn chưa trả không
                boolean hasOverdue = borrows.stream().anyMatch(b -> b.getStudentId().equals(studentId)
                        && (b.getStatus() == BorrowStatus.OVERDUE || (b.getStatus() == BorrowStatus.BORROWING && b.getDueDate().isBefore(LocalDate.now()))));
                if (hasOverdue) {
                    throw new LibraryException(ResponseStatus.BLOCKED, "Còn sách quá hạn chưa trả");
                }

                // Kiểm tra hạn mức mượn tối đa (3 cuốn)
                long activeCount = borrows.stream().filter(b -> b.getStudentId().equals(studentId)
                        && (b.getStatus() == BorrowStatus.BORROWING || b.getStatus() == BorrowStatus.OVERDUE)).count();
                if (activeCount >= user.getMaxBorrowQuota()) {
                    throw new LibraryException(ResponseStatus.QUOTA_EXCEEDED, "Đã đạt hạn mức mượn tối đa (" + user.getMaxBorrowQuota() + " cuốn)");
                }

                List<Book> books = storage.loadBooks();
                Book book = books.stream().filter(b -> b.getId() == bookId).findFirst()
                        .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy sách"));

                List<BookCopy> copies = storage.loadCopies();
                List<ReservationRecord> reservations = storage.loadReservations();

                // Kiểm tra xem sinh viên có bản sao RESERVED dành riêng cho mình không
                Optional<ReservationRecord> myReservation = reservations.stream()
                        .filter(r -> r.getBookId() == bookId && r.getStudentId().equals(studentId)
                                && r.getStatus() == ReservationStatus.PENDING && r.getAssignedCopyId() != null
                                && (r.getExpiryDate() == null || r.getExpiryDate().isAfter(LocalDateTime.now())))
                        .findFirst();

                BookCopy targetCopy = null;
                if (myReservation.isPresent()) {
                    String reservedCopyId = myReservation.get().getAssignedCopyId();
                    targetCopy = copies.stream().filter(c -> c.getCopyId().equals(reservedCopyId)).findFirst().orElse(null);
                    if (targetCopy != null) {
                        myReservation.get().setStatus(ReservationStatus.FULFILLED);
                        expiringNotifiedIds.remove(myReservation.get().getId());
                        storage.saveReservations(reservations);
                    }
                }

                // Nếu không có bản đặt trước, tìm bản AVAILABLE bình thường
                if (targetCopy == null) {
                    targetCopy = copies.stream()
                            .filter(c -> c.getBookId() == bookId && c.getStatus() == CopyStatus.AVAILABLE)
                            .findFirst()
                            .orElse(null);
                }

                if (targetCopy == null) {
                    throw new LibraryException(ResponseStatus.OUT_OF_STOCK, "Sách đã hết bản sao khả dụng");
                }

                // Cập nhật trạng thái bản sao
                targetCopy.setStatus(CopyStatus.BORROWED);
                storage.saveCopies(copies);

                // Cập nhật số lượng sách khả dụng
                int availableCount = (int) copies.stream().filter(c -> c.getBookId() == bookId && c.getStatus() == CopyStatus.AVAILABLE).count();
                book.setAvailableCopies(availableCount);
                storage.saveBooks(books);

                // Giới hạn số ngày mượn từ 1 đến 30 ngày (mặc định 14 ngày nếu ngoài dải)
                int clampedDays = (days <= 0 || days > 30) ? 14 : days;
                record = new BorrowRecord(
                        nextBorrowId.incrementAndGet(),
                        targetCopy.getCopyId(),
                        bookId,
                        book.getTitle(),
                        studentId,
                        LocalDate.now(),
                        LocalDate.now().plusDays(clampedDays),
                        null,
                        BorrowStatus.BORROWING,
                        0
                );
                borrows.add(record);
                storage.saveBorrows(borrows);
                updatedBook = book;
                auditCopyId = targetCopy.getCopyId();
            }

            addAudit(AuditAction.BORROW, studentId, "Mượn sách '" + updatedBook.getTitle() + "', bản sao: " + auditCopyId);
            publishEvent(new RealtimeEvent(EventType.BOOKS_CHANGED, null, "Cập nhật số lượng sách", updatedBook));
            publishEvent(new RealtimeEvent(EventType.BORROW_CHANGED, studentId, "Mượn sách thành công", record));

            return record;
        } finally {
            lock.unlock();
        }
    }

    public BorrowRecord returnBook(String studentId, String copyId) throws LibraryException {
        BorrowRecord record;
        Book updatedBook = null;
        String blockedUsername = null;

        synchronized (globalLock) {
            List<BorrowRecord> borrows = storage.loadBorrows();
            record = borrows.stream()
                    .filter(b -> b.getCopyId().equalsIgnoreCase(copyId) && (b.getStatus() == BorrowStatus.BORROWING || b.getStatus() == BorrowStatus.OVERDUE))
                    .findFirst()
                    .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy lượt mượn đang hoạt động cho bản sao " + copyId));

            List<User> users = storage.loadUsers();
            if (studentId != null && !studentId.isBlank() && !record.getStudentId().equalsIgnoreCase(studentId)) {
                User caller = users.stream().filter(u -> u.getUsername().equalsIgnoreCase(studentId)).findFirst().orElse(null);
                if (caller == null || caller.getRole() != Role.LIBRARIAN) {
                    throw new LibraryException(ResponseStatus.UNAUTHORIZED, "Bạn không thể trả sách của sinh viên khác");
                }
            }

            record.setStatus(BorrowStatus.RETURNED);
            record.setReturnDate(LocalDate.now());

            // Kiểm tra quá hạn -> ghi nhận vi phạm (3-strike)
            if (LocalDate.now().isAfter(record.getDueDate())) {
                User borrower = users.stream().filter(u -> u.getUsername().equals(record.getStudentId())).findFirst().orElse(null);
                if (borrower != null) {
                    borrower.setViolationCount(borrower.getViolationCount() + 1);
                    addAudit(AuditAction.OVERDUE_STRIKE, record.getStudentId(), "Quá hạn trả sách " + copyId + ", vi phạm lần " + borrower.getViolationCount());
                    if (borrower.getViolationCount() >= 3) {
                        borrower.setBlocked(true);
                        blockedUsername = borrower.getUsername();
                    }
                    storage.saveUsers(users);
                }
            }

            storage.saveBorrows(borrows);
        }

        if (blockedUsername != null) {
            publishEvent(new RealtimeEvent(EventType.VIOLATION_BLOCKED, blockedUsername, "Tài khoản bị khóa do vi phạm quá hạn 3 lần", null));
        }

        // Tự động phân phối bản sao vừa trả (Auto-allocation D5) và đồng bộ lại số lượng sách
        assignReservedCopy(record.getBookId(), record.getCopyId());

        synchronized (globalLock) {
            List<Book> books = storage.loadBooks();
            updatedBook = books.stream().filter(b -> b.getId() == record.getBookId()).findFirst().orElse(null);
        }

        addAudit(AuditAction.RETURN, studentId != null ? studentId : record.getStudentId(), "Trả sách bản sao: " + record.getCopyId());
        publishEvent(new RealtimeEvent(EventType.BOOKS_CHANGED, null, "Cập nhật số lượng sách", updatedBook));
        publishEvent(new RealtimeEvent(EventType.BORROW_CHANGED, record.getStudentId(), "Trả sách thành công", record));

        return record;
    }

    // ==========================================
    // D5: GIA HẠN, ĐẶT TRƯỚC, HẾT HẠN (C5)
    // ==========================================

    public BorrowRecord renewBook(String studentId, long borrowRecordId) throws LibraryException {
        BorrowRecord record;
        synchronized (globalLock) {
            List<User> users = storage.loadUsers();
            User user = users.stream().filter(u -> u.getUsername().equalsIgnoreCase(studentId)).findFirst()
                    .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy thông tin sinh viên"));
            if (user.isBlocked()) {
                throw new LibraryException(ResponseStatus.BLOCKED, "Tài khoản đang bị khóa do vi phạm, không thể gia hạn");
            }

            List<BorrowRecord> borrows = storage.loadBorrows();
            record = borrows.stream()
                    .filter(b -> b.getId() == borrowRecordId && b.getStudentId().equals(studentId))
                    .findFirst()
                    .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy phiếu mượn"));

            if (record.getStatus() == BorrowStatus.RETURNED) {
                throw new LibraryException(ResponseStatus.ERROR, "Sách đã trả, không thể gia hạn");
            }

            if (record.getStatus() == BorrowStatus.OVERDUE || record.getDueDate().isBefore(LocalDate.now())) {
                throw new LibraryException(ResponseStatus.ERROR, "Sách đã quá hạn, không thể gia hạn");
            }

            if (record.getRenewCount() >= 2) {
                throw new LibraryException(ResponseStatus.ERROR, "Đã đạt giới hạn gia hạn tối đa (2 lần)");
            }

            // Từ chối nếu đang có người đặt trước (PENDING) cho sách này
            List<ReservationRecord> reservations = storage.loadReservations();
            boolean hasPendingReservation = reservations.stream()
                    .anyMatch(r -> r.getBookId() == record.getBookId() && r.getStatus() == ReservationStatus.PENDING && !r.getStudentId().equals(studentId));
            if (hasPendingReservation) {
                throw new LibraryException(ResponseStatus.ERROR, "Không thể gia hạn vì có sinh viên khác đang đặt trước sách này");
            }

            record.setDueDate(record.getDueDate().plusDays(7));
            record.setRenewCount(record.getRenewCount() + 1);
            storage.saveBorrows(borrows);
        }

        addAudit(AuditAction.RENEW, studentId, "Gia hạn thêm 7 ngày (lần " + record.getRenewCount() + ") cho phiếu mượn " + borrowRecordId);
        publishEvent(new RealtimeEvent(EventType.BORROW_CHANGED, studentId, "Gia hạn mượn thành công", record));

        return record;
    }

    public ReservationRecord reserveBook(String studentId, int bookId) throws LibraryException {
        ReentrantLock lock = getLock(bookId);
        lock.lock();
        try {
            ReservationRecord record;
            String bookTitle;
            synchronized (globalLock) {
                List<User> users = storage.loadUsers();
                User user = users.stream().filter(u -> u.getUsername().equalsIgnoreCase(studentId)).findFirst()
                        .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy thông tin sinh viên"));
                if (user.isBlocked()) {
                    throw new LibraryException(ResponseStatus.BLOCKED, "Tài khoản đang bị khóa do vi phạm");
                }

                List<Book> books = storage.loadBooks();
                Book book = books.stream().filter(b -> b.getId() == bookId).findFirst()
                        .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy sách"));

                if (book.getAvailableCopies() > 0) {
                    throw new LibraryException(ResponseStatus.ERROR, "Sách còn trong kho, không cần đặt trước");
                }

                List<BorrowRecord> borrows = storage.loadBorrows();
                boolean currentlyBorrowing = borrows.stream()
                        .anyMatch(b -> b.getBookId() == bookId && b.getStudentId().equalsIgnoreCase(studentId)
                                && (b.getStatus() == BorrowStatus.BORROWING || b.getStatus() == BorrowStatus.OVERDUE));
                if (currentlyBorrowing) {
                    throw new LibraryException(ResponseStatus.ERROR, "Bạn đang mượn cuốn sách này rồi");
                }

                List<ReservationRecord> reservations = storage.loadReservations();
                boolean alreadyReserved = reservations.stream()
                        .anyMatch(r -> r.getBookId() == bookId && r.getStudentId().equals(studentId) && r.getStatus() == ReservationStatus.PENDING);
                if (alreadyReserved) {
                    throw new LibraryException(ResponseStatus.ERROR, "Bạn đã đặt trước sách này rồi");
                }

                bookTitle = book.getTitle();
                record = new ReservationRecord(
                        nextReservationId.incrementAndGet(),
                        bookId,
                        bookTitle,
                        studentId,
                        LocalDateTime.now(),
                        null,
                        ReservationStatus.PENDING,
                        null
                );
                reservations.add(record);
                storage.saveReservations(reservations);
            }

            addAudit(AuditAction.RESERVE, studentId, "Đặt trước sách: " + bookTitle);
            return record;
        } finally {
            lock.unlock();
        }
    }

    public void cancelReservation(String studentId, long reservationId) throws LibraryException {
        int bookId;
        String copyId;
        synchronized (globalLock) {
            List<ReservationRecord> reservations = storage.loadReservations();
            ReservationRecord record = reservations.stream()
                    .filter(r -> r.getId() == reservationId && r.getStudentId().equals(studentId))
                    .findFirst()
                    .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy phiếu đặt trước"));

            if (record.getStatus() != ReservationStatus.PENDING) {
                throw new LibraryException(ResponseStatus.ERROR, "Phiếu đặt trước không ở trạng thái chờ");
            }

            record.setStatus(ReservationStatus.CANCELLED);
            bookId = record.getBookId();
            copyId = record.getAssignedCopyId();
            record.setAssignedCopyId(null);
            expiringNotifiedIds.remove(record.getId());
            storage.saveReservations(reservations);
        }

        if (copyId != null) {
            assignReservedCopy(bookId, copyId);
        }
    }

    public void assignReservedCopy(int bookId, String copyId) {
        RealtimeEvent alertEvent = null;
        RealtimeEvent booksChangedEvent = null;

        synchronized (globalLock) {
            List<ReservationRecord> reservations = storage.loadReservations();
            List<BookCopy> copies = storage.loadCopies();

            BookCopy copy = copies.stream().filter(c -> c.getCopyId().equalsIgnoreCase(copyId)).findFirst().orElse(null);
            if (copy == null) return;

            // Tìm người xếp hàng đặt trước sớm nhất (FIFO) chưa được gán bản sao
            Optional<ReservationRecord> nextInLine = reservations.stream()
                    .filter(r -> r.getBookId() == bookId && r.getStatus() == ReservationStatus.PENDING && r.getAssignedCopyId() == null)
                    .sorted(Comparator.comparing(ReservationRecord::getReservationDate))
                    .findFirst();

            if (nextInLine.isPresent()) {
                ReservationRecord res = nextInLine.get();
                res.setAssignedCopyId(copy.getCopyId());
                res.setExpiryDate(LocalDateTime.now().plusHours(48)); // Hạn giữ sách 48h
                expiringNotifiedIds.remove(res.getId());
                copy.setStatus(CopyStatus.RESERVED);
                storage.saveReservations(reservations);
                storage.saveCopies(copies);

                alertEvent = new RealtimeEvent(
                        EventType.RESERVATION_ALERT,
                        res.getStudentId(),
                        "Sách bạn đặt trước '" + res.getBookTitle() + "' đã có sẵn tại quầy! Vui lòng nhận trong 48h.",
                        res
                );
            } else {
                copy.setStatus(CopyStatus.AVAILABLE);
                storage.saveCopies(copies);
            }

            // Đồng bộ lại availableCopies trong books.dat
            List<Book> books = storage.loadBooks();
            Book book = books.stream().filter(b -> b.getId() == bookId).findFirst().orElse(null);
            if (book != null) {
                int availableCount = (int) copies.stream()
                        .filter(c -> c.getBookId() == bookId && c.getStatus() == CopyStatus.AVAILABLE)
                        .count();
                if (book.getAvailableCopies() != availableCount) {
                    book.setAvailableCopies(availableCount);
                    storage.saveBooks(books);
                    booksChangedEvent = new RealtimeEvent(EventType.BOOKS_CHANGED, null, "Cập nhật số lượng sách", book);
                }
            }
        }

        if (alertEvent != null) {
            publishEvent(alertEvent);
        }
        if (booksChangedEvent != null) {
            publishEvent(booksChangedEvent);
        }
    }

    public void expireReservations() {
        List<String> expiredCopyIds = new ArrayList<>();
        Map<String, Integer> copyToBookId = new HashMap<>();
        List<RealtimeEvent> expiringEvents = new ArrayList<>();

        synchronized (globalLock) {
            List<ReservationRecord> reservations = storage.loadReservations();
            LocalDateTime now = LocalDateTime.now();

            for (ReservationRecord r : reservations) {
                if (r.getStatus() == ReservationStatus.PENDING && r.getAssignedCopyId() != null && r.getExpiryDate() != null) {
                    if (now.isAfter(r.getExpiryDate())) {
                        r.setStatus(ReservationStatus.EXPIRED);
                        String copyId = r.getAssignedCopyId();
                        r.setAssignedCopyId(null);
                        expiringNotifiedIds.remove(r.getId());
                        expiredCopyIds.add(copyId);
                        copyToBookId.put(copyId, r.getBookId());
                    } else if (now.plusHours(6).isAfter(r.getExpiryDate())) {
                        // Chỉ phát cảnh báo HOLD_EXPIRING đúng 1 lần cho mỗi phiếu đặt trước
                        if (expiringNotifiedIds.add(r.getId())) {
                            expiringEvents.add(new RealtimeEvent(
                                    EventType.HOLD_EXPIRING,
                                    r.getStudentId(),
                                    "Sách đặt trước '" + r.getBookTitle() + "' sắp hết hạn giữ (còn dưới 6 giờ)!",
                                    r
                            ));
                        }
                    }
                }
            }

            if (!expiredCopyIds.isEmpty()) {
                storage.saveReservations(reservations);
            }
        }

        for (RealtimeEvent ev : expiringEvents) {
            publishEvent(ev);
        }

        for (String copyId : expiredCopyIds) {
            assignReservedCopy(copyToBookId.get(copyId), copyId);
        }
    }

    // ==========================================
    // D6: E-BOOK, AUDIT, CRUD, QUẢN LÝ USER (C6)
    // ==========================================

    public byte[] getEbook(int bookId, boolean siteLocal) {
        addAudit(AuditAction.EBOOK_DOWNLOAD, "Hệ thống", "Tải E-Book ID: " + bookId + " (siteLocal=" + siteLocal + ")");
        return storage.getEbookBytes(bookId, siteLocal);
    }

    public List<AuditLog> getAuditLogs() {
        return storage.loadAuditLogs();
    }

    public List<User> getBlockedUsers() {
        return storage.loadUsers().stream().filter(User::isBlocked).collect(Collectors.toList());
    }

    public User unblockUser(String username) throws LibraryException {
        User user;
        synchronized (globalLock) {
            List<User> users = storage.loadUsers();
            user = users.stream().filter(u -> u.getUsername().equalsIgnoreCase(username)).findFirst()
                    .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy người dùng: " + username));

            user.setBlocked(false);
            user.setViolationCount(0);
            storage.saveUsers(users);
        }

        addAudit(AuditAction.UNBLOCK, "admin", "Mở khóa tài khoản cho sinh viên: " + username);
        return user;
    }

    public void checkOverdue() {
        synchronized (globalLock) {
            List<BorrowRecord> borrows = storage.loadBorrows();
            LocalDate today = LocalDate.now();
            boolean changed = false;
            for (BorrowRecord b : borrows) {
                if (b.getStatus() == BorrowStatus.BORROWING && b.getDueDate().isBefore(today)) {
                    b.setStatus(BorrowStatus.OVERDUE);
                    changed = true;
                }
            }
            if (changed) {
                storage.saveBorrows(borrows);
            }
        }
    }

    public Book addBook(String title, String author, String category, int publishYear, int copies) {
        Book book;
        synchronized (globalLock) {
            List<Book> books = storage.loadBooks();
            int nextId = books.stream().mapToInt(Book::getId).max().orElse(0) + 1;
            int numCopies = Math.max(1, copies);

            book = new Book(
                    nextId,
                    "978-604-0-" + String.format("%02d", nextId),
                    title,
                    author,
                    category,
                    publishYear,
                    numCopies,
                    numCopies,
                    true,
                    nextId + "_full.pdf"
            );
            books.add(book);
            storage.saveBooks(books);

            List<BookCopy> allCopies = storage.loadCopies();
            String prefix = String.format("BK%02d", nextId);
            for (int c = 1; c <= numCopies; c++) {
                String copyId = String.format("%s-C%02d", prefix, c);
                String barcode = "BC-" + copyId;
                String qrData = "LIB|" + copyId + "|" + barcode;
                allCopies.add(new BookCopy(copyId, nextId, barcode, qrData, CopyStatus.AVAILABLE));
            }
            storage.saveCopies(allCopies);
        }

        publishEvent(new RealtimeEvent(EventType.BOOKS_CHANGED, null, "Thêm sách mới: " + title, book));
        return book;
    }

    public Book updateBook(int bookId, String title, String author, String category, int publishYear) throws LibraryException {
        Book updated;
        synchronized (globalLock) {
            List<Book> books = storage.loadBooks();
            Book existing = books.stream().filter(b -> b.getId() == bookId).findFirst()
                    .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy sách cần cập nhật"));

            if (title != null && !title.isBlank()) existing.setTitle(title);
            if (author != null && !author.isBlank()) existing.setAuthor(author);
            if (category != null && !category.isBlank()) existing.setCategory(category);
            if (publishYear > 0) existing.setPublishYear(publishYear);

            storage.saveBooks(books);
            updated = existing;
        }
        publishEvent(new RealtimeEvent(EventType.BOOKS_CHANGED, null, "Cập nhật thông tin sách: " + updated.getTitle(), updated));
        return updated;
    }

    public Book updateBook(Book book) throws LibraryException {
        if (book == null) {
            throw new LibraryException(ResponseStatus.ERROR, "Thông tin sách không hợp lệ");
        }
        Book updated;
        synchronized (globalLock) {
            List<Book> books = storage.loadBooks();
            Book existing = null;
            for (int i = 0; i < books.size(); i++) {
                if (books.get(i).getId() == book.getId()) {
                    existing = books.get(i);
                    if (book.getIsbn() != null) existing.setIsbn(book.getIsbn());
                    if (book.getTitle() != null) existing.setTitle(book.getTitle());
                    if (book.getAuthor() != null) existing.setAuthor(book.getAuthor());
                    if (book.getCategory() != null) existing.setCategory(book.getCategory());
                    if (book.getPublishYear() > 0) existing.setPublishYear(book.getPublishYear());
                    if (book.getTotalCopies() > 0) existing.setTotalCopies(book.getTotalCopies());
                    if (book.getAvailableCopies() >= 0 && book.getTotalCopies() > 0) {
                        existing.setAvailableCopies(book.getAvailableCopies());
                    }
                    if (book.getEbookFileName() != null) {
                        existing.setHasEbook(book.isHasEbook());
                        existing.setEbookFileName(book.getEbookFileName());
                    }
                    storage.saveBooks(books);
                    break;
                }
            }
            if (existing == null) {
                throw new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy sách cần cập nhật");
            }
            updated = existing;
        }
        publishEvent(new RealtimeEvent(EventType.BOOKS_CHANGED, null, "Cập nhật thông tin sách: " + updated.getTitle(), updated));
        return updated;
    }

    public void deleteBook(int bookId) throws LibraryException {
        synchronized (globalLock) {
            List<BookCopy> copies = storage.loadCopies();
            boolean hasActiveCopies = copies.stream()
                    .anyMatch(c -> c.getBookId() == bookId && c.getStatus() != CopyStatus.AVAILABLE);
            if (hasActiveCopies) {
                throw new LibraryException(ResponseStatus.ERROR, "Không thể xóa sách đang có bản sao được mượn hoặc giữ chỗ");
            }

            // Xóa bản sao
            List<BookCopy> remainingCopies = copies.stream().filter(c -> c.getBookId() != bookId).collect(Collectors.toList());
            storage.saveCopies(remainingCopies);

            // Xóa sách
            List<Book> books = storage.loadBooks();
            boolean removed = books.removeIf(b -> b.getId() == bookId);
            if (!removed) {
                throw new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy sách cần xóa");
            }
            storage.saveBooks(books);
        }

        publishEvent(new RealtimeEvent(EventType.BOOKS_CHANGED, null, "Đã xóa sách ID " + bookId, null));
    }

    public BookCopy addCopy(int bookId) throws LibraryException {
        Book book;
        BookCopy newCopy;
        synchronized (globalLock) {
            List<Book> books = storage.loadBooks();
            book = books.stream().filter(b -> b.getId() == bookId).findFirst()
                    .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy sách"));

            List<BookCopy> copies = storage.loadCopies();
            int maxIndex = copies.stream()
                    .filter(c -> c.getBookId() == bookId && c.getCopyId() != null)
                    .mapToInt(c -> {
                        int idx = c.getCopyId().lastIndexOf("-C");
                        if (idx >= 0) {
                            try {
                                return Integer.parseInt(c.getCopyId().substring(idx + 2));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        return 0;
                    })
                    .max()
                    .orElse(0);
            String copyId = String.format("BK%02d-C%02d", bookId, maxIndex + 1);
            String barcode = "BC-" + copyId;
            String qrData = "LIB|" + copyId + "|" + barcode;

            newCopy = new BookCopy(copyId, bookId, barcode, qrData, CopyStatus.AVAILABLE);
            copies.add(newCopy);
            storage.saveCopies(copies);

            book.setTotalCopies(book.getTotalCopies() + 1);
            book.setAvailableCopies(book.getAvailableCopies() + 1);
            storage.saveBooks(books);
        }

        publishEvent(new RealtimeEvent(EventType.BOOKS_CHANGED, null, "Thêm bản sao cho sách: " + book.getTitle(), book));
        return newCopy;
    }

    public void removeCopy(String copyId) throws LibraryException {
        Book updatedBook = null;
        synchronized (globalLock) {
            List<BookCopy> copies = storage.loadCopies();
            BookCopy copy = copies.stream().filter(c -> c.getCopyId().equalsIgnoreCase(copyId)).findFirst()
                    .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy bản sao " + copyId));

            if (copy.getStatus() != CopyStatus.AVAILABLE) {
                throw new LibraryException(ResponseStatus.ERROR, "Chỉ có thể xóa bản sao đang khả dụng");
            }

            copies.remove(copy);
            storage.saveCopies(copies);

            List<Book> books = storage.loadBooks();
            Book book = books.stream().filter(b -> b.getId() == copy.getBookId()).findFirst().orElse(null);
            if (book != null) {
                book.setTotalCopies(Math.max(0, book.getTotalCopies() - 1));
                book.setAvailableCopies(Math.max(0, book.getAvailableCopies() - 1));
                storage.saveBooks(books);
                updatedBook = book;
            }
        }

        if (updatedBook != null) {
            publishEvent(new RealtimeEvent(EventType.BOOKS_CHANGED, null, "Xóa bản sao: " + copyId, updatedBook));
        }
    }

    public List<BookCopy> getCopies(int bookId) {
        return storage.loadCopies().stream().filter(c -> c.getBookId() == bookId).collect(Collectors.toList());
    }

    public BookCopy getCopyByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) return null;
        String input = barcode.trim();
        return storage.loadCopies().stream()
                .filter(c -> input.equalsIgnoreCase(c.getBarcode())
                        || input.equalsIgnoreCase(c.getCopyId())
                        || input.equalsIgnoreCase(c.getQrData())
                        || input.equalsIgnoreCase("BAR-" + c.getCopyId())
                        || input.equalsIgnoreCase("BC-" + c.getCopyId()))
                .findFirst()
                .orElse(null);
    }

    public List<BorrowRecord> getAllBorrows() {
        return storage.loadBorrows();
    }

    public List<ReservationRecord> getAllReservations() {
        return storage.loadReservations();
    }

    public List<BorrowRecord> getBorrowsByStudent(String studentId) {
        return storage.loadBorrows().stream()
                .filter(b -> studentId.equals(b.getStudentId()))
                .collect(Collectors.toList());
    }

    public List<ReservationRecord> getReservationsByStudent(String studentId) {
        return storage.loadReservations().stream()
                .filter(r -> studentId.equals(r.getStudentId()))
                .collect(Collectors.toList());
    }

    public List<User> getUsers() {
        return storage.loadUsers();
    }

    public User addUser(String username, String password, String fullName, String email) throws LibraryException {
        return addUser(username, password, fullName, email, Role.STUDENT);
    }

    public User addUser(String username, String password, String fullName, String email, Role role) throws LibraryException {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new LibraryException(ResponseStatus.ERROR, "Tên tài khoản và mật khẩu không được để trống");
        }
        synchronized (globalLock) {
            List<User> users = storage.loadUsers();
            boolean exists = users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(username.trim()));
            if (exists) {
                throw new LibraryException(ResponseStatus.ERROR, "Tên tài khoản '" + username + "' đã tồn tại");
            }

            User user = new User(username.trim(), password, fullName, role != null ? role : Role.STUDENT, email, 3, 0, false);
            users.add(user);
            storage.saveUsers(users);
            return user;
        }
    }

    public User resetPassword(String username, String newPassword) throws LibraryException {
        if (username == null || newPassword == null || newPassword.isBlank()) {
            throw new LibraryException(ResponseStatus.ERROR, "Mật khẩu mới không được để trống");
        }
        synchronized (globalLock) {
            List<User> users = storage.loadUsers();
            User user = users.stream().filter(u -> u.getUsername().equalsIgnoreCase(username.trim())).findFirst()
                    .orElseThrow(() -> new LibraryException(ResponseStatus.NOT_FOUND, "Không tìm thấy tài khoản: " + username));

            user.setPassword(newPassword);
            storage.saveUsers(users);
            return user;
        }
    }
}
