"""Preregistered ablation benchmark: versioned-state memory (evo-memory)
against a plain full-history chat baseline, on contradictory personal facts.

Both arms receive the identical raw story; only the machinery differs.
Decision metrics and criteria are frozen in
docs/research/prereg/2026-10-19-ablacao-obsolescencia.md — do not edit metrics
here without a new preregistration file.
"""
import argparse
import hashlib
import json
import os
import statistics
import subprocess
import time
import unicodedata
import urllib.request
import uuid
from pathlib import Path

from benchmark_client import post, request

HERE = Path(__file__).resolve().parent

BASELINE_SYSTEM = ("Use the supplied conversation history to answer the user. "
                   "Prefer explicit corrections and the most recent facts. "
                   "Do not invent personal facts.")


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


def classify(answer, case):
    text = normalize(answer or "")
    has_current = normalize(case["expected"]) in text
    has_obsolete = normalize(case["obsolete"]) in text
    if has_current and has_obsolete:
        return "ambiguous"
    if has_obsolete:
        return "obsolete"
    if has_current:
        return "correct"
    return "failed"


def evo_memory_arm(base, case, fillers_before, fillers_after, user):
    story = [case["initial"], *fillers_before, case["update"], *fillers_after]
    for index, message in enumerate(story):
        post(base, f"/api/conversations/{user}", {"conversationId": f"turn-{index}", "messages": [message]})
    reply = post(base, f"/api/assistant/{user}", {"conversationId": "query", "message": case["question"]})
    return {"answer": reply.get("answer", "")}


def full_history_arm(configuration, case, fillers_before, fillers_after):
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise RuntimeError("OPENROUTER_API_KEY is required for the full-history baseline")
    story = [case["initial"], *fillers_before, case["update"], *fillers_after]
    history = [{"role": "user", "content": message} for message in story]
    payload = {"model": configuration["models"]["chat"], "temperature": 0.7,
               "messages": [{"role": "system", "content": BASELINE_SYSTEM}, *history,
                            {"role": "user", "content": case["question"]}]}
    req = urllib.request.Request("https://openrouter.ai/api/v1/chat/completions",
                                 data=json.dumps(payload).encode("utf-8"),
                                 headers={"Content-Type": "application/json",
                                          "Authorization": "Bearer " + key})
    with urllib.request.urlopen(req, timeout=180) as response:
        return {"answer": json.load(response)["choices"][0]["message"]["content"], "llm_calls": 1}


def run_case(base, configuration, case, fillers, system):
    user = f"obsolescence-{system}-{uuid.uuid4().hex}"
    tick = time.monotonic()
    try:
        if system == "evo-memory":
            arm = evo_memory_arm(base, case, fillers["before"], fillers["after"], user)
        else:
            arm = full_history_arm(configuration, case, fillers["before"], fillers["after"])
        verdict = classify(arm["answer"], case)
        result = {"system": system, "id": case["id"], "verdict": verdict, "answer": arm["answer"]}
    except (OSError, RuntimeError, ValueError) as error:
        result = {"system": system, "id": case["id"], "verdict": "failed", "error_type": type(error).__name__}
    result["elapsed_seconds"] = round(time.monotonic() - tick, 3)
    return result


def summarize(results):
    per_system = {}
    for system in ("evo-memory", "full-history"):
        rows = [r for r in results if r["system"] == system]
        counts = {verdict: sum(r["verdict"] == verdict for r in rows)
                  for verdict in ("correct", "obsolete", "ambiguous", "failed")}
        elapsed = [r["elapsed_seconds"] for r in rows if "elapsed_seconds" in r]
        per_system[system] = {"n": len(rows), **counts,
                              "obsolete_rate": round(counts["obsolete"] / len(rows), 4) if rows else None,
                              "mean_elapsed_seconds": round(statistics.mean(elapsed), 2) if elapsed else None}
    return {"per_system": per_system,
            "obsolete_counts": {"evo-memory": per_system["evo-memory"]["obsolete"],
                                "full-history": per_system["full-history"]["obsolete"]}}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--cases", type=Path, default=HERE / "obsolescence_cases.json")
    parser.add_argument("--repetitions", type=int, default=5)
    args = parser.parse_args()
    if args.repetitions < 1:
        parser.error("repetitions must be positive")
    raw = args.cases.read_bytes()
    dataset = json.loads(raw)
    cases = dataset["cases"]
    fillers = {"before": dataset["fillers_before"], "after": dataset["fillers_after"]}
    run_id = uuid.uuid4().hex
    configuration = request(args.base, f"/api/experiments/probe-{run_id[:12]}/configuration", method="GET")

    results = []
    started = time.monotonic()
    for repetition in range(args.repetitions):
        for case in cases:
            for system in ("evo-memory", "full-history"):
                result = run_case(args.base, configuration, case, fillers, system)
                result["repetition"] = repetition
                results.append(result)
                print(f"rep {repetition} {system:13s} {case['id']}: {result['verdict']}", flush=True)
    summary = summarize(results)
    report = {"run_id": run_id, "dataset_sha256": hashlib.sha256(raw).hexdigest(), "code": git_state(),
              "configuration": configuration,
              "baseline_system_prompt": BASELINE_SYSTEM,
              "repetitions": args.repetitions, "results": results, "summary": summary,
              "elapsed_seconds": round(time.monotonic() - started, 3),
              "decision_rule": {"dependent": "obsolete answer counts", "n_per_arm": len(cases) * args.repetitions,
                                "supported": "evo-memory < full-history",
                                "refuted": "evo-memory > full-history",
                                "undemonstrated": "tie (including 0 vs 0)"},
              "limitations": ("Marker-based answer grading; ambiguous answers (both markers) are not obsolete use. "
                              "Baseline prompt favors recency (conservative bias). No significance test at n=30/arm.")}
    out = HERE / "results" / f"obsolescence_{run_id}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    print(f"Saved: {out}", flush=True)


if __name__ == "__main__":
    main()
