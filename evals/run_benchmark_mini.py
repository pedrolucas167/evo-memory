#!/usr/bin/env python3
"""Mini-benchmark: R@5 com retrieval_v2 (6 casos). Nacional, não replicação LongMemEval."""
import json, time, urllib.request, sys
from pathlib import Path
from benchmark_client import post as authenticated_post
from uuid import uuid4

HERE = Path(__file__).parent
DATA = HERE / "retrieval_v2.json"
base = "http://localhost:8080"

def post(path, body):
    return authenticated_post(base, path, body)

with open(DATA, encoding="utf-8") as f:
    dataset = json.load(f)

results = []
passed = 0
for case in dataset["cases"]:
    user = f"bench-{uuid4().hex[:16]}"
    for msg in dataset["seeds"]:
        post(f"/api/conversations/{user}", {"conversationId":"s","messages":[msg]})
    reply = post(f"/api/assistant/{user}", {"conversationId":"c","message":case["message"]})
    context = [m["content"] for m in reply.get("context", [])]
    expected = case.get("expected_any", [])
    forbidden = case.get("forbidden", [])
    # R@5: memória esperada aparece entre as 5 primeiras?
    found = any(any(e in c for e in expected) for c in context[:5])
    violations = any(any(f in c for f in forbidden) for c in context[:5])
    ok = found and not violations
    if ok: passed += 1
    results.append({"id":case["id"],"passed":ok,"context":context,"expected":expected,"forbidden":forbidden})

out = HERE / "results" / f"benchmark_mini_{int(time.time())}.json"
out.parent.mkdir(exist_ok=True)
out.write_text(json.dumps({"dataset":"retrieval_v2","passed":passed,"total":len(dataset["cases"]),"results":results}, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Benchmark mini: {passed}/{len(dataset['cases'])}  -> {out}")
