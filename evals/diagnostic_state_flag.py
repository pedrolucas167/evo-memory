"""Exploratory post-run diagnostic: seed one v2 case and dump the graph.

Purpose: decompose the preregistered `state=WRONG` mechanism flag — are the
A/B markers retained by episodic summaries (by design) or by active semantic
memories (evolution defect)? Single sample, exploratory, no decision use.
"""
import json
import sys
import unicodedata
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from benchmark_client import post, request

HERE = Path(__file__).resolve().parent
dataset = json.loads((HERE / "obsolescence_v2_cases.json").read_bytes())
case = next(c for c in dataset["cases"] if c["id"] == "gato-abc")
fillers = dataset["fillers"]


def normalize(text):
    return "".join(c for c in unicodedata.normalize("NFKD", text.lower()) if not unicodedata.combining(c))


story = ([case["initial"]]
         + [fillers[i % len(fillers)] for i in range(13)]
         + [case["change1"]]
         + [fillers[(1 + i) % len(fillers)] for i in range(7)]
         + [case["change2"]]
         + [fillers[(2 + i) % len(fillers)] for i in range(7)])
user = f"diagnostic-gato-{uuid.uuid4().hex}"
print(f"semeando {len(story)} turnos em {user}...", flush=True)
for index, message in enumerate(story):
    post("http://127.0.0.1:8091", f"/api/conversations/{user}", {"conversationId": f"turn-{index}", "messages": [message]})
graph = request("http://127.0.0.1:8091", f"/api/memories/{user}/graph", method="GET")
for memory in graph.get("memories", []):
    content = memory.get("content", "")
    tag = []
    text = normalize(content)
    if normalize(case["expected"]) in text:
        tag.append("C")
    if normalize(case["intermediate"]) in text:
        tag.append("B")
    if normalize(case["old"]) in text:
        tag.append("A")
    print(f"[{','.join(tag) or '-'}] kind={memory.get('kind')} active={memory.get('active')} id={memory.get('id')[:8]} :: {content}")
