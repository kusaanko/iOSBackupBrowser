package com.github.kusaanko.iosbackupbrowser.window;

import com.dd.plist.*;
import com.github.kusaanko.iosbackupbrowser.Backup;
import com.github.kusaanko.iosbackupbrowser.Util;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.text.ParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class FileListWindow extends JFrame {
    private Backup backup;

    public FileListWindow(Backup backup, Path manifestDBPath, String domain) {
        super();
        this.backup = backup;
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(domain);
        Map<String, DefaultMutableTreeNode> directories = new LinkedHashMap<>();
        try(Connection connection = DriverManager.getConnection("jdbc:sqlite:" + manifestDBPath.toString())) {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT relativePath, flags FROM Files WHERE domain = '" + domain + "'");
            while(rs.next()) {
                String path = rs.getString("relativePath");
                if(path.contains("/")) {
                    for(String parentPath : getParentPathArray(splitPath(path))) {
                        if(!directories.containsKey(parentPath)) {
                            DefaultMutableTreeNode node = directories.put(parentPath, new DefaultMutableTreeNode(parentPath));
                            directories.get(getParentPath(splitPath(parentPath))).add(node);
                        }
                    }
                }
                //if((rs.getInt("flags") & 0b10) > 0) {
                    if(!directories.containsKey(path)) {
                        DefaultMutableTreeNode node = new DefaultMutableTreeNode(getFileName(splitPath(path)));
                        directories.put(path, node);
                        if(path.contains("/")) {
                            directories.get(getParentPath(splitPath(path))).add(node);
                        }else {
                            rootNode.add(node);
                        }
                    }
               // }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem expandAll = new JMenuItem("Expand all");
        JMenuItem save = new JMenuItem("Save");
        popupMenu.add(expandAll);
        popupMenu.add(save);
        JTree jTree = new JTree(rootNode);
        jTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if(e.getButton() == MouseEvent.BUTTON3) {
                    popupMenu.show(jTree, e.getX(), e.getY());
                }
            }
        });
        expandAll.addActionListener(e -> {
            if(jTree.isSelectionEmpty()) return;
            int row = jTree.getSelectionRows()[0];
            String nodePath = toPath(jTree.getPathForRow(row));
            jTree.expandRow(row);
            row++;
            while(jTree.getPathForRow(row) != null && toPath(jTree.getPathForRow(row)).contains(nodePath)) {
                jTree.expandRow(row);
                row++;
            }
        });
        save.addActionListener(e -> {
            if(jTree.isSelectionEmpty()) return;
            int row = jTree.getSelectionRows()[0];
            String nodePath = toPath(jTree.getPathForRow(row));
            nodePath = nodePath.substring(0, nodePath.length() - 1);
            nodePath = nodePath.substring(nodePath.indexOf("/") + 1);
            System.out.println(nodePath);
            try(Connection connection = DriverManager.getConnection("jdbc:sqlite:" + manifestDBPath.toString())) {
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT relativePath, flags, fileID, file FROM Files WHERE domain = '" + domain + "' AND relativePath LIKE '" + nodePath + "%';");
                while(rs.next()) {
                    String path = rs.getString("relativePath");
                    String fileId = rs.getString("fileID");
                    byte[] file = rs.getBytes("file");
                    NSDictionary dictionary = (NSDictionary) PropertyListParser.parse(new ByteArrayInputStream(file));
                    NSDictionary fileData = (NSDictionary) ((NSArray) dictionary.get("$objects")).getArray()[new BigInteger(((UID)((NSDictionary)dictionary.get("$top")).get("root")).getBytes()).intValue()];
                    int size = ((NSNumber)fileData.get("Size")).intValue();
                    int protectionClass = ((NSNumber)fileData.get("ProtectionClass")).intValue();
                    byte[] encryptionKey = ((NSData)((NSDictionary)((NSArray)dictionary.get("$objects")).getArray()[new BigInteger(((UID)fileData.get("EncryptionKey")).getBytes()).intValue()]).get("NS.data")).bytes();
                    encryptionKey = Arrays.copyOfRange(encryptionKey, 4, encryptionKey.length);
                    int flag = rs.getInt("flags");
                    if((flag & 1) > 0) {
                        Path realPath = toRealPath(fileId);
                        try(InputStream stream = Files.newInputStream(realPath)) {
                            byte[] data = Util.readFromInputStream(stream);
                            byte[] decrypted = backup.decryptData(data, protectionClass, encryptionKey);
                            decrypted = Arrays.copyOfRange(decrypted, 0, size);
                            Files.createDirectories(toSavePath(domain, path).getParent());
                            Files.write(toSavePath(domain, path), decrypted);
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                }
            } catch (SQLException | IOException | PropertyListFormatException | ParseException | ParserConfigurationException | SAXException throwables) {
                throwables.printStackTrace();
            }
        });
        this.setLayout(new BorderLayout());
        this.add(new JScrollPane(jTree), BorderLayout.CENTER);
        this.setTitle(backup.getDeviceName() + "'s backup Domain:" + domain + " - iOSBackupBrowser");
        this.setSize(700, 800);
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.setVisible(true);
    }
    
    private String[] splitPath(String path) {
        return path.split("/");
    }
    
    private String getFileName(String[] path) {
        return path[path.length - 1];
    }

    private String getParentPath(String[] path) {
        return String.join("/", getPathArray(path));
    }

    private String[] getPathArray(String[] path) {
        return Arrays.copyOfRange(path, 0, path.length - 1);
    }

    private String[] getParentPathArray(String[] path) {
        String[] paths = new String[path.length - 1];
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < path.length - 1; i++) {
            String folder = path[i];
            paths[i] = builder.toString();
            if (builder.length() > 0) builder.append("/");
            builder.append(folder);
        }
        return paths;
    }

    public String toPath(TreePath treePath) {
        Object[] paths = treePath.getPath();
        StringBuilder ret = new StringBuilder();
        for(Object path : paths) {
            ret.append(path);
            ret.append("/");
        }
        return ret.toString();
    }

    public Path toRealPath(String fileID) {
        return backup.getDir().resolve(fileID.substring(0, 2)).resolve(fileID);
    }

    public Path toSavePath(String domain, String relativePath) {
        return Paths.get("decrypted", this.backup.getSerialNumber(), domain, relativePath);
    }
}
