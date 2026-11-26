# Rsyncify

A clean JavaFX backup tool powered by **rsync** and **tar**.

### Features
- Incremental backups (rsync)
- Archive creation (tar + optional gzip)
- Real-time progress & activity log
- Pause / Resume / Stop
- Bandwidth limit & dry-run mode

### Requirements
- Java 17+
- `rsync` and `tar` installed

### Run
```bash
git clone https://github.com/YOUR_USERNAME/rsyncify.git
cd rsyncify
mvn clean javafx:run
