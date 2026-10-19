#!/usr/bin/env python3
"""Mini-benchmark: R@5 com retrieval_v2 (6 casos). Nacional, não replicação LongMemEval."""
import argparse
import json
import re
import time
import unicodedata
import urllib.request
import sys
from pathlib import Path
from benchmark_client import post as authenticated_post
from uuid import uuid4

parser = argparse.ArgumentParser()
parser.add_argument("--base", default="http://localhost:8080")
args = parser.parse_args()
base = args.base

HERE = Path(__file__).parent

def post(path, body):
    return authenticated_post(base, path, body)

with open(Path(__file__).parent / "retrieval_v2.json", encoding="utf-8") as f:
    dataset = json.load(f)

results = []
passed = 0
for case in dataset["cases"]:
    user = f"bench-{uuid4().hex[:16]}"
    for msg in dataset["seeds"]:
        post(f"/api/conversations/{user}", {"conversationId":"s","messages":[msg]})
    reply = post(f"/api/assistant/{user}", {"conversationId":"c","message":case["message"]})
    context = [m["content"] for m in reply.get("context", [])]
    # Normalização idêntica ao run_retrieval_eval.py: sem acentos e em caixa baixa,
    # para não reprovar contexto correto por 'História' != 'historia' ou 'manhã' != 'manha'.
    normalized_context = [" ".join(c.split()).lower() for c in context]
    normalized_context = [unicodedata.normalize("NFKD", c) for c in normalized_context]
    normalized_context = ["".join(ch for ch in c if not unicodedata.combining(ch)) for c in normalized_context]
    expected = case.get("expected_any", [])
    forbidden = case.get("forbidden", [])
    expected = [unicodedata.normalize("NFKD", e.lower()) for e in expected]
    expected = ["".join(ch for ch in e if not unicodedata.combining(ch)) for e in expected]
    forbidden = [unicodedata.normalize("NFKD", f.lower()) for f in forbidden]
    forbidden = ["".join(ch for ch in f if not unicodedata.combining(ch)) for f in forbidden]
    # R@5: memória esperada aparece entre as 5 primeiras?
    found = any(any(e in c for e in expected) for c in normalized_context[:5])
    violations = any(any(f in c for f in forbidden) for c in normalized_context[:5])
    ok = found and not violations
    if ok: passed += 1
    results.append({"id":case["id"],"passed":ok,"context":context,"expected":expected,"forbidden":forbidden})

out = HERE / "results" / f"benchmark_mini_{int(time.time())}.json"
out.parent.mkdir(exist_ok=True)
out.write_text(json.dumps({"dataset":"retrieval_v2","passed":passed,"total":len(dataset["cases"]),"results":results}, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Benchmark mini: {passed}/{len(dataset['cases'])}  -> {out}")
