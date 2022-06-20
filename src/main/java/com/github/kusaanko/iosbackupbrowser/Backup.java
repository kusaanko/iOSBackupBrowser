package com.github.kusaanko.iosbackupbrowser;

import com.dd.plist.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;

public class Backup {
    private String deviceName;
    private Date backupDate;
    private boolean isEncrypted;
    private String productVersion;
    private String productType;
    private String buildVersion;
    private String serialNumber;
    private byte[] backupKeyBag;
    private byte[] manifestKey;

    private KeyBag keyBag;
    private byte[] decryptKey;
    private Path dir;

    public Backup(String deviceName, Date backupDate, boolean isEncrypted, String productVersion, String productType, String buildVersion, String serialNumber) {
        this.deviceName = deviceName;
        this.backupDate = backupDate;
        this.isEncrypted = isEncrypted;
        this.productVersion = productVersion;
        this.productType = productType;
        this.buildVersion = buildVersion;
        this.serialNumber = serialNumber;
    }

    public Backup(InputStream stream) throws PropertyListFormatException, IOException, ParseException, ParserConfigurationException, SAXException {
        NSDictionary dictionary = (NSDictionary) PropertyListParser.parse(stream);
        NSDictionary lockdown = (NSDictionary) dictionary.get("Lockdown");
        this.deviceName = lockdown.get("DeviceName").toString();
        this.productVersion = lockdown.get("ProductVersion").toString();
        this.productType = lockdown.get("ProductType").toString();
        this.buildVersion = lockdown.get("BuildVersion").toString();
        this.serialNumber = lockdown.get("SerialNumber").toString();
        this.backupDate = ((NSDate)dictionary.get("Date")).getDate();
        this.isEncrypted = ((NSNumber)dictionary.get("IsEncrypted")).boolValue();
        this.setBackupKeyBag(((NSData)dictionary.get("BackupKeyBag")).bytes());
        this.setManifestKey(((NSData)dictionary.get("ManifestKey")).bytes());
    }

    public boolean unlockWithPasscode(String passcode) {
        this.keyBag = new KeyBag(this.getBackupKeyBag());
        this.keyBag.printClassKeys();
        if(!this.keyBag.unlockWithPasscode(passcode)) {
            return false;
        }
        this.keyBag.printClassKeys();

        int manifestClass = ByteBuffer.wrap(Arrays.copyOfRange(this.getManifestKey(), 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
        this.decryptKey = this.keyBag.unwrapKeyForClass(manifestClass, Arrays.copyOfRange(this.getManifestKey(), 4, this.getManifestKey().length));
        return true;
    }

    public byte[] decryptData(byte[] data, int protectionClass, byte[] key) {
        key = this.keyBag.unwrapKeyForClass(protectionClass, key);
        return Util.AESDecryptCBC(data, key);
    }

    public String getDeviceName() {
        return deviceName;
    }

    public Date getBackupDate() {
        return backupDate;
    }

    public boolean isEncrypted() {
        return isEncrypted;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public String getProductType() {
        return productType;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public byte[] getBackupKeyBag() {
        return backupKeyBag;
    }

    public void setBackupKeyBag(byte[] backupKeyBag) {
        this.backupKeyBag = backupKeyBag;
    }

    public byte[] getManifestKey() {
        return manifestKey;
    }

    public void setManifestKey(byte[] manifestKey) {
        this.manifestKey = manifestKey;
    }

    public Path getDir() {
        return dir;
    }

    public void setDir(Path dir) {
        this.dir = dir;
    }
}
