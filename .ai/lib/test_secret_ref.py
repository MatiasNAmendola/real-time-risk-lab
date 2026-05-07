"""Tests for secret_ref.py"""

import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from secret_ref import (
    is_secret_ref,
    mask_secrets,
    resolve_in_place,
    resolve_secret_ref,
)


class TestIsSecretRef(unittest.TestCase):
    def test_valid_env_ref(self):
        self.assertTrue(is_secret_ref({"source": "env", "id": "MY_TOKEN"}))

    def test_valid_file_ref(self):
        self.assertTrue(is_secret_ref({"source": "file", "id": "/run/secrets/token"}))

    def test_random_dict_not_ref(self):
        self.assertFalse(is_secret_ref({"host": "localhost", "port": 5432}))

    def test_extra_keys_not_ref(self):
        self.assertFalse(is_secret_ref({"source": "env", "id": "X", "extra": "y"}))

    def test_list_not_ref(self):
        self.assertFalse(is_secret_ref(["source", "id"]))

    def test_none_not_ref(self):
        self.assertFalse(is_secret_ref(None))

    def test_string_not_ref(self):
        self.assertFalse(is_secret_ref("env:MY_TOKEN"))


class TestResolveEnv(unittest.TestCase):
    def test_resolve_env_set(self):
        os.environ["_TEST_SECRET_REF_TOKEN"] = "my-secret-value"
        try:
            result = resolve_secret_ref({"source": "env", "id": "_TEST_SECRET_REF_TOKEN"})
            self.assertEqual(result, "my-secret-value")
        finally:
            del os.environ["_TEST_SECRET_REF_TOKEN"]

    def test_resolve_env_missing(self):
        # Ensure the var is not set
        os.environ.pop("_TEST_SECRET_REF_MISSING", None)
        with self.assertRaises(ValueError) as ctx:
            resolve_secret_ref({"source": "env", "id": "_TEST_SECRET_REF_MISSING"})
        self.assertIn("_TEST_SECRET_REF_MISSING", str(ctx.exception))

    def test_resolve_unsupported_source(self):
        with self.assertRaises(ValueError) as ctx:
            resolve_secret_ref({"source": "vault", "id": "secret/path"})
        self.assertIn("unsupported source", str(ctx.exception))


class TestResolveFile(unittest.TestCase):
    def test_resolve_file(self):
        with tempfile.NamedTemporaryFile(mode="w", suffix=".txt", delete=False) as f:
            f.write("file-secret-value\n")
            tmp_path = f.name
        try:
            result = resolve_secret_ref({"source": "file", "id": tmp_path})
            self.assertEqual(result, "file-secret-value")
        finally:
            Path(tmp_path).unlink()

    def test_resolve_file_strips_whitespace(self):
        with tempfile.NamedTemporaryFile(mode="w", suffix=".txt", delete=False) as f:
            f.write("  padded-value  \n\n")
            tmp_path = f.name
        try:
            result = resolve_secret_ref({"source": "file", "id": tmp_path})
            self.assertEqual(result, "padded-value")
        finally:
            Path(tmp_path).unlink()


class TestResolveInPlace(unittest.TestCase):
    def test_resolve_nested_config(self):
        os.environ["_TEST_DB_PASS"] = "supersecret"
        os.environ["_TEST_API_KEY"] = "key-xyz"
        try:
            config = {
                "database": {
                    "host": "localhost",
                    "password": {"source": "env", "id": "_TEST_DB_PASS"},
                },
                "service": {
                    "api_key": {"source": "env", "id": "_TEST_API_KEY"},
                    "retries": 3,
                },
            }
            result = resolve_in_place(config)
            self.assertEqual(result["database"]["password"], "supersecret")
            self.assertEqual(result["service"]["api_key"], "key-xyz")
            self.assertEqual(result["database"]["host"], "localhost")
            self.assertEqual(result["service"]["retries"], 3)
            # Original not mutated
            self.assertIsInstance(config["database"]["password"], dict)
        finally:
            del os.environ["_TEST_DB_PASS"]
            del os.environ["_TEST_API_KEY"]

    def test_resolve_in_list(self):
        os.environ["_TEST_LIST_TOKEN"] = "list-token-value"
        try:
            config = {"tokens": [{"source": "env", "id": "_TEST_LIST_TOKEN"}, "plain"]}
            result = resolve_in_place(config)
            self.assertEqual(result["tokens"][0], "list-token-value")
            self.assertEqual(result["tokens"][1], "plain")
        finally:
            del os.environ["_TEST_LIST_TOKEN"]

    def test_original_not_mutated(self):
        os.environ["_TEST_IMMUTABLE"] = "val"
        try:
            config = {"x": {"source": "env", "id": "_TEST_IMMUTABLE"}}
            _ = resolve_in_place(config)
            self.assertIsInstance(config["x"], dict)
        finally:
            del os.environ["_TEST_IMMUTABLE"]


class TestMaskSecrets(unittest.TestCase):
    def test_masks_password_key(self):
        config = {"username": "alice", "password": "hunter2"}
        result = mask_secrets(config)
        self.assertEqual(result["password"], "***")
        self.assertEqual(result["username"], "alice")

    def test_masks_token_key(self):
        config = {"api_token": "tok_abc123", "timeout": 30}
        result = mask_secrets(config)
        self.assertEqual(result["api_token"], "***")
        self.assertEqual(result["timeout"], 30)

    def test_benign_keys_not_masked(self):
        config = {"host": "db.example.com", "port": 5432, "database": "mydb"}
        result = mask_secrets(config)
        self.assertEqual(result["host"], "db.example.com")
        self.assertEqual(result["port"], 5432)

    def test_nested_masking(self):
        config = {
            "db": {
                "host": "localhost",
                "password": "secret123",
                "credentials": {"api_key": "key-999"},
            }
        }
        result = mask_secrets(config)
        self.assertEqual(result["db"]["password"], "***")
        self.assertEqual(result["db"]["credentials"]["api_key"], "***")
        self.assertEqual(result["db"]["host"], "localhost")

    def test_original_not_mutated(self):
        config = {"password": "real-value"}
        _ = mask_secrets(config)
        self.assertEqual(config["password"], "real-value")

    def test_numeric_sensitive_value_masked(self):
        config = {"api_key": 123456}
        result = mask_secrets(config)
        self.assertEqual(result["api_key"], "***")


if __name__ == "__main__":
    unittest.main()
