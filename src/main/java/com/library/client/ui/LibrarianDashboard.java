package com.library.client.ui;

import com.library.client.network.LibraryClient;
import com.library.common.model.Book;
import com.library.common.model.BorrowRecord;
import com.library.common.model.User;
import com.library.common.net.CommandType;
import com.library.common.net.Response;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LibrarianDashboard extends JFrame {
    private final LibraryClient client;
    private final User user;

    private final List<Book> shownBooks = new ArrayList<>();
    private final DefaultTableModel bookModel =
            Tables.model("ID", "Tựa sách", "Tác giả", "Thể loại", "Năm", "Còn/Tổng");
    private final JTable bookTable = new JTable(bookModel);
    private final DefaultTableModel borrowModel =
            Tables.model("Mã phiếu", "MSSV", "Tựa sách", "Ngày mượn", "Hạn trả", "Ngày trả", "Trạng thái");
    private final JTable borrowTable = new JTable(borrowModel);

    public LibrarianDashboard(LibraryClient client, User user) {
        super("Thủ thư - " + user.getFullName());
        this.client = client;
        this.user = user;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Quản lý sách", booksPanel());
        tabs.addTab("Phiếu mượn toàn trường", borrowsPanel());
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) {
                loadBorrows();
            } else {
                loadBooks();
            }
        });

        setContentPane(tabs);
        setSize(980, 560);
        setLocationRelativeTo(null);
        loadBooks();
        loadBorrows();
        // Server đẩy sự kiện khi có người khác mượn/trả/sửa sách -> tải lại bảng.
        client.setEventListener(event -> javax.swing.SwingUtilities.invokeLater(() -> {
            loadBooks();
            loadBorrows();
        }));
    }

    private JPanel booksPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Thêm sách");
        addButton.addActionListener(e -> doAdd());
        JButton editButton = new JButton("Sửa");
        editButton.addActionListener(e -> doEdit());
        JButton deleteButton = new JButton("Xóa");
        deleteButton.addActionListener(e -> doDelete());
        JButton refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> loadBooks());
        top.add(addButton);
        top.add(editButton);
        top.add(deleteButton);
        top.add(refreshButton);

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

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(borrowTable), BorderLayout.CENTER);
        return panel;
    }

    private void loadBooks() {
        Response response = send(CommandType.GET_BOOKS, Map.of());
        if (response == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Book> data = (List<Book>) response.getData();
        shownBooks.clear();
        shownBooks.addAll(data);
        bookModel.setRowCount(0);
        for (Book book : data) {
            bookModel.addRow(new Object[]{
                    book.getId(), book.getTitle(), book.getAuthor(), book.getCategory(),
                    book.getPublishYear(), Tables.copiesText(book)
            });
        }
    }

    private void loadBorrows() {
        Response response = send(CommandType.GET_ALL_BORROWS, Map.of("username", user.getUsername()));
        if (response == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<BorrowRecord> data = (List<BorrowRecord>) response.getData();
        borrowModel.setRowCount(0);
        for (BorrowRecord record : data) {
            borrowModel.addRow(new Object[]{
                    record.getId(), record.getStudentId(), record.getBookTitle(),
                    record.getBorrowDate(), record.getDueDate(),
                    record.getReturnDate() == null ? "" : record.getReturnDate(),
                    Tables.statusText(record.getStatus())
            });
        }
    }

    private void doAdd() {
        BookDialog dialog = new BookDialog(this, null);
        if (!dialog.showDialog()) {
            return;
        }
        Response response = send(CommandType.ADD_BOOK, Map.of(
                "username", user.getUsername(),
                "title", dialog.title(),
                "author", dialog.author(),
                "category", dialog.category(),
                "year", dialog.year(),
                "copies", dialog.copies()));
        report(response, "Đã thêm sách");
        loadBooks();
    }

    private void doEdit() {
        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một cuốn sách trong bảng.");
            return;
        }
        Book book = shownBooks.get(row);
        BookDialog dialog = new BookDialog(this, book);
        if (!dialog.showDialog()) {
            return;
        }
        Response response = send(CommandType.UPDATE_BOOK, Map.of(
                "username", user.getUsername(),
                "bookId", book.getId(),
                "title", dialog.title(),
                "author", dialog.author(),
                "category", dialog.category(),
                "year", dialog.year(),
                "copies", dialog.copies()));
        report(response, "Đã cập nhật sách");
        loadBooks();
    }

    private void doDelete() {
        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một cuốn sách trong bảng.");
            return;
        }
        Book book = shownBooks.get(row);
        int answer = JOptionPane.showConfirmDialog(this,
                "Xóa tựa sách \"" + book.getTitle() + "\"?", "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        Response response = send(CommandType.DELETE_BOOK, Map.of(
                "username", user.getUsername(),
                "bookId", book.getId()));
        report(response, "Đã xóa sách");
        loadBooks();
    }

    private void report(Response response, String successTitle) {
        if (response == null) {
            return;
        }
        JOptionPane.showMessageDialog(this, response.getMessage(),
                response.isOk() ? successTitle : "Không thực hiện được",
                response.isOk() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
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
