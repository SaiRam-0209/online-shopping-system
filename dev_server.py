#!/usr/bin/env python3
"""Serve the ShopNova frontend and proxy chatbot requests to Gemini."""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import dataclass
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib import error, request


ROOT_DIR = Path(__file__).resolve().parent
FRONTEND_DIR = ROOT_DIR / "frontend"
DEFAULT_PORT = int(os.getenv("SHOPNOVA_PORT", "4174"))
DEFAULT_MODEL = os.getenv("SHOPNOVA_GEMINI_MODEL", "gemini-2.5-flash")

STOP_WORDS = {
    "a",
    "an",
    "and",
    "about",
    "all",
    "am",
    "any",
    "app",
    "application",
    "are",
    "at",
    "be",
    "can",
    "do",
    "does",
    "for",
    "from",
    "give",
    "help",
    "how",
    "i",
    "in",
    "is",
    "it",
    "me",
    "my",
    "of",
    "on",
    "or",
    "please",
    "show",
    "tell",
    "that",
    "the",
    "this",
    "to",
    "what",
    "when",
    "where",
    "which",
    "with",
    "you",
}

CURATED_CONTEXT = """
You are ShopNova Assistant, a friendly, helpful AI embedded in the ShopNova demo online shopping app.
You can answer ANY question the user asks — general knowledge, coding help, math, writing, trivia, advice,
as well as ShopNova-specific questions. Be concise, accurate, and conversational.

When the user asks about ShopNova, use this context:
- Frontend pages: Products, Cart, Checkout (React storefront).
- Cart supports quantity changes, removal, and coupon codes SAVE10, SAVE20, FLAT50.
- Checkout supports Credit Card, Debit Card, UPI, Net Banking; simulates orders with a demo order ID.
- Backend services: product-service, cart-service, order-service, payment-service, user-service behind an API gateway.
- Intended data stores: PostgreSQL for most services, Redis for cart state.

Rules:
- If you are unsure, say so honestly rather than inventing details.
- For general questions unrelated to ShopNova, just answer normally and helpfully.
""".strip()

CONTEXT_PATHS = [
    ROOT_DIR / "README.md",
    ROOT_DIR / "docs" / "architecture.json",
    ROOT_DIR / "docs" / "risk-edge-cases.md",
    ROOT_DIR / "api-specs" / "product-service-openapi.yaml",
    ROOT_DIR / "api-specs" / "cart-service-openapi.yaml",
]


@dataclass
class ContextChunk:
    source: str
    text: str
    normalized: str
    tokens: set[str]


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9\s]", " ", text.lower())).strip()


def tokenize(text: str) -> set[str]:
    return {
        token
        for token in normalize_text(text).split()
        if token and token not in STOP_WORDS
    }


def chunk_text(text: str, chunk_size: int = 1400, overlap: int = 220) -> list[str]:
    clean = text.replace("\r", "").strip()
    if not clean:
        return []
    if len(clean) <= chunk_size:
        return [clean]

    chunks: list[str] = []
    start = 0
    while start < len(clean):
        end = min(len(clean), start + chunk_size)
        if end < len(clean):
            boundary = clean.rfind("\n\n", start + 700, end)
            if boundary != -1 and boundary > start:
                end = boundary
        chunk = clean[start:end].strip()
        if chunk:
            chunks.append(chunk)
        if end >= len(clean):
            break
        start = max(end - overlap, start + 1)
    return chunks


def load_context_chunks() -> list[ContextChunk]:
    chunks: list[ContextChunk] = []
    for path in CONTEXT_PATHS:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        rel = str(path.relative_to(ROOT_DIR))
        for piece in chunk_text(text):
            chunks.append(
                ContextChunk(
                    source=rel,
                    text=piece,
                    normalized=normalize_text(piece),
                    tokens=tokenize(piece),
                )
            )
    return chunks


CONTEXT_CHUNKS = load_context_chunks()


def select_relevant_context(question: str, limit: int = 4) -> list[ContextChunk]:
    question_normalized = normalize_text(question)
    question_tokens = tokenize(question)
    scored: list[tuple[int, ContextChunk]] = []

    for chunk in CONTEXT_CHUNKS:
        score = 0
        for token in question_tokens:
            if token in chunk.tokens:
                score += 1
        if question_normalized and question_normalized in chunk.normalized:
            score += 6
        if score:
            scored.append((score, chunk))

    scored.sort(key=lambda item: item[0], reverse=True)
    return [chunk for _, chunk in scored[:limit]]


