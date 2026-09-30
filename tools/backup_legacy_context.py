"""Export a debuggable Language Talk installation without deleting phone data.

The Windows release includes adb. Source users need Python 3.10+ and Android
Platform Tools. No shell, network service, app installation or uninstallation is
performed. The app is stopped to obtain a consistent SQLite database snapshot.
"""
import argparse
import base64
import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import sqlite3
import subprocess
import sys
import tarfile
import tempfile
import threading

PACKAGE = "com.myothuonion.languagetalk"
DATABASE = "language-talk.db"
MAX_BYTES = 32 * 1024 * 1024
PROFILE = "I am a Myanmar-speaking learner living in Korea. I need practical Korean for factory work and daily life. I have 30 minutes a day. Explain briefly in Myanmar when needed."
FIELDS = {
    "chats": "id title language topic level tutorRole correctionMode customPrompt voiceName voiceStyle brainMode createdAt updatedAt summary archived pinned practiceMode speakingPace silenceMs speakCorrections".split(),
    "messages": "id chatId role content translation correction explanation createdAt audioPath audioMimeType".split(),
    "memories": "id title content category scopeChatId enabled createdAt updatedAt".split(),
    "knowledge_sources": "id name mimeType uri summary enabled createdAt".split(),
    "learning_progress": "chatId goal stage targetSentence completed summary lastCorrection updatedAt".split(),
    "review_items": "id chatId sentence correction dueAt successes updatedAt".split(),
}
BOOLEAN_FIELDS = {"archived", "pinned", "enabled", "speakCorrections"}


def convert_archive(archive: bytes) -> dict:
    """Read only the named DB/WAL and recording files; never extract tar paths."""
    if len(archive) > 64 * 1024 * 1024:
        raise ValueError("App data is too large for this backup helper.")
    recordings = {}
    with tempfile.TemporaryDirectory() as directory:
        with tarfile.open(fileobj=io.BytesIO(archive), mode="r:") as tar:
            seen = set()
            total = 0
            for member in tar:
                path = PurePosixPath(member.name)
                if path.is_absolute() or ".." in path.parts or member.issym() or member.islnk():
                    raise ValueError("Unsafe entry in app backup.")
                name = path.as_posix()
                db_file = name in {f"databases/{DATABASE}", f"databases/{DATABASE}-wal", f"databases/{DATABASE}-shm"}
                audio_file = len(path.parts) == 3 and path.parts[:2] == ("files", "practice-recordings")
                if not member.isfile() or not (db_file or audio_file):
                    continue
                if name in seen:
                    raise ValueError("Duplicate entry in app backup.")
                seen.add(name)
                total += member.size
                if member.size < 0 or total > 64 * 1024 * 1024 or (audio_file and member.size > 4 * 1024 * 1024):
                    raise ValueError("App backup contains oversized files.")
                source = tar.extractfile(member)
                if source is None:
                    raise ValueError("Missing backup file.")
                data = source.read()
                if db_file:
                    (Path(directory) / path.name).write_bytes(data)
                else:
                    recordings[path.name] = data
        database_path = Path(directory) / DATABASE
        if not database_path.exists():
            raise ValueError("Language Talk database was not found. Keep the old app installed and open it once.")
        db = sqlite3.connect(database_path)
        db.row_factory = sqlite3.Row
        try:
            if db.execute("PRAGMA quick_check").fetchone()[0] != "ok":
                raise ValueError("Database is damaged; no backup was saved.")
            tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            if not {"chats", "messages", "memories", "knowledge_sources"}.issubset(tables):
                raise ValueError("Unrecognized Language Talk database.")
            result = {"format": "language-talk-backup", "version": 1}
            aliases = {"knowledge_sources": "sources", "learning_progress": "progress", "review_items": "reviews"}
            for table, fields in FIELDS.items():
                rows = []
                if table in tables:
                    for source in db.execute(f'SELECT * FROM "{table}"'):
                        row = {field: source[field] for field in fields if field in source.keys()}
                        for field in BOOLEAN_FIELDS & row.keys():
                            row[field] = bool(row[field])
                        if table == "knowledge_sources":
                            row["uri"] = ""
                        rows.append(row)
                result[aliases.get(table, table)] = rows
            result["audio"] = []
            for message in result["messages"]:
                name = message.get("audioPath", "")
                if name and name == PurePosixPath(name).name and name in recordings:
                    result["audio"].append({"messageId": message["id"], "data": base64.b64encode(recordings[name]).decode("ascii")})
                else:
                    message["audioPath"] = ""
            result["preferences"] = {"profile": PROFILE}
            validate_backup(result)
            return result
        finally:
            db.close()


def encode_backup(backup: dict) -> bytes:
    data = json.dumps(backup, ensure_ascii=False, indent=2).encode("utf-8")
    if len(data) > MAX_BYTES:
        raise ValueError("Backup exceeds the app's 32 MB import limit.")
    return data


