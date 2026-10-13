"""Authenticated local benchmark requests using only the standard library."""
import base64
import hashlib
import hmac
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def local_token(user, key_file=None):
    path = Path(key_file or os.environ.get("AUTH_LOCAL_KEY_FILE", ROOT / ".local/auth-secret"))
    key = base64.b64decode(path.read_text(encoding="utf-8").strip(), validate=True)
    if len(key) < 32:
        raise ValueError("Local benchmark key must have at least 256 bits")

    def encode(value):
        raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
        return base64.urlsafe_b64encode(raw).rstrip(b"=")

    now = int(time.time())
    unsigned = encode({"alg": "HS256", "typ": "JWT"}) + b"." + encode({
        "iss": "evo-memory-local", "aud": "evo-memory", "sub": user, "iat": now, "exp": now + 300,
    })
    signature = base64.urlsafe_b64encode(hmac.new(key, unsigned, hashlib.sha256).digest()).rstrip(b"=")
    return (unsigned + b"." + signature).decode("ascii")


def request(base, path, body=None, method="POST", attempts=3):
    parsed = urllib.parse.urlparse(base)
    if parsed.scheme not in ("http", "https") or parsed.hostname not in ("127.0.0.1", "localhost", "::1"):
        raise ValueError("Local benchmark credentials can only be sent to a loopback address")
    parts = path.split("/")
    if len(parts) < 4 or parts[1] != "api":
        raise ValueError("Expected an API path containing a user identity")
    user = urllib.parse.unquote(parts[3])
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    for attempt in range(attempts):
        headers = {"Content-Type": "application/json; charset=utf-8", "Authorization": "Bearer " + local_token(user)}
        req = urllib.request.Request(base.rstrip("/") + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=180) as response:
                raw = response.read()
                return json.loads(raw.decode("utf-8")) if raw else None
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == attempts - 1:
                raise RuntimeError(f"Benchmark API returned HTTP {error.code}") from None
            delay = error.headers.get("Retry-After", "5")
            time.sleep(min(60, int(delay)) if delay.isdigit() else 5)
    raise RuntimeError("Benchmark API request failed")


def post(base, path, body, attempts=3):
    return request(base, path, body, attempts=attempts)
