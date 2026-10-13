"""Mede a relevância do contexto recuperado pelo endpoint /api/assistant.

Semeia memórias para um usuário novo via /api/conversations e envia perguntas
para /api/assistant. Em cada resposta, o campo "context" mostra quais memórias
foram usadas. O teste confere se a memória esperada entrou e se nenhuma memória
proibida entrou.

Uso (com a aplicação rodando em localhost:8080):
    python evals/run_retrieval_eval.py                                  # conjunto de calibração
    python evals/run_retrieval_eval.py --cases evals/retrieval_heldout.json   # conjunto separado
"""

import argparse
import json
import re
import time
import unicodedata
import urllib.error
import urllib.request
import uuid
from benchmark_client import post as authenticated_post
from pathlib import Path

HERE = Path(__file__).parent

SEED_MESSAGES = [
    "Estou estudando sistemas distribuídos.",
    "Moro em Florianópolis.",
    "Gosto de culinária italiana, principalmente massas.",
    "Estou construindo um app de finanças pessoais em React.",
    "Tenho um gato chamado Miau.",
    "Prefiro café sem açúcar.",
    "Meu time de futebol é o Figueirense.",
]

# Cada caso: pergunta, pelo menos um destes termos deve aparecer no contexto,
# e nenhum dos termos proibidos pode aparecer.
CASES = [
    {
        "id": "sem-palavras-em-comum-distribuidos",
        "message": "Quero entender replicação de dados entre servidores.",
        "expected_any": ["distribu"],
        "forbidden": ["culin", "massa", "react"],
    },
    {
        "id": "sinonimo-culinaria",
        "message": "Qual restaurante italiano você recomenda para jantar?",
        "expected_any": ["culin", "massa", "italian"],
        "forbidden": ["react", "distribu"],
    },
    {
        "id": "termo-exato",
        "message": "Como deixo meu código React mais rápido?",
        "expected_any": ["react"],
        "forbidden": ["culin", "massa"],
    },
    {
        "id": "cidade-implicita",
        "message": "Onde eu moro hoje?",
        "expected_any": ["florian"],
        "forbidden": [],
    },
    {
        "id": "consenso-por-significado",
        "message": "Como funciona consenso entre nós de um cluster?",
        "expected_any": ["distribu"],
        "forbidden": ["culin", "react"],
    },
]


def normalize(text):
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    return text.lower()


def post(base, path, body, attempts=3):
    return authenticated_post(base, path, body, attempts)


def contains_any(texts, terms):
    haystack = " ".join(normalize(t) for t in texts)
    return [t for t in terms if re.search(re.escape(normalize(t)), haystack)]


def load_dataset(path):
    if path is None:
        return SEED_MESSAGES, CASES
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return data["seeds"], data["cases"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--cases", default=None, help="JSON com seeds e cases")
    args = parser.parse_args()

    seeds, cases_list = load_dataset(args.cases)
    dataset_name = Path(args.cases).stem if args.cases else "calibracao"

    # Cada caso usa um usuário novo: o assistente aprende com as perguntas,
    # então perguntas anteriores não podem contaminar o contexto dos casos seguintes.
    stamp = f"{int(time.time())}-{uuid.uuid4().hex[:8]}"
    results = []
    for case in cases_list:
        slug = re.sub(r"[^a-z0-9]+", "-", normalize(case["id"])).strip("-")
        user = f"eval-ret-{stamp}-{slug[:40]}"
        for i, message in enumerate(seeds):
            post(args.base, f"/api/conversations/{user}",
                 {"conversationId": f"seed-{i}", "messages": [message]})

        reply = post(args.base, f"/api/assistant/{user}",
                     {"conversationId": f"case-{case['id']}", "message": case["message"]})
        context = [m["content"] for m in reply["context"]]
        found = contains_any(context, case["expected_any"])
        violations = contains_any(context, case["forbidden"])
        passed = bool(found) and not violations
        results.append({
            "id": case["id"],
            "user": user,
            "message": case["message"],
            "passed": passed,
            "found": found,
            "violations": violations,
            "context": context,
        })

    passed_count = sum(r["passed"] for r in results)
    print(f"\nConjunto: {dataset_name}")
    print(f"Acerto: {passed_count}/{len(results)}\n")
    for r in results:
        status = "OK" if r["passed"] else "FALHOU"
        print(f"[{status}] {r['id']}: {r['message']}")
        for c in r["context"]:
            print(f"    contexto: {c}")
        if r["violations"]:
            print(f"    proibido presente: {r['violations']}")

    out_dir = HERE / "results"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / f"retrieval_{dataset_name}_{stamp}.json"
    out_path.write_text(json.dumps({"dataset": dataset_name, "passed": passed_count, "results": results},
                                   ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nResultado salvo em {out_path}")


if __name__ == "__main__":
    main()