def validate_backup(backup: dict) -> None:
    for table, limit in (("chats", 2000), ("messages", 20000), ("memories", 3000)):
        if len(backup[table]) > limit:
            raise ValueError("Too many records for app restore.")
        ids = [row["id"] for row in backup[table]]
        if len(set(ids)) != len(ids) or any(value <= 0 for value in ids):
            raise ValueError("Invalid or duplicate record IDs.")
    chats = {row["id"] for row in backup["chats"]}
    rows = backup["messages"] + backup["progress"] + backup["reviews"]
    if any(row["chatId"] not in chats for row in rows) or any(row.get("scopeChatId") is not None and row["scopeChatId"] not in chats for row in backup["memories"]):
        raise ValueError("Database contains missing chat references.")
    encode_backup(backup)


def find_adb() -> str:
    root = Path(getattr(sys, "_MEIPASS", Path(__file__).parent))
    bundled = root / "platform-tools" / "adb.exe"
    if bundled.is_file():
        return str(bundled)
    path = shutil.which("adb")
    if path:
        return path
    raise ValueError("Install Android Platform Tools and add adb to PATH, or use the Windows backup helper release.")


def run_adb(adb: str, arguments: list[str], serial: str | None = None, timeout: int = 90) -> bytes:
    command = [adb] + (["-s", serial] if serial else []) + arguments
    result = subprocess.run(command, capture_output=True, timeout=timeout, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    if result.returncode:
        raise ValueError(result.stderr.decode("utf-8", errors="replace").strip() or "Phone backup command failed.")
    return result.stdout


def export_phone(destination: Path, serial: str | None = None) -> dict:
    adb = find_adb()
    devices = []
    output = run_adb(adb, ["devices"]).decode("utf-8", errors="replace")
    for line in output.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    if serial is None:
        if len(devices) != 1:
            raise ValueError("Connect one unlocked phone, enable USB debugging and accept the phone's Allow USB debugging prompt. Disconnect other phones/emulators.")
        serial = devices[0]
    elif serial not in devices:
        raise ValueError("The selected phone is not connected and authorized.")
    run_adb(adb, ["shell", "run-as", PACKAGE, "id"], serial)
    run_adb(adb, ["shell", "am", "force-stop", PACKAGE], serial)
    # Shared preferences (including encrypted keys) are deliberately not requested.
    archive = run_adb(adb, ["exec-out", "run-as", PACKAGE, "tar", "-c", "-f", "-", "databases", "files"], serial)
    try:
        backup = convert_archive(archive)
    except tarfile.ReadError as failure:
        raise ValueError("Phone data could not be read. Keep the old app installed and use the debuggable APK from this repository.") from failure
    data = encode_backup(backup)
    # Avoid leaving a partial backup if the write fails.
    with tempfile.NamedTemporaryFile(dir=destination.parent, suffix=".tmp", delete=False) as file:
        temporary = Path(file.name)
        try:
            file.write(data)
            file.flush()
            os.fsync(file.fileno())
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise
    try:
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    return backup


def show_window() -> None:
    import tkinter as tk
    from tkinter import filedialog, messagebox
    root = tk.Tk()
    root.title("Language Talk — Save old chats")
    root.geometry("570x350")
    text = "Keep v0.4.0 installed until your backup succeeds.\n\n1. Enable USB debugging on your Android phone.\n2. Connect it by USB and allow this computer on the phone.\n3. Click Save backup and choose a JSON file on this computer.\n\nThen install v0.5.0 and use My Context → Restore backup.\nThis helper stops the app but never deletes or installs anything.\nAPI key settings and original imported documents are excluded."
    tk.Label(root, text=text, justify="left", wraplength=530, padx=20, pady=20).pack(anchor="w")
    status = tk.StringVar(value="Ready to save chats and memories.")
    tk.Label(root, textvariable=status, wraplength=530).pack(padx=20)
    def save():
        filename = filedialog.asksaveasfilename(defaultextension=".json", initialfile="language-talk-backup.json", filetypes=[("Language Talk backup", "*.json")])
        if not filename:
            return
        button.configure(state="disabled")
        status.set("Reading phone data…")
        def done(backup=None, failure=None):
            button.configure(state="normal")
            if failure:
                status.set("Backup failed. Your phone data is still there.")
                messagebox.showerror("Backup failed", str(failure))
            else:
                status.set(f"Saved {len(backup['chats'])} chats and {len(backup['memories'])} memories.")
                messagebox.showinfo("Backup saved", f"Saved and validated:\n{filename}\n\nKeep this JSON file. Restore it in v0.5.0 before deleting your backup.")
        def work():
            try:
                backup = export_phone(Path(filename))
                root.after(0, lambda: done(backup=backup))
            except Exception as error:
                root.after(0, lambda failure=error: done(failure=failure))
        threading.Thread(target=work, daemon=True).start()
    button = tk.Button(root, text="Save backup", command=save, padx=24, pady=8)
    button.pack(pady=16)
    root.mainloop()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="Save backup without opening the window")
    parser.add_argument("--serial", help="Authorized adb phone serial")
    arguments = parser.parse_args()
    if arguments.output:
        backup = export_phone(arguments.output, arguments.serial)
        print(f"Saved {len(backup['chats'])} chats, {len(backup['messages'])} messages and {len(backup['memories'])} memories to {arguments.output}")
    else:
        show_window()


if __name__ == "__main__":
    main()
