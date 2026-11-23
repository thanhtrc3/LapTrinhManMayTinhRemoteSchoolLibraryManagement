package com.library.client.ui;

import com.library.common.model.Book;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;

/** Hộp thoại thêm/sửa tựa sách. */
public class BookDialog extends JDialog {
    private final JTextField titleField = new JTextField(24);
    private final JTextField authorField = new JTextField(24);
    private final JTextField categoryField = new JTextField(24);
    private final JTextField yearField = new JTextField(24);
    private final JTextField copiesField = new JTextField(24);
    private boolean confirmed;

    public BookDialog(Frame owner, Book book) {
        super(owner, book == null ? "Thêm sách" : "Sửa sách", true);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        addRow(form, 0, "Tựa sách:", titleField);
        addRow(form, 1, "Tác giả:", authorField);
        addRow(form, 2, "Thể loại:", categoryField);
        addRow(form, 3, "Năm xuất bản:", yearField);
        addRow(form, 4, "Số bản sao:", copiesField);

        if (book != null) {
            titleField.setText(book.getTitle());
            authorField.setText(book.getAuthor());
            categoryField.setText(book.getCategory());
            yearField.setText(String.valueOf(book.getPublishYear()));
            copiesField.setText(String.valueOf(book.getTotalCopies()));
        } else {
            yearField.setText(String.valueOf(LocalDate.now().getYear()));
            copiesField.setText("1");
        }

        JButton okButton = new JButton("Lưu");
        okButton.addActionListener(e -> onOk());
        JButton cancelButton = new JButton("Hủy");
        cancelButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(okButton);
        JPanel buttons = new JPanel();
        buttons.add(okButton);
        buttons.add(cancelButton);

        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean showDialog() {
        setVisible(true);
        return confirmed;
    }

    public String title() {
        return titleField.getText().trim();
    }

    public String author() {
        return authorField.getText().trim();
    }

    public String category() {
        return categoryField.getText().trim();
    }

    public int year() {
        return Integer.parseInt(yearField.getText().trim());
    }

    public int copies() {
        return Integer.parseInt(copiesField.getText().trim());
    }

    private void onOk() {
        if (titleField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tựa sách không được để trống.");
            return;
        }
        try {
            if (Integer.parseInt(yearField.getText().trim()) <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Năm xuất bản phải là số dương.");
            return;
        }
        try {
            if (Integer.parseInt(copiesField.getText().trim()) < 1) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số bản sao phải là số từ 1 trở lên.");
            return;
        }
        confirmed = true;
        dispose();
    }

    private static void addRow(JPanel panel, int row, String label, JTextField field) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(4, 4, 4, 8);
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }
}
