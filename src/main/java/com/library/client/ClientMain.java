package com.library.client;

import com.library.client.ui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // dùng look and feel mặc định của Java cũng được
            }
            new LoginFrame().setVisible(true);
        });
    }
}
