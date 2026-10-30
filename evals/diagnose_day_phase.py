"""Diagnóstico da frente 2 (futebol): foto do grafo a cada turno da história
do caso futebol-abc (v2), para mapear qual fase sobrevive ao change2
('de domingo para quarta') e por qual caminho (COMPLEMENTED vs chave nova).
Diagnóstico de dossiê/design: não é experimento de decisão -- nenhum número
daqui entra em critério; cada rep usa user próprio (sem somar dados).
"""
import json
from pathlib import Path

from benchmark_client import post, request
from run_obsolescence_v2 import story_of

HERE = Path(__file__).resolve().parent
CASES = json.loads((HERE / "obsolescence_v2_cases.json").read_text(encoding="utf-8"))
CASE = next(case for case in CASES["cases"] if case["id"] == "futebol-abc")


def graph_contents(base, user):
    graph = request(base, f"/api/memories/{user}/graph", method="GET")
    return sorted(m.get("content", "") for m in graph.get("memories", []))


def main():
    base = "http://127.0.0.1:8090"
    for repeat in range(10, 12):
        user = f"diag-dia-{repeat}"
        previous = set()
        story = story_of(CASE, CASES["fillers"])
        print(f"=== rep {repeat} (user {user}) | {len(story)} turnos ===")
        for index, message in enumerate(story):
            post(base, f"/api/conversations/{user}", {"conversationId": f"turn-{index}", "messages": [message]})
            current = set(graph_contents(base, user))
            if current != previous:
                print(f"turno {index:02d} [{message[:48]}...]")
                for content in sorted(current - previous):
                    print(f"    + {content}")
                for content in sorted(previous - current):
                    print(f"    - {content}")
                previous = current
        print(f"grafo final rep {repeat}:")
        for content in sorted(previous):
            print(f"    {content}")


if __name__ == "__main__":
    main()
