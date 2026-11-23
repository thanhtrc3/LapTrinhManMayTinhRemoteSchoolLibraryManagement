package com.library.client.ui;

import com.library.common.model.Book;
import com.library.common.model.BorrowStatus;
import com.library.common.model.ReservationStatus;

import javax.swing.table.DefaultTableModel;

/** Tiện ích nhỏ dùng chung cho các bảng Swing. */
final class Tables {
    private Tables() {
    }

    static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    static String statusText(BorrowStatus status) {
        switch (status) {
            case RETURNED:
                return "Đã trả";
            case OVERDUE:
                return "QUÁ HẠN";
            default:
                return "Đang mượn";
        }
    }

    static String reservationText(ReservationStatus status) {
        switch (status) {
            case READY:
                return "Sẵn sàng nhận";
            case FULFILLED:
                return "Đã nhận sách";
            case CANCELLED:
                return "Đã hủy";
            case EXPIRED:
                return "Hết hạn nhận";
            default:
                return "Đang chờ";
        }
    }

    /** "0/2 (giữ 1)" = không còn bản tự do, nhưng 1 bản đang giữ cho người đặt trước. */
    static String copiesText(Book book) {
        String text = book.getAvailableCopies() + "/" + book.getTotalCopies();
        if (book.getHeldCopies() > 0) {
            text += " (giữ " + book.getHeldCopies() + ")";
        }
        return text;
    }
}
