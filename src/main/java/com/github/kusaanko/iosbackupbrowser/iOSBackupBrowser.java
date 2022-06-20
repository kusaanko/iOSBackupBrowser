package com.github.kusaanko.iosbackupbrowser;

import com.dd.plist.*;
import com.github.kusaanko.iosbackupbrowser.window.BackupChooser;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;
import java.util.Base64;

public class iOSBackupBrowser {
    public static List<String> backupDirectories = new ArrayList<>(Collections.singletonList("~/Library/Application Support/MobileSync/Backup/"));
    public static List<Path> backupPaths;
    public static List<Backup> backups;

    public static void main(String[] args) {
        // Add backup directories
        backupDirectories.add(System.getenv("USERPROFILE") + "\\Apple\\MobileSync\\Backup");
        backupDirectories.add(System.getenv("USERPROFILE") + "\\Apple Computer\\MobileSync\\Backup");
        backupDirectories.add(System.getenv("appdata") + "\\Apple\\MobileSync\\Backup");
        backupDirectories.add(System.getenv("appdata") + "\\Apple Computer\\MobileSync\\Backup");
        //Find backups
        backupPaths = new ArrayList<>();
        for(String dir : backupDirectories) {
            Path pathDir = Paths.get(dir);
            if(Files.exists(pathDir)) {
                try {
                    Files.list(pathDir).forEach(path -> {
                        if(Files.exists(path.resolve("Manifest.plist"))) {
                            backupPaths.add(path);
                        }
                    });
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        backups = new ArrayList<>();
        for(Path backupDir : backupPaths) {
            Backup backup;
            try {
                backup = new Backup(Files.newInputStream(backupDir.resolve("Manifest.plist")));
                backup.setDir(backupDir);
                backups.add(backup);
            } catch (PropertyListFormatException | ParseException | IOException | ParserConfigurationException | SAXException e) {
                e.printStackTrace();
            }
        }
        new BackupChooser();
    }
}