def summarize_cart(cart: dict[str, Any]) -> str:
    item_count = int(cart.get("itemCount", 0) or 0)
    subtotal = float(cart.get("subtotal", 0) or 0)
    total = float(cart.get("total", 0) or 0)
    discount = float(cart.get("discount", 0) or 0)
    coupon = cart.get("couponCode") or "none"
    items = cart.get("items") or []

    if not items:
        return (
            f"Cart is empty. itemCount={item_count}, subtotal=${subtotal:.2f}, "
            f"discount=${discount:.2f}, total=${total:.2f}, coupon={coupon}."
        )

    preview = []
    for item in items[:5]:
        name = str(item.get("productName", "Unknown item"))
        quantity = int(item.get("quantity", 0) or 0)
        line_total = float(item.get("lineTotal", 0) or 0)
        preview.append(f"{name} x{quantity} (${line_total:.2f})")

    return (
        f"Cart has {item_count} items. subtotal=${subtotal:.2f}, "
        f"discount=${discount:.2f}, total=${total:.2f}, coupon={coupon}. "
        f"Items: {'; '.join(preview)}."
    )


def summarize_history(history: list[dict[str, Any]]) -> str:
    if not history:
        return "No recent chat history."

    lines = []
    for message in history[-6:]:
        role = "assistant" if message.get("role") == "assistant" else "user"
        text = str(message.get("text", "")).strip()
        if text:
            lines.append(f"{role}: {text}")

    return "\n".join(lines) if lines else "No recent chat history."


def build_prompt_payload(question: str, current_page: str, cart: dict[str, Any], history: list[dict[str, Any]]) -> tuple[str, str]:
    relevant_chunks = select_relevant_context(question)
    context_sections = []
    for chunk in relevant_chunks:
        context_sections.append(f"[{chunk.source}]\n{chunk.text}")

    context_text = "\n\n".join(context_sections) if context_sections else "No extra file snippets matched the question closely."
    system_instruction = CURATED_CONTEXT
    user_prompt = f"""
User question about ShopNova:
{question}

Live app state:
- Current page: {current_page or 'unknown'}
- {summarize_cart(cart)}

Recent chat:
{summarize_history(history)}

Relevant project context:
{context_text}

Answer the user's question about this ShopNova application. Be concise, practical, and truthful.
If something is only an inference from the context, say so.
""".strip()
    return system_instruction, user_prompt


def get_api_key() -> str | None:
    return os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")


def extract_gemini_text(payload: dict[str, Any]) -> str:
    texts: list[str] = []
    for candidate in payload.get("candidates", []):
        content = candidate.get("content", {})
        for part in content.get("parts", []):
            text = part.get("text")
            if text:
                texts.append(text.strip())

    answer = "\n\n".join(text for text in texts if text).strip()
    if answer:
        return answer

    prompt_feedback = payload.get("promptFeedback", {})
    block_reason = prompt_feedback.get("blockReason")
    if block_reason:
        raise RuntimeError(f"Gemini blocked the response: {block_reason}")

    raise RuntimeError("Gemini returned no text content.")


def call_gemini(question: str, current_page: str, cart: dict[str, Any], history: list[dict[str, Any]]) -> dict[str, Any]:
    api_key = get_api_key()
    if not api_key:
        raise RuntimeError("GEMINI_API_KEY is not set.")

    model = os.getenv("SHOPNOVA_GEMINI_MODEL", DEFAULT_MODEL)
    system_instruction, user_prompt = build_prompt_payload(question, current_page, cart, history)
    endpoint = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

    body = {
        "system_instruction": {
            "parts": [{"text": system_instruction}]
        },
        "contents": [
            {
                "role": "user",
                "parts": [{"text": user_prompt}]
            }
        ],
        "generationConfig": {
            "temperature": 0.2,
            "maxOutputTokens": 700
        }
    }

    req = request.Request(
        endpoint,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "x-goog-api-key": api_key,
        },
        method="POST",
    )

    try:
        with request.urlopen(req, timeout=60) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="ignore")
        try:
            message = json.loads(details).get("error", {}).get("message", details)
        except json.JSONDecodeError:
            message = details or exc.reason
        raise RuntimeError(f"Gemini request failed: {message}") from exc
    except error.URLError as exc:
        raise RuntimeError(f"Gemini request failed: {exc.reason}") from exc

    return {
        "answer": extract_gemini_text(payload),
        "provider": "gemini",
        "model": model,
    }


POLLINATIONS_ENDPOINT = "https://text.pollinations.ai/openai"
POLLINATIONS_MODEL = os.getenv("SHOPNOVA_POLLINATIONS_MODEL", "openai")


