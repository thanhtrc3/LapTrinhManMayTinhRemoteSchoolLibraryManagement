package com.library.client.ui;

import com.library.client.network.LibraryClient;
import com.library.common.model.Book;
import com.library.common.model.BorrowRecord;
import com.library.common.model.ReservationRecord;
import com.library.common.model.User;
import com.library.common.net.CommandType;
import com.library.common.net.Response;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudentDashboard extends JFrame {
    private final LibraryClient client;
    private final User user;

    private final List<Book> books = new ArrayList<>();
    private final List<Book> shownBooks = new ArrayList<>();
    private final List<BorrowRecord> shownBorrows = new ArrayList<>();
    private final List<ReservationRecord> shownReservations = new ArrayList<>();

    private final DefaultTableModel bookModel =
            Tables.model("ID", "Tựa sách", "Tác giả", "Thể loại", "Năm", "Còn/Tổng");
    private final JTable bookTable = new JTable(bookModel);
    private final DefaultTableModel borrowModel =
            Tables.model("Mã phiếu", "Tựa sách", "Ngày mượn", "Hạn trả", "Ngày trả", "Trạng thái");
    private final JTable borrowTable = new JTable(borrowModel);
    private final DefaultTableModel reservationModel =
            Tables.model("Mã", "Tựa sách", "Ngày đặt", "Hạn nhận", "Trạng thái");
    private final JTable reservationTable = new JTable(reservationModel);

    private final JTextField searchField = new JTextField(22);
    private final JSpinner daysSpinner = new JSpinner(new SpinnerNumberModel(14, 1, 30, 1));

    public StudentDashboard(LibraryClient client, User user) {
        super("Sinh viên - " + user.getFullName());
        this.client = client;
        this.user = user;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Tra cứu & Mượn sách", booksPanel());
        tabs.addTab("Sách đang mượn", borrowsPanel());
        tabs.addTab("Sách đã đặt trước", reservationsPanel());
        tabs.addChangeListener(e -> {
            switch (tabs.getSelectedIndex()) {
                case 1:
                    loadBorrows();
                    break;
                case 2:
                    loadReservations();
                    break;
                default:
                    loadBooks();
            }
        });

        setContentPane(tabs);
        setSize(920, 560);
        setLocationRelativeTo(null);
        loadBooks();
        loadBorrows();
        loadReservations();
        // Server đẩy sự kiện khi có người khác mượn/trả/đặt trước/sửa sách -> tải lại bảng.
        client.setEventListener(event -> SwingUtilities.invokeLater(() -> {
            loadBooks();
            loadBorrows();
            loadReservations();
        }));
    }

    private JPanel booksPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Tìm kiếm:"));
        top.add(searchField);
        JButton searchButton = new JButton("Tìm");
        searchButton.addActionListener(e -> fillBooks());
        searchField.addActionListener(e -> fillBooks());
        top.add(searchButton);
        top.add(new JLabel("Số ngày mượn:"));
        top.add(daysSpinner);
        JButton borrowButton = new JButton("Mượn sách");
        borrowButton.addActionListener(e -> doBorrow());
        top.add(borrowButton);
        JButton reserveButton = new JButton("Đặt trước");
        reserveButton.addActionListener(e -> doReserve());
        top.add(reserveButton);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(bookTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel borrowsPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> loadBorrows());
        top.add(refreshButton);
        JButton returnButton = new JButton("Trả sách");
        returnButton.addActionListener(e -> doReturn());
        top.add(returnButton);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(borrowTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel reservationsPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> loadReservations());
        top.add(refreshButton);
        JButton cancelButton = new JButton("Hủy đặt trước");
        cancelButton.addActionListener(e -> doCancelReservation());
        top.add(cancelButton);
        JLabel hint = new JLabel("Khi sách về, bạn có 48 giờ để đến nhận.");
        top.add(hint);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(reservationTable), BorderLayout.CENTER);
        return panel;
    }

    private void loadBooks() {
        Response response = send(CommandType.GET_BOOKS, Map.of());
        if (response == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Book> data = (List<Book>) response.getData();
        books.clear();
        books.addAll(data);
        fillBooks();
    }

    private void fillBooks() {
        String keyword = searchField.getText().trim().toLowerCase(Locale.ROOT);
        bookModel.setRowCount(0);
        shownBooks.clear();
        for (Book book : books) {
            if (!keyword.isEmpty() && !matches(book, keyword)) {
                continue;
            }
            shownBooks.add(book);
            bookModel.addRow(new Object[]{
                    book.getId(), book.getTitle(), book.getAuthor(), book.getCategory(),
                    book.getPublishYear(), Tables.copiesText(book)
            });
        }
    }

    private static boolean matches(Book book, String keyword) {
        return book.getTitle().toLowerCase(Locale.ROOT).contains(keyword)
                || book.getAuthor().toLowerCase(Locale.ROOT).contains(keyword)
                || book.getCategory().toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void loadBorrows() {
        Response response = send(CommandType.GET_MY_BORROWS, Map.of("username", user.getUsername()));
        if (response == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<BorrowRecord> data = (List<BorrowRecord>) response.getData();
        shownBorrows.clear();
        shownBorrows.addAll(data);
        borrowModel.setRowCount(0);
        for (BorrowRecord record : data) {
            borrowModel.addRow(new Object[]{
                    record.getId(), record.getBookTitle(), record.getBorrowDate(), record.getDueDate(),
                    record.getReturnDate() == null ? "" : record.getReturnDate(),
                    Tables.statusText(record.getStatus())
            });
        }
    }

    private void loadReservations() {
        Response response = send(CommandType.GET_MY_RESERVATIONS, Map.of("username", user.getUsername()));
        if (response == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<ReservationRecord> data = (List<ReservationRecord>) response.getData();
        shownReservations.clear();
        shownReservations.addAll(data);
        reservationModel.setRowCount(0);
        for (ReservationRecord record : data) {
            reservationModel.addRow(new Object[]{
                    record.getId(), record.getBookTitle(), record.getReservationDate(),
                    record.getExpiryDate() == null ? "" : record.getExpiryDate(),
                    Tables.reservationText(record.getStatus())
            });
        }
    }

    private void doBorrow() {
        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một cuốn sách trong bảng.");
            return;
        }
        Book book = shownBooks.get(row);
        Response response = send(CommandType.BORROW, Map.of(
                "username", user.getUsername(),
                "bookId", book.getId(),
                "days", daysSpinner.getValue()));
        if (response == null) {
            return;
        }
        JOptionPane.showMessageDialog(this, response.getMessage(),
                response.isOk() ? "Thành công" : "Không thể mượn",
                response.isOk() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        reloadAll();
    }

    private void doReturn() {
        int row = borrowTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một phiếu mượn trong bảng.");
            return;
        }
        BorrowRecord record = shownBorrows.get(row);
        Response response = send(CommandType.RETURN_BOOK, Map.of(
                "username", user.getUsername(),
                "recordId", record.getId()));
        if (response == null) {
            return;
        }
        JOptionPane.showMessageDialog(this, response.getMessage(),
                response.isOk() ? "Thành công" : "Không thể trả",
                response.isOk() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        reloadAll();
    }

    private void doReserve() {
        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một cuốn sách trong bảng.");
            return;
        }
        Book book = shownBooks.get(row);
        Response response = send(CommandType.RESERVE, Map.of(
                "username", user.getUsername(),
                "bookId", book.getId()));
        if (response == null) {
            return;
        }
        JOptionPane.showMessageDialog(this, response.getMessage(),
                response.isOk() ? "Đã đặt trước" : "Không thể đặt trước",
                response.isOk() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        reloadAll();
    }

    private void doCancelReservation() {
        int row = reservationTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một phiếu đặt trước trong bảng.");
            return;
        }
        ReservationRecord record = shownReservations.get(row);
        Response response = send(CommandType.CANCEL_RESERVATION, Map.of(
                "username", user.getUsername(),
                "reservationId", record.getId()));
        if (response == null) {
            return;
        }
        JOptionPane.showMessageDialog(this, response.getMessage(),
                response.isOk() ? "Đã hủy" : "Không thể hủy",
                response.isOk() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        reloadAll();
    }

    private void reloadAll() {
        loadBooks();
        loadBorrows();
        loadReservations();
    }

    private Response send(CommandType type, Map<String, Object> data) {
        try {
            return client.send(type, data);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Mất kết nối tới server:\n" + e.getMessage(),
                    "Lỗi mạng", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
}
