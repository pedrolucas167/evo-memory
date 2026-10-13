"""Small, authenticated temporal-memory experiment. Uses synthetic users."""
import argparse
import hashlib
import json
import subprocess
import time
import unicodedata
import uuid
import os
import urllib.request
from pathlib import Path
from benchmark_client import post, request

HERE = Path(__file__).resolve().parent


def normalize(text):
    return "".join(c for c in unicodedata.normalize("NFKD", text.lower()) if not unicodedata.combining(c))


def git_state():
    def git(*args):
        return subprocess.check_output(["git", *args], cwd=HERE.parent, text=True).strip()
    try:
        files = sorted([*HERE.parent.glob("src/main/**/*.java"), *HERE.glob("*.py"), HERE.parent / "pom.xml"])
        digest = hashlib.sha256()
        for path in files:
            digest.update(str(path.relative_to(HERE.parent)).replace("\\", "/").encode("utf-8"))
            digest.update(b"\0" + path.read_bytes() + b"\0")
        return {"revision": git("rev-parse", "HEAD"), "dirty": bool(git("status", "--porcelain")),
                "source_sha256": digest.hexdigest()}
    except (OSError, subprocess.CalledProcessError):
        return {"revision": None, "dirty": None}


def run_case(base, case, user):
    learned = []
    for index, message in enumerate((case["initial"], case["update"])):
        reply = post(base, f"/api/conversations/{user}", {"conversationId": f"seed-{index}", "messages": [message]})
        learned.extend(reply["memories"])
    graph = request(base, f"/api/memories/{user}/graph", method="GET")
    candidates = [m for m in graph["memories"] if normalize(case["expected"]) in normalize(m["content"])]
    state_correct = bool(candidates) and all(normalize(case["obsolete"]) not in normalize(m["content"])
                                            for m in graph["memories"])
    reply = post(base, f"/api/assistant/{user}", {"conversationId": "query", "message": case["question"]})
    checks = {"latest_state": state_correct,
              "latest_answer": normalize(case["expected"]) in normalize(reply["answer"])}
    if not candidates:
        return {"id": case["id"], "checks": checks, "passed": False, "learned": learned, "graph": graph}
    memory_id = candidates[0]["id"]
    history = request(base, f"/api/memories/{user}/{memory_id}/history", method="GET")
    checks["contradiction_audited"] = any(r["changeType"] == "CONTRADICTED" for r in history)
    corrected = request(base, f"/api/memories/{user}/{memory_id}", {"content": case["correction"]}, method="PATCH")
    checks["explicit_correction"] = corrected["content"] == case["correction"] and corrected["confidence"] == 1.0
    correction_reply = post(base, f"/api/assistant/{user}", {"conversationId": "corrected", "message": case["question"]})
    checks["corrected_answer"] = normalize(case["corrected_expected"]) in normalize(correction_reply["answer"])
    forgotten = request(base, f"/api/memories/{user}/{memory_id}", method="DELETE")
    checks["logical_forgetting"] = forgotten["status"] == "RETRACTED"
    post(base, f"/api/conversations/{user}", {"conversationId": "reobserve", "messages": [case["initial"]]})
    active = request(base, f"/api/memories/{user}/graph", method="GET")["memories"]
    checks["no_reactivation"] = all(m["id"] != memory_id and m["subject_key"] != candidates[0]["subject_key"] for m in active)
    history = request(base, f"/api/memories/{user}/{memory_id}/history", method="GET")
    checks["forgetting_audited"] = history[-1]["changeType"] == "FORGOTTEN"
    return {"id": case["id"], "checks": checks, "passed": all(checks.values()),
            "answer": reply["answer"], "corrected_answer": correction_reply["answer"], "history": history}


BASELINE_SYSTEM = "Use the supplied conversation history to answer the user. Prefer explicit corrections and the most recent facts. Do not invent personal facts."


def full_history_baseline(configuration, case):
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise RuntimeError("OPENROUTER_API_KEY is required for the full-history baseline")
    history = [{"role": "user", "content": case["initial"]}, {"role": "user", "content": case["update"]}]

    def answer(messages):
        payload = {"model": configuration["models"]["chat"], "temperature": 0.7,
                   "messages": [{"role": "system", "content": BASELINE_SYSTEM}, *messages]}
        req = urllib.request.Request("https://openrouter.ai/api/v1/chat/completions",
                                     data=json.dumps(payload).encode("utf-8"),
                                     headers={"Content-Type": "application/json", "Authorization": "Bearer " + key})
        with urllib.request.urlopen(req, timeout=180) as response:
            return json.load(response)["choices"][0]["message"]["content"]

    latest = answer([*history, {"role": "user", "content": case["question"]}])
    corrected = answer([*history, {"role": "user", "content": "Explicit correction: " + case["correction"]},
                        {"role": "user", "content": case["question"]}])
    checks = {"latest_answer": normalize(case["expected"]) in normalize(latest),
              "corrected_answer": normalize(case["corrected_expected"]) in normalize(corrected)}
    return {"id": case["id"], "checks": checks, "passed": all(checks.values()),
            "answer": latest, "corrected_answer": corrected}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--cases", type=Path, default=HERE / "temporal_cases.json")
    parser.add_argument("--repetitions", type=int, default=1)
    parser.add_argument("--system", choices=("evo-memory", "full-history"), default="evo-memory")
    args = parser.parse_args()
    if args.repetitions < 1:
        parser.error("repetitions must be positive")
    raw = args.cases.read_bytes()
    cases = json.loads(raw)["cases"]
    run_id = uuid.uuid4().hex
    configuration = request(args.base, f"/api/experiments/probe-{run_id[:12]}/configuration", method="GET")
    results = []
    started = time.monotonic()
    for repetition in range(args.repetitions):
        for case in cases:
            user = f"temporal-{uuid.uuid4().hex}"
            tick = time.monotonic()
            try:
                result = (run_case(args.base, case, user) if args.system == "evo-memory"
                          else full_history_baseline(configuration, case))
            except (OSError, RuntimeError, ValueError) as error:
                result = {"id": case["id"], "passed": False, "error_type": type(error).__name__}
            result.update(repetition=repetition, elapsed_seconds=round(time.monotonic() - tick, 3))
            results.append(result)
            print(f"{case['id']}: {'PASS' if result['passed'] else 'FAIL'} {result.get('checks', {})}", flush=True)
    report = {"run_id": run_id, "dataset_sha256": hashlib.sha256(raw).hexdigest(), "code": git_state(),
              "system": args.system,
              "configuration": configuration, "repetitions": args.repetitions, "total": len(results),
              "passed": sum(r["passed"] for r in results), "results": results,
              "elapsed_seconds": round(time.monotonic() - started, 3),
              "limitations": "Synthetic smoke suite; full-history compares only answer checks, not memory-state invariants. No statistical significance claim."}
    shared = ("latest_answer", "corrected_answer")
    report["shared_answer_checks_passed"] = sum(bool(r.get("checks", {}).get(k)) for r in results for k in shared)
    report["shared_answer_checks_total"] = len(results) * len(shared)
    if args.system == "full-history":
        report["baseline_system_prompt"] = BASELINE_SYSTEM
    out = HERE / "results" / f"temporal_{run_id}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Temporal checks: {report['passed']}/{report['total']}. Saved: {out}", flush=True)


if __name__ == "__main__":
    main()
