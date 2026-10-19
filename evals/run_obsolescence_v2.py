"""Preregistered ablation v2: versioned-state memory (evo-memory) against a
plain full-history chat baseline on three-state personal facts (A->B->C)
across 30-turn histories with neutral (recency-cue-free) queries.

Both arms receive the identical raw story; only the machinery differs.
Criteria and metrics are frozen in
docs/research/prereg/2026-10-19-ablacao-obsolescencia-v2.md — do not edit
metrics here without a new preregistration file.
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

FILLERS_BEFORE_CHANGE1 = 13
FILLERS_BETWEEN_CHANGES = 7
FILLERS_AFTER_CHANGE2 = 7


def normalize(text):
    return "".join(c for c in unicodedata.normalize("NFKD", text.lower())
                   if not unicodedata.combining(c))


def story_of(case, fillers):
    def cycle(count, offset):
        return [fillers[(offset + i) % len(fillers)] for i in range(count)]

    return ([case["initial"]]
            + cycle(FILLERS_BEFORE_CHANGE1, 0)
            + [case["change1"]]
            + cycle(FILLERS_BETWEEN_CHANGES, 1)
            + [case["change2"]]
            + cycle(FILLERS_AFTER_CHANGE2, 2))


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
    """Frozen v2 classes: correct | ambiguous | obsolete_intermediate |
    obsolete_old | failed (prereg formulas)."""
    text = normalize(answer or "")
    has_c = normalize(case["expected"]) in text
    has_b = normalize(case["intermediate"]) in text
    has_a = normalize(case["old"]) in text
    if has_c:
        return "ambiguous" if (has_a or has_b) else "correct"
    if has_b:
        return "obsolete_intermediate"
    if has_a:
        return "obsolete_old"
    return "failed"


def state_correct(graph, case):
    text = " ".join(normalize(m.get("content", "")) for m in graph.get("memories", []))
    has_c = normalize(case["expected"]) in text
    has_b = normalize(case["intermediate"]) in text
    has_a = normalize(case["old"]) in text
    return has_c and not has_a and not has_b


def evo_memory_arm(base, case, story, user):
    seed_tick = time.monotonic()
    for index, message in enumerate(story):
        post(base, f"/api/conversations/{user}", {"conversationId": f"turn-{index}", "messages": [message]})
    seed_elapsed = round(time.monotonic() - seed_tick, 3)
    graph = request(base, f"/api/memories/{user}/graph", method="GET")
    state_ok = state_correct(graph, case)
    query_tick = time.monotonic()
    reply = post(base, f"/api/assistant/{user}", {"conversationId": "query", "message": case["question"]})
    return {"answer": reply.get("answer", ""), "seed_elapsed_seconds": seed_elapsed,
            "query_elapsed_seconds": round(time.monotonic() - query_tick, 3),
            "state_correct": state_ok, "estimated_llm_calls": len(story) * 2 + 2}


def full_history_arm(configuration, case, story):
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise RuntimeError("OPENROUTER_API_KEY is required for the full-history baseline")
    history = [{"role": "user", "content": message} for message in story]
    payload = {"model": configuration["models"]["chat"], "temperature": 0.7,
               "messages": [{"role": "system", "content": BASELINE_SYSTEM}, *history,
                            {"role": "user", "content": case["question"]}]}
    req = urllib.request.Request("https://openrouter.ai/api/v1/chat/completions",
                                 data=json.dumps(payload).encode("utf-8"),
                                 headers={"Content-Type": "application/json",
                                          "Authorization": "Bearer " + key})
    with urllib.request.urlopen(req, timeout=180) as response:
        body = json.load(response)
    usage = body.get("usage") or {}
    return {"answer": body["choices"][0]["message"]["content"],
            "llm_calls": 1,
            "prompt_tokens": usage.get("prompt_tokens"),
            "completion_tokens": usage.get("completion_tokens")}


def run_case(base, configuration, case, fillers, system):
    user = f"obsolescence-v2-{system}-{uuid.uuid4().hex}"
    story = story_of(case, fillers)
    tick = time.monotonic()
    try:
        if system == "evo-memory":
            arm = evo_memory_arm(base, case, story, user)
        else:
            arm = full_history_arm(configuration, case, story)
        result = {"system": system, "id": case["id"], "story_turns": len(story),
                  "answer": arm["answer"],
                  **{k: v for k, v in arm.items() if k != "answer"}}
        result["verdict"] = classify(arm["answer"], case)
    except (OSError, RuntimeError, ValueError) as error:
        result = {"system": system, "id": case["id"], "story_turns": len(story),
                  "verdict": "failed", "error_type": type(error).__name__}
    result["elapsed_seconds"] = round(time.monotonic() - tick, 3)
    return result


def summarize(results):
    per_system = {}
    for system in ("evo-memory", "full-history"):
        rows = [r for r in results if r["system"] == system]
        verdicts = ("correct", "obsolete_intermediate", "obsolete_old", "ambiguous", "failed")
        counts = {v: sum(r.get("verdict") == v for r in rows) for v in verdicts}
        stale = counts["obsolete_intermediate"] + counts["obsolete_old"]
        elapsed = [r["elapsed_seconds"] for r in rows if "elapsed_seconds" in r]
        entry = {"n": len(rows), **counts, "obsolete_total": stale,
                 "obsolete_rate": round(stale / len(rows), 4) if rows else None,
                 "mean_elapsed_seconds": round(statistics.mean(elapsed), 2) if elapsed else None}
        if system == "evo-memory":
            query_elapsed = [r["query_elapsed_seconds"] for r in rows if "query_elapsed_seconds" in r]
            entry["mean_query_elapsed_seconds"] = round(statistics.mean(query_elapsed), 2) if query_elapsed else None
            entry["state_correct_rate"] = round(
                sum(bool(r.get("state_correct")) for r in rows) / len(rows), 4) if rows else None
            entry["estimated_llm_calls_sum"] = sum(
                int(r.get("estimated_llm_calls", 0)) for r in rows)
        else:
            tokens = [r.get("prompt_tokens") for r in rows if r.get("prompt_tokens")]
            out_tokens = [r.get("completion_tokens") for r in rows if r.get("completion_tokens")]
            entry["mean_prompt_tokens"] = round(statistics.mean(tokens)) if tokens else None
            entry["mean_completion_tokens"] = round(statistics.mean(out_tokens)) if out_tokens else None
        per_system[system] = entry
    return {"per_system": per_system,
            "obsolete_counts": {"evo-memory": per_system["evo-memory"]["obsolete_total"],
                                "full-history": per_system["full-history"]["obsolete_total"]}}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--cases", type=Path, default=HERE / "obsolescence_v2_cases.json")
    parser.add_argument("--repetitions", type=int, default=5)
    args = parser.parse_args()
    if args.repetitions < 1:
        parser.error("repetitions must be positive")
    raw = args.cases.read_bytes()
    dataset = json.loads(raw)
    cases = dataset["cases"]
    fillers = dataset["fillers"]
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
                extra = ""
                if system == "evo-memory" and "state_correct" in result:
                    extra = f" state={'ok' if result['state_correct'] else 'WRONG'}"
                print(f"rep {repetition} {system:13s} {case['id']}: {result['verdict']}{extra}", flush=True)
    summary = summarize(results)
    report = {"run_id": run_id, "dataset_sha256": hashlib.sha256(raw).hexdigest(), "code": git_state(),
              "configuration": configuration,
              "baseline_system_prompt": BASELINE_SYSTEM,
              "layout": {"fillers_before_change1": FILLERS_BEFORE_CHANGE1,
                         "fillers_between": FILLERS_BETWEEN_CHANGES,
                         "fillers_after_change2": FILLERS_AFTER_CHANGE2},
              "repetitions": args.repetitions, "results": results, "summary": summary,
              "elapsed_seconds": round(time.monotonic() - started, 3),
              "decision_rule": {"dependent": "obsolete totals (intermediate+old)",
                                "n_per_arm": len(cases) * args.repetitions,
                                "supported": "evo-memory < full-history",
                                "refuted": "evo-memory > full-history",
                                "undemonstrated": "tie (including 0 vs 0; two consecutive 0x0 ties close this operationalization)"},
              "limitations": ("Marker-based grading; ambiguous answers are not obsolete use. Baseline prompt favors "
                              "recency (conservative bias). Evo LLM-call counts are estimates. No significance test "
                              "at n=30/arm. Thirty turns still fit the chat model window: stress is distractor "
                              "density plus chained changes, not truncation.")}
    out = HERE / "results" / f"obsolescence_v2_{run_id}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    print(f"Saved: {out}", flush=True)


if __name__ == "__main__":
    main()
