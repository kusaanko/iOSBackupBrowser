package com.github.kusaanko.iosbackupbrowser.window;

import com.github.kusaanko.iosbackupbrowser.Backup;
import com.github.kusaanko.iosbackupbrowser.Util;
import com.github.kusaanko.iosbackupbrowser.iOSBackupBrowser;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BackupChooser extends JFrame {
    public BackupChooser() {
        super("iOSBackupBrowser");
        this.setLayout(new BorderLayout());
        String[] columnName = {"Device Name", "iOS Version", "Build Number", "Backup Date", "Serial Number"};
        List<String[]> devices = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
        for(Backup backup : iOSBackupBrowser.backups) {
            devices.add(new String[]{
                    backup.getDeviceName(),
                    backup.getProductVersion(),
                    backup.getBuildVersion(),
                    dateFormat.format(backup.getBackupDate()),
                    backup.getSerialNumber()
            });
        }
        JTable jTable = new JTable();
        jTable.setModel(new DefaultTableModel(devices.toArray(new String[0][0]), columnName){
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        jTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if(e.getClickCount() >= 2) {
                    if(jTable.getSelectedRow() >= 0) {
                        Backup backup = iOSBackupBrowser.backups.get(jTable.getSelectedRow());
                        if(backup.isEncrypted()) {
                            String passcode = JOptionPane.showInputDialog(BackupChooser.this, "Enter " + backup.getDeviceName() + "'s backup passcode");
                            if(passcode != null) {
                                jTable.setEnabled(false);
                                new Thread(() -> {
                                    if(!backup.unlockWithPasscode(passcode)) {
                                        JOptionPane.showMessageDialog(BackupChooser.this, "Your passcode was incorrect.");
                                        jTable.setEnabled(true);
                                    }else {
                                        // Decode Manifest.db
                                        byte[] manifestDB;
                                        try {
                                            try (InputStream manifestStream = Files.newInputStream(backup.getDir().resolve("Manifest.db"))) {
                                                manifestDB = Util.readFromInputStream(manifestStream);
                                            }
                                            int manifestClass = ByteBuffer.wrap(Arrays.copyOfRange(backup.getManifestKey(), 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
                                            byte[] key = Arrays.copyOfRange(backup.getManifestKey(), 4, backup.getManifestKey().length);
                                            manifestDB = backup.decryptData(manifestDB, manifestClass, key);
                                            if(!Files.exists(Paths.get("decrypted/"))) Files.createDirectories(Paths.get("decrypted/"));
                                            Files.write(Paths.get("decrypted", backup.getSerialNumber() + ".db"), manifestDB);
                                            BackupChooser.this.dispose();
                                            new FileBrowser(backup, Paths.get(backup.getSerialNumber() + ".db"));
                                        }catch (IOException ex) {
                                            ex.printStackTrace();
                                        }
                                    }
                                }).start();
                            }
                        }
                    }
                }
            }
        });
        this.add(new JScrollPane(jTable), BorderLayout.CENTER);
        this.setSize(700, 300);
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setVisible(true);
    }
}
