import collections
import json

r = json.load(open("evals/results/obsolescence_v2_93dfcdd1b82b4f758882032c975a8eff.json", encoding="utf-8"))
verdict_by_state = collections.Counter()
state_by_case = collections.defaultdict(dict)
for row in r["results"]:
    if row["system"] != "evo-memory":
        continue
    verdict_by_state[(row.get("state_correct"), row["verdict"])] += 1
    state_by_case[row["id"]][row["repetition"]] = row.get("state_correct")

print("veredito x estado do grafo (evo, 30):")
for (state, verdict), count in sorted(verdict_by_state.items(), key=lambda kv: (str(kv[0][0]), kv[0][1])):
    print(f"  state={state!s:5s} {verdict:22s} {count}")

print("por caso (rep0..4):")
for case, reps in sorted(state_by_case.items()):
    flags = ",".join(("ok" if state else "WRONG") for _, state in sorted(reps.items()))
    print(f"  {case:18s} {flags}")