def call_pollinations(question: str, current_page: str, cart: dict[str, Any], history: list[dict[str, Any]]) -> dict[str, Any]:
    """Free, keyless LLM via Pollinations (OpenAI-compatible chat completions)."""
    system_instruction, user_prompt = build_prompt_payload(question, current_page, cart, history)

    messages: list[dict[str, str]] = [{"role": "system", "content": system_instruction}]
    for msg in (history or [])[-6:]:
        role = "assistant" if msg.get("role") == "assistant" else "user"
        text = str(msg.get("text", "")).strip()
        if text:
            messages.append({"role": role, "content": text})
    messages.append({"role": "user", "content": user_prompt})

    body = {
        "model": POLLINATIONS_MODEL,
        "messages": messages,
        "temperature": 0.4,
    }

    req = request.Request(
        POLLINATIONS_ENDPOINT,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "User-Agent": "Mozilla/5.0 (ShopNovaBot)",
            "Accept": "application/json, text/plain, */*",
        },
        method="POST",
    )

    try:
        with request.urlopen(req, timeout=60) as response:
            raw = response.read().decode("utf-8")
    except error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"Pollinations request failed: {details or exc.reason}") from exc
    except error.URLError as exc:
        raise RuntimeError(f"Pollinations request failed: {exc.reason}") from exc

    try:
        payload = json.loads(raw)
        answer = payload["choices"][0]["message"]["content"].strip()
    except (json.JSONDecodeError, KeyError, IndexError, TypeError):
        answer = raw.strip()

    if not answer:
        raise RuntimeError("Pollinations returned no text.")

    return {"answer": answer, "provider": "pollinations", "model": POLLINATIONS_MODEL}


class ShopNovaHTTPServer(ThreadingHTTPServer):
    allow_reuse_address = True


class ShopNovaHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args: Any, **kwargs: Any) -> None:
        super().__init__(*args, directory=str(FRONTEND_DIR), **kwargs)

    def log_message(self, format: str, *args: Any) -> None:
        super().log_message(format, *args)

    def do_OPTIONS(self) -> None:
        self.send_response(HTTPStatus.NO_CONTENT)
        self._send_cors_headers()
        self.end_headers()

    def do_GET(self) -> None:
        if self.path == "/api/chat/status":
            self._handle_chat_status()
            return
        super().do_GET()

    def do_POST(self) -> None:
        if self.path == "/api/chat":
            self._handle_chat_completion()
            return
        self.send_error(HTTPStatus.NOT_FOUND, "Unknown API endpoint")

    def end_headers(self) -> None:
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def _send_cors_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")

    def _send_json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self._send_cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _read_json_body(self) -> dict[str, Any]:
        raw_length = self.headers.get("Content-Length", "0")
        try:
            length = int(raw_length)
        except ValueError:
            raise ValueError("Invalid Content-Length header")

        payload = self.rfile.read(length)
        try:
            return json.loads(payload.decode("utf-8"))
        except json.JSONDecodeError as exc:
            raise ValueError("Request body must be valid JSON") from exc

    def _handle_chat_status(self) -> None:
        api_key = get_api_key()
        model = os.getenv("SHOPNOVA_GEMINI_MODEL", DEFAULT_MODEL)
        self._send_json(
            {
                "aiEnabled": True,
                "provider": "gemini" if api_key else "pollinations",
                "model": model if api_key else POLLINATIONS_MODEL,
            }
        )

    def _handle_chat_completion(self) -> None:
        try:
            body = self._read_json_body()
        except ValueError as exc:
            self._send_json({"error": str(exc)}, HTTPStatus.BAD_REQUEST)
            return

        question = str(body.get("question", "")).strip()
        current_page = str(body.get("currentPage", "")).strip()
        cart = body.get("cart") if isinstance(body.get("cart"), dict) else {}
        history = body.get("history") if isinstance(body.get("history"), list) else []

        if not question:
            self._send_json({"error": "question is required"}, HTTPStatus.BAD_REQUEST)
            return

        errors: list[str] = []
        if get_api_key():
            try:
                payload = call_gemini(question, current_page, cart, history)
                self._send_json(payload, HTTPStatus.OK)
                return
            except RuntimeError as exc:
                errors.append(f"gemini: {exc}")

        try:
            payload = call_pollinations(question, current_page, cart, history)
            self._send_json(payload, HTTPStatus.OK)
            return
        except RuntimeError as exc:
            errors.append(f"pollinations: {exc}")

        self._send_json({"error": "; ".join(errors) or "No AI provider available."}, HTTPStatus.BAD_GATEWAY)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Serve ShopNova with Gemini-backed chatbot support.")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"Port to listen on. Default: {DEFAULT_PORT}")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    server = ShopNovaHTTPServer(("127.0.0.1", args.port), ShopNovaHandler)
    api_key = get_api_key()
    model = os.getenv("SHOPNOVA_GEMINI_MODEL", DEFAULT_MODEL)

    print(f"ShopNova server running at http://127.0.0.1:{args.port}")
    if api_key:
        print(f"Gemini AI is enabled with model: {model}")
    else:
        print("Gemini AI is disabled. Set GEMINI_API_KEY to enable live chatbot replies.")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping ShopNova server...")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
