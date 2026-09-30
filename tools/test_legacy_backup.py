import io
import json
from pathlib import Path
import sqlite3
import tarfile
import tempfile
import unittest

from backup_legacy_context import DATABASE, convert_archive, encode_backup


def fixture_archive(scope=1, unsafe=False, wal=False):
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / DATABASE
        db = sqlite3.connect(path)
        if wal:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("PRAGMA wal_autocheckpoint=0")
        db.executescript("""
            CREATE TABLE chats(id INTEGER, title TEXT, language TEXT, topic TEXT, level TEXT, tutorRole TEXT, correctionMode TEXT, customPrompt TEXT, voiceName TEXT, voiceStyle TEXT, brainMode TEXT, createdAt INTEGER, updatedAt INTEGER, summary TEXT, archived INTEGER, pinned INTEGER);
            INSERT INTO chats VALUES(1, 'အရင်သင်ခန်းစာ', 'KOREAN', 'Factory', 'Beginner', 'TEACHER', 'AFTER_REPLY', '', 'Kore', 'Calm', 'GEMINI_ONLY', 1, 1, 'Old notes', 0, 1);
            CREATE TABLE messages(id INTEGER, chatId INTEGER, role TEXT, content TEXT, translation TEXT, correction TEXT, explanation TEXT, createdAt INTEGER);
            INSERT INTO messages VALUES(1, 1, 'USER', '안녕하세요.', '', '', '', 1);
            CREATE TABLE memories(id INTEGER, title TEXT, content TEXT, category TEXT, scopeChatId INTEGER, enabled INTEGER, createdAt INTEGER, updatedAt INTEGER);
            CREATE TABLE knowledge_sources(id INTEGER, name TEXT, mimeType TEXT, uri TEXT, summary TEXT, enabled INTEGER, createdAt INTEGER);
            INSERT INTO knowledge_sources VALUES(1, 'Old document', 'text/plain', 'content://private/document', 'Saved summary', 1, 1);
            CREATE TABLE private_keys(secret TEXT);
            INSERT INTO private_keys VALUES('secret-should-not-be-exported');
        """)
        db.execute("INSERT INTO memories VALUES(1, 'Shift', 'Day shift', 'Personal', ?, 1, 1, 1)", (scope,))
        db.commit()
        if not wal:
            db.close()
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode="w") as tar:
            for filename in (DATABASE, DATABASE + "-wal", DATABASE + "-shm"):
                source = Path(directory) / filename
                if source.exists():
                    tar.add(source, arcname="databases/" + filename)
            if unsafe:
                entry = tarfile.TarInfo("../../escape")
                entry.size = 1
                tar.addfile(entry, io.BytesIO(b"x"))
        if wal:
            db.close()
        return output.getvalue()


class LegacyBackupTest(unittest.TestCase):
    def test_legacy_rows_become_portable_backup_without_credentials(self):
        result = convert_archive(fixture_archive())
        self.assertEqual("language-talk-backup", result["format"])
        self.assertIs(result["chats"][0]["pinned"], True)
        self.assertIs(result["chats"][0]["archived"], False)
        self.assertIs(result["memories"][0]["enabled"], True)
        self.assertEqual(1, result["memories"][0]["scopeChatId"])
        self.assertEqual("안녕하세요.", result["messages"][0]["content"])
        self.assertEqual("", result["sources"][0]["uri"])
        data = encode_backup(result).decode("utf-8")
        self.assertIn("အရင်သင်ခန်းစာ", data)
        self.assertNotIn("secret-should-not-be-exported", data)
        self.assertEqual(result, json.loads(data))

    def test_uncheckpointed_wal_is_preserved(self):
        result = convert_archive(fixture_archive(wal=True))
        self.assertEqual("Day shift", result["memories"][0]["content"])

    def test_missing_chat_scope_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "missing chat references"):
            convert_archive(fixture_archive(scope=99))

    def test_archive_path_traversal_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unsafe entry"):
            convert_archive(fixture_archive(unsafe=True))


if __name__ == "__main__":
    unittest.main()
