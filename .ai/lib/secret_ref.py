"""
Late-binding secret resolution for agent configs.

Secrets are represented as descriptors at config-load time and resolved
to their actual values only when needed. Logging uses mask_secrets() to
avoid persisting sensitive values.
"""

from pathlib import Path
import os
import copy


def is_secret_ref(value) -> bool:
    """True if value is a {source, id} descriptor."""
    return isinstance(value, dict) and set(value.keys()) == {"source", "id"}


def resolve_secret_ref(ref: dict) -> str:
    """Resolves a single ref against env or file.

    Supported sources:
      env  — reads os.environ[id]
      file — reads file at path id, strips trailing whitespace
    """
    source = ref["source"]
    id_ = ref["id"]
    if source == "env":
        val = os.environ.get(id_)
        if val is None:
            raise ValueError(f"env var {id_} not set")
        return val
    elif source == "file":
        return Path(id_).read_text().strip()
    else:
        raise ValueError(f"unsupported source: {source}")


def resolve_in_place(config: dict) -> dict:
    """Walks config recursively, replaces secret refs with resolved values.

    Returns a deep copy with all {source, id} descriptors replaced by
    their resolved string values. The original dict is not modified.
    """
    result = copy.deepcopy(config)
    _walk(result)
    return result


def _walk(node):
    if isinstance(node, dict):
        for k, v in list(node.items()):
            if is_secret_ref(v):
                node[k] = resolve_secret_ref(v)
            else:
                _walk(v)
    elif isinstance(node, list):
        for i, item in enumerate(node):
            if is_secret_ref(item):
                node[i] = resolve_secret_ref(item)
            else:
                _walk(item)


SENSITIVE_KEY_HINTS = (
    "secret",
    "token",
    "key",
    "password",
    "credential",
    "api_key",
    "apikey",
    "pwd",
    "auth",
)


def mask_secrets(config: dict) -> dict:
    """Returns a copy of config with sensitive values masked.

    Any scalar value whose key contains a sensitive hint (case-insensitive)
    is replaced with '***'. Nested structures are walked recursively.
    """
    result = copy.deepcopy(config)
    _mask_walk(result)
    return result


def _mask_walk(node):
    if isinstance(node, dict):
        for k in node:
            if isinstance(node[k], (str, int, float)) and any(
                h in k.lower() for h in SENSITIVE_KEY_HINTS
            ):
                node[k] = "***"
            else:
                _mask_walk(node[k])
    elif isinstance(node, list):
        for item in node:
            _mask_walk(item)
