package com.github.kusaanko.iosbackupbrowser.window;

import com.github.kusaanko.iosbackupbrowser.Backup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FileBrowser extends JFrame {
    public FileBrowser(Backup backup, Path manifestDBPath) {
        super();
        List<String> domains = new ArrayList<>();
        {
            try(Connection connection = DriverManager.getConnection("jdbc:sqlite:" + manifestDBPath.toString())) {
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT DISTINCT domain FROM Files");
                while(rs.next()) {
                    domains.add(rs.getString("domain"));
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        JList<String> jList = new JList<>(domains.toArray(new String[0]));
        jList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if(e.getClickCount() >= 2 && jList.getSelectedValue() != null) {
                    new FileListWindow(backup, manifestDBPath, jList.getSelectedValue());
                }
            }
        });
        this.setLayout(new BorderLayout());
        this.add(new JScrollPane(jList), BorderLayout.CENTER);
        this.setTitle(backup.getDeviceName() + "'s backup - iOSBackupBrowser");
        this.setSize(700, 800);
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setVisible(true);
    }
}
