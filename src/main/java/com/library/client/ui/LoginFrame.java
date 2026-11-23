package com.library.client.ui;

import com.library.client.network.LibraryClient;
import com.library.common.model.Role;
import com.library.common.model.User;
import com.library.common.net.CommandType;
import com.library.common.net.Response;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.Map;

public class LoginFrame extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1", 16);
    private final JTextField portField = new JTextField("8888", 16);
    private final JTextField userField = new JTextField(16);
    private final JPasswordField passField = new JPasswordField(16);

    public LoginFrame() {
        super("Thư viện trường học - Đăng nhập");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel("THƯ VIỆN TRƯỜNG HỌC");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        addRow(form, 0, title, new JLabel(""));

        addRow(form, 1, new JLabel("Máy chủ:"), hostField);
        addRow(form, 2, new JLabel("Cổng:"), portField);
        addRow(form, 3, new JLabel("Tên đăng nhập:"), userField);
        addRow(form, 4, new JLabel("Mật khẩu:"), passField);

        JButton loginButton = new JButton("Đăng nhập");
        loginButton.addActionListener(e -> doLogin());
        getRootPane().setDefaultButton(loginButton);
        addRow(form, 5, new JLabel(""), loginButton);

        JLabel hint = new JLabel("<html>admin/admin123 (thủ thư) &nbsp;|&nbsp; sv001/123 (sinh viên)</html>");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        addRow(form, 6, new JLabel(""), hint);

        setContentPane(form);
        pack();
        setLocationRelativeTo(null);
    }

    private void doLogin() {
        String host = hostField.getText().trim().isEmpty() ? "127.0.0.1" : hostField.getText().trim();
        int port;
        try {
            port = portField.getText().trim().isEmpty() ? 8888 : Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng phải là số, ví dụ 8888.", "Sai cổng", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());

        try {
            LibraryClient client = new LibraryClient(host, port);
            Response response = client.send(CommandType.LOGIN,
                    Map.of("username", username, "password", password));
            if (!response.isOk()) {
                client.close();
                JOptionPane.showMessageDialog(this, response.getMessage(),
                        "Đăng nhập thất bại", JOptionPane.WARNING_MESSAGE);
                return;
            }
            User user = (User) response.getData();
            dispose();
            JFrame dashboard = user.getRole() == Role.LIBRARIAN
                    ? new LibrarianDashboard(client, user)
                    : new StudentDashboard(client, user);
            dashboard.setVisible(true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Không kết nối được server " + host + ":" + port + "\n" + e.getMessage(),
                    "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void addRow(JPanel panel, int row, JLabel label, java.awt.Component field) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(4, 4, 4, 8);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
