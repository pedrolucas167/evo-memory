"""Parte 2 do diagnóstico exploratório: o juiz com candidatos relaxados.

Medição que falta para o desenho da A.2: dada a lista completa (margem
relativa suspensa, apenas corte 0,38), o juiz conservador mantém as duas
linhas do caso pet ou descarta a Nina de novo? E que espaçamento natural
os fillers têm entre si (para isto escolher limiar de vizinhança)?
1 chamada LLM + 7 embeddings. Exploratório, sem uso de decisão.
"""
import json
import math
import os
import random
import time
import urllib.request

BASE = "https://openrouter.ai/api/v1"
KEY = os.environ["OPENROUTER_API_KEY"]
EMBED_MODEL = "baai/bge-m3"
JUDGE_MODEL = "openai/gpt-6-luna"

QUESTION = "Qual o nome do meu gato?"
MEMS = [
    ("gato-tobi", "Adotou um gato chamado Tobi, que fugiu."),
    ("gata-nina", "A gata Mel morreu, e adotou uma gata chamada Nina."),
    ("pai-bola", "Ligou para o pai para falar de bola."),
    ("plan-dormir", "Pretende dormir cedo hoje."),
    ("projeto", "O prazo de um projeto termina amanhã."),
    ("serie", "Terminou de assistir a uma série de dez episódios."),
]

JUDGE_PROMPT = """Você é um filtro de relevância de memórias do usuário. Dada a mensagem
atual e uma lista numerada de memórias, escolha APENAS as memórias que
ajudam diretamente a responder ou contextualizar a mensagem.

Critérios:
- Relevância por composição de significado, não por palavra em comum.
- Rotinas, família, hobbies e planos só entram se a mensagem tratar deles.
- Seja conservador: memória que não apoia diretamente fica fora.
- Não devolver nada que não esteja na lista.

Responda SÓ com JSON, sem acrescentar outra coisa:
{"relevant": [<números>]}
"""


def openrouter(path, payload):
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        BASE + path, data=body, method="POST",
        headers={"Authorization": f"Bearer {KEY}",
                 "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def embed(text):
    return openrouter("/embeddings", {"model": EMBED_MODEL, "input": text})["data"][0]["embedding"]


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    return dot / (na * nb) if na and nb else 0.0


def main():
    vectors = {}
    for key, content in MEMS:
        vectors[key] = embed(content)
        time.sleep(0.4)

    print("--- cossenos linha a linha (estrutura do espaço) ---")
    keys = [k for k, _ in MEMS]
    for i, a in enumerate(keys):
        for b in keys[i + 1:]:
            print(f"  cos({a}, {b}) = {cosine(vectors[a], vectors[b]):.4f}")

    listing = "\n".join(f"{i + 1}. [FACT] {c}" for i, (_, c) in enumerate(MEMS))
    user = f"Mensagem atual:\n{QUESTION}\n\nMemórias:\n{listing}"
    reply = openrouter("/chat/completions", {
        "model": JUDGE_MODEL,
        "messages": [{"role": "system", "content": JUDGE_PROMPT},
                     {"role": "user", "content": user}],
        "temperature": 0.0,
    })
    text = reply["choices"][0]["message"]["content"]
    print("\n--- juiz com 6 candidatos (margem suspensa, corte 0.38) ---")
    print(text)
    import re
    m = re.search(r"\[([\d,\s]*)\]", text)
    if m:
        picked = [int(n) for n in m.group(1).split(",") if n.strip()]
        chosen = [MEMS[p - 1][0] for p in picked if 1 <= p <= len(MEMS)]
        print(f"escolhidos: {chosen}")


if __name__ == "__main__":
    main()
