"""Unit tests for tools/check-ux-backlog-json.py."""
from __future__ import annotations

import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHECKER = ROOT / "tools" / "check-ux-backlog-json.py"


def load_validator():
    spec = importlib.util.spec_from_file_location("check_ux_backlog_json", CHECKER)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module.validate_backlog_text


validate_backlog_text = load_validator()


class UxBacklogJsonTest(unittest.TestCase):
    def test_valid_minimal_object(self):
        self.assertEqual(validate_backlog_text('{"a": 1}'), [])

    def test_rejects_conflict_marker_at_line_start(self):
        text = '{"items": []}\n<<<<<<< Updated upstream\n'
        self.assertTrue(any("conflict marker" in err for err in validate_backlog_text(text)))

    def test_ignores_equals_inside_string(self):
        text = json.dumps({"note": "======= not a marker"})
        self.assertEqual(validate_backlog_text(text), [])

    def test_rejects_duplicate_keys(self):
        text = '{"id": "first", "id": "second"}'
        errors = validate_backlog_text(text)
        self.assertTrue(any("duplicate keys" in err for err in errors))

    def test_rejects_invalid_json(self):
        errors = validate_backlog_text("{not json")
        self.assertTrue(any("invalid JSON" in err for err in errors))

    def test_real_backlog_file(self):
        backlog = ROOT / "docs" / "ux-program" / "UX-Backlog.json"
        text = backlog.read_text(encoding="utf-8")
        self.assertEqual(validate_backlog_text(text), [])


if __name__ == "__main__":
    unittest.main()
