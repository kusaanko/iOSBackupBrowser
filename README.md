# iOSBackupBrowser
![Tested iOS Version 15.5](https://img.shields.io/badge/Tested%20iOS%20Version-15.5-green) ![Tested Desktop OS Windows](https://img.shields.io/badge/Tested%20Desktop%20OS-Windows-blue)  ![Tested iTuens Version 12.12.4](https://img.shields.io/badge/Tested%20iTuens%20Version-12.12.4-green) 

Work in Progress

This software enables you to decrypt iOS backup and browse backup files. This only works with encrypted iOS backups.

This includes a Java port of andrewdotn's python code.

# Features
 - Decrypt iOS backup
 - Browse iOS backup files

# How to run
```bash
./gradlew run
```
Runnable package is not served.

# How to use
 - Select backup and double click
 - Enter your backup passcode
 - Choose domain
 - Select file and right click, then click Save.
 - You'll find decrypted data at `decrypted/[Serial Number]/[Domain]/[File]`

# References
 - https://stackoverflow.com/questions/1498342/how-to-decrypt-an-encrypted-apple-itunes-iphone-backup/13793043#13793043
 - https://code.google.com/archive/p/iphone-dataprotection/