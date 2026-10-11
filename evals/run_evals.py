"""Roda os cenários de avaliação de extração de memória via OpenRouter.

Uso:
    set OPENROUTER_API_KEY=sua_chave
    python evals/run_evals.py --model openai/gpt-6-luna
"""

import argparse
import json
import os
import re
import time
import unicodedata
import urllib.request
from pathlib import Path

API_URL = "https://openrouter.ai/api/v1/chat/completions"
HERE = Path(__file__).parent

SYSTEM_PROMPT = """Você extrai memórias sobre o usuário a partir das mensagens dele.

Tipos:
- INTEREST: assunto que o usuário estuda ou acompanha (ex.: "está estudando sistemas distribuídos").
- PREFERENCE: gosto, escolha ou forma de trabalhar que o usuário prefere (ex.: "prefere consistência eventual").
- PROJECT: algo que o usuário está construindo ou desenvolvendo.
- DECISION: escolha já tomada e assumida pelo usuário (ex.: "decidiu usar PostgreSQL").
- FACT: dado pessoal ou circunstância objetiva (ex.: "mora em Florianópolis").
- HEALTH: alergia, restrição alimentar ou condição de saúde que o próprio usuário declara sobre si (ex.: "sou alérgico a amendoim").
- PLAN: intenção declarada de fazer algo (ex.: "vou viajar para o Japão em abril"). Se a mensagem trouxer uma data, preencha valid_until com AAAA-MM-DD; caso contrário, null.

Regras:
- Registre apenas afirmações feitas pelo usuário como fatos sobre si.
- Não registre hipóteses ("e se...", "talvez", "seria legal"), perguntas, ironia ou brincadeiras.
- Não registre planos de outras pessoas.
- Não registre documentos (CPF, RG), senhas, números de cartão ou endereços completos.
- Se a mensagem disser que um plano foi cancelado ou concluído, use o mesmo subject_key do plano, com o conteúdo atualizado (ex.: "Cancelou a viagem para o Japão.") e kind PLAN.
- Se uma mensagem atualiza uma memória existente, use o mesmo subject_key com o conteúdo atualizado.
- Se nada for relevante, retorne uma lista vazia.

Responda apenas com JSON neste formato:
{"memories": [{"kind": "INTEREST|PREFERENCE|PROJECT|DECISION|FACT|HEALTH|PLAN", "subject_key": "slug-curto", "content": "frase curta em português", "confidence": 0.0, "valid_until": null}]}"""


def normalize(text):
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    return text.lower()


def has_keywords(memory, keywords):
    content = normalize(memory["content"])
    return all(re.search(r"\b" + re.escape(normalize(k)) + r"\b", content) for k in keywords)


def matches(memory, spec, check_kind=True):
    if check_kind and spec.get("kind") and memory["kind"] != spec["kind"]:
        return False
    if any(has_keywords(memory, [k]) for k in spec.get("exclude", [])):
        return False
    return has_keywords(memory, spec["keywords"])


def call_llm(api_key, model, existing, day, messages):
    user_content = (
        "Memórias existentes:\n"
        + json.dumps(existing, ensure_ascii=False)
        + f"\n\nMensagens do dia {day}:\n"
        + "\n".join(f"- {m}" for m in messages)
    )
    body = {
        "model": model,
        "temperature": 0,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_content},
        ],
    }
    request = urllib.request.Request(
        API_URL,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return payload["choices"][0]["message"]["content"], payload.get("usage", {})


def parse_memories(raw):
    cleaned = raw.strip()
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", cleaned)
    try:
        return json.loads(cleaned).get("memories", []), None
    except (json.JSONDecodeError, AttributeError) as error:
        return [], str(error)


def run_scenario(api_key, model, scenario):
    store = {}  # subject_key -> memória atual (simula upsert do MemoryService)
    usage_total = {"prompt_tokens": 0, "completion_tokens": 0}
    errors = []

    for session in scenario["sessions"]:
        existing = list(store.values())
        raw, usage = call_llm(api_key, model, existing, session["day"], session["messages"])
        for key in usage_total:
            usage_total[key] += usage.get(key, 0)

        memories, error = parse_memories(raw)
        if error:
            errors.append(f"dia {session['day']}: {error}")
        for memory in memories:
            store[memory["subject_key"]] = memory

    final = list(store.values())
    expected_results = [
        {
            "spec": spec,
            "found": any(matches(m, spec, check_kind=False) for m in final),
            "kind_ok": any(matches(m, spec) for m in final),
        }
        for spec in scenario["expected"]
    ]
    forbidden_hits = [
        {"spec": spec, "memory": m}
        for spec in scenario["forbidden"]
        for m in final
        if matches(m, spec)
    ]
    return {
        "id": scenario["id"],
        "final_memories": final,
        "expected": expected_results,
        "forbidden_hits": forbidden_hits,
        "errors": errors,
        "usage": usage_total,
    }


def summarize(results):
    expected_total = sum(len(r["expected"]) for r in results)
    expected_found = sum(e["found"] for r in results for e in r["expected"])
    kind_ok = sum(e["kind_ok"] for r in results for e in r["expected"])
    forbidden_total = sum(len(r["forbidden_hits"]) for r in results)
    return {
        "recall": expected_found / expected_total if expected_total else None,
        "expected_found": expected_found,
        "expected_total": expected_total,
        "kind_accuracy": kind_ok / expected_total if expected_total else None,
        "forbidden_hits": forbidden_total,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--scenarios", default=str(HERE / "scenarios.json"))
    parser.add_argument("--out", default=None)
    args = parser.parse_args()

    api_key = os.environ.get("OPENROUTER_API_KEY")
    if not api_key:
        raise SystemExit("Defina a variável de ambiente OPENROUTER_API_KEY.")

    scenarios = json.loads(Path(args.scenarios).read_text(encoding="utf-8"))
    started = time.time()
    results = [run_scenario(api_key, args.model, s) for s in scenarios]
    elapsed = time.time() - started

    summary = summarize(results)
    print(f"\nModelo: {args.model}")
    print(f"Recall: {summary['recall']:.0%} ({summary['expected_found']}/{summary['expected_total']})")
    print(f"Acerto de tipo (kind): {summary['kind_accuracy']:.0%}")
    print(f"Violações (forbidden): {summary['forbidden_hits']}")
    print(f"Tempo total: {elapsed:.1f}s\n")

    for r in results:
        status = "OK" if all(e["found"] for e in r["expected"]) and not r["forbidden_hits"] and not r["errors"] else "FALHOU"
        print(f"[{status}] {r['id']}")
        for e in r["expected"]:
            mark = "[ok]" if e["found"] else "[faltou]"
            kind_note = "" if e["kind_ok"] or not e["found"] else f" (tipo diferente de {e['spec'].get('kind')})"
            print(f"    esperado {mark} {e['spec']['keywords']}{kind_note}")
        for h in r["forbidden_hits"]:
            print(f"    proibido [violou] {h['memory']['kind']}: {h['memory']['content']}")
        for err in r["errors"]:
            print(f"    erro de parse: {err}")

    scenarios_stem = Path(args.scenarios).stem
    default_name = f"{args.model.replace('/', '_')}__{scenarios_stem}.json"
    out_path = Path(args.out) if args.out else HERE / "results" / default_name
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        json.dumps({"model": args.model, "summary": summary, "results": results}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\nResultado salvo em {out_path}")


if __name__ == "__main__":
    main()
