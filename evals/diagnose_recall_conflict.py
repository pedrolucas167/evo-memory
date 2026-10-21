"""Diagnóstico exploratório: onde morre a linha correta no recall?

Caso: gato-abc (dossiê vivo 2026-10-21) — a resposta de estado foi "Tobi"
(errado; esperado Nina) porque o contexto trouxe só a linha antiga. Este
script replica OFFLINE os três portões do recall (AssistantService + config
padrão) para decompor a causa, sem servidor e sem alterar estado:

  1. portão semântico: cosines bge-m3 via OpenRouter (mesma chamada do
     OpenRouterEmbeddingClient), cortes minSimilarity 0,38 e margem relativa
     0,05 contra o melhor match;
  2. portão léxico: mesma regra de words() do AssistantService (>=4 chars,
     stopwords próprias) + SynonymExpander;
  3. juiz de re-ranking: mesmo SYSTEM_PROMPT e temperatura 0.0 do
     JudgeReranker, com os candidatos na ordem emulada de score.

Exploratório, 1 amostra, sem uso de decisão — antecede o pré-registro da
frente A.2 (resolução de conflito no recall).
"""
import json
import math
import os
import re
import time
import unicodedata
import urllib.request

BASE = "https://openrouter.ai/api/v1"
KEY = os.environ["OPENROUTER_API_KEY"]  # escopo máquina; nunca imprimir/copiar
EMBED_MODEL = "baai/bge-m3"
JUDGE_MODEL = "openai/gpt-6-luna"

QUESTION = "Qual o nome do meu gato?"

NINE = [
    ("gato-tobi", "Adotou um gato chamado Tobi, que fugiu."),
    ("gata-nina", "A gata Mel morreu, e adotou uma gata chamada Nina."),
]
FILLERS = [
    ("plan-dormir", "Pretende dormir cedo hoje."),
    ("pai-bola", "Ligou para o pai para falar de bola."),
    ("serie", "Terminou de assistir a uma série de dez episódios."),
    ("projeto", "O prazo de um projeto termina amanhã."),
]
MEMORIES = NINE + FILLERS

MIN_SIM = 0.38
MARGIN = 0.05
TOP_K = 10
LEXICAL_WEIGHT = 0.1
STOPWORDS = {"para", "porque", "quando", "onde", "durante", "atraves", "mediante",
             "conforme", "enquanto", "embora", "apesar", "sobre", "entre", "desde",
             "dentro", "apenas", "talvez", "tambem", "sempre", "ainda", "agora"}
SYNONYMS = {
    "violao": {"violino", "violoncelo", "instrumento", "musica"},
    "violino": {"violao", "violoncelo", "instrumento", "musica"},
    "filho": {"filhos", "criancas", "pequenos", "familia"},
    "filhos": {"filho", "criancas", "pequenos", "familia"},
    "corrida": {"maratona", "esporte", "treino"},
    "maratona": {"corrida", "esporte", "treino", "42"},
    "nadar": {"agua", "piscina", "nado", "exercicio"},
    "agua": {"nadar", "piscina", "nado", "exercicio"},
    "romance": {"escrever", "livro", "texto", "destravar"},
    "escrever": {"romance", "livro", "texto", "destravar"},
}


def openrouter(path, payload):
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        BASE + path, data=body, method="POST",
        headers={"Authorization": f"Bearer {KEY}",
                 "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def embed(text):
    data = openrouter("/embeddings", {"model": EMBED_MODEL, "input": text})
    return data["data"][0]["embedding"]


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    return dot / (na * nb) if na and nb else 0.0


def normalize(text):
    flat = unicodedata.normalize("NFD", text.lower())
    return "".join(ch for ch in flat if not unicodedata.combining(ch))


def words(text):
    tokens = re.split(r"[^a-z]+", normalize(text))
    return {t for t in tokens if len(t) >= 4 and t not in STOPWORDS}


def expanded(ws):
    out = set(ws)
    for w in ws:
        out |= SYNONYMS.get(w, set())
    return out


def lexical_overlap(msg_words, memory_text):
    return len(expanded(msg_words) & expanded(words(memory_text)))


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


def judge(question, ordered):
    listing = "\n".join(
        f"{i + 1}. [FACT] {content}" for i, (_, content) in enumerate(ordered))
    user = f"Mensagem atual:\n{question}\n\nMemórias:\n{listing}"
    reply = openrouter("/chat/completions", {
        "model": JUDGE_MODEL,
        "messages": [{"role": "system", "content": JUDGE_PROMPT},
                     {"role": "user", "content": user}],
        "temperature": 0.0,
    })
    text = reply["choices"][0]["message"]["content"]
    print(f"\n--- juiz (resposta crua) ---\n{text}")
    m = re.search(r"\[([\d,\s]*)\]", text)
    return [int(n) for n in m.group(1).split(",") if n.strip()] if m else []


def main():
    qvec = embed(QUESTION)
    sims = {}
    vectors = {}
    for key, content in MEMORIES:
        vectors[key] = embed(content)
        sims[key] = cosine(qvec, vectors[key])
        time.sleep(0.4)

    print("--- portão semântico (cosines bge-m3, corte 0.38, margem 0.05) ---")
    best_key = max(sims, key=sims.get)
    best = sims[best_key]
    print(f"melhor: {best_key} = {best:.4f}")
    survivors = []
    for key, content in MEMORIES:
        s = sims[key]
        ok = s >= MIN_SIM and s >= best - MARGIN
        print(f"  {s:.4f}  {'PASSA' if ok else '  CAI '}  {key} :: {content}")
        if ok:
            survivors.append(key)

    pet_keys = [k for k, _ in NINE]
    print("\n--- cossenos entre as duas linhas do caso (conflito de tópico sem LLM?) ---")
    print(f"  cos({pet_keys[1]}, {pet_keys[0]}) = "
          f"{cosine(vectors[pet_keys[1]], vectors[pet_keys[0]]):.4f}")

    print("\n--- portão léxico (words() + sinônimos) ---")
    msg_words = words(QUESTION)
    for key, content in MEMORIES:
        ov = lexical_overlap(msg_words, f"{content} {key}")
        print(f"  overlap={ov} (peso {LEXICAL_WEIGHT * ov:.2f})  {key}")

    print("\n--- ordenação emulada (semantic + 0.1 * lexical) ---")
    scored = []
    for key, content in MEMORIES:
        ov = lexical_overlap(msg_words, f"{content} {key}")
        score = sims[key] + LEXICAL_WEIGHT * ov
        pass_gate = key in survivors or ov > 0
        print(f"  {score:.4f}  {'entra no ranked' if pass_gate else 'nao entra'}  {key}")
        if pass_gate:
            scored.append((score, key, content))
    scored.sort(reverse=True)
    order = [(k, c) for _, k, c in scored]

    print("\n--- juiz ---")
    picked = judge(QUESTION, order)
    print(f"escolhidos pelo juiz: {[order[p - 1][0] for p in picked if 1 <= p <= len(order)]}")

    print("\n--- contexto final emulado (topK=10) ---")
    selected = [order[p - 1][0] for p in picked if 1 <= p <= len(order)]
    for key in selected:
        print(f"  {key} :: {[c for k, c in order if k == key][0]}")


if __name__ == "__main__":
    main()
