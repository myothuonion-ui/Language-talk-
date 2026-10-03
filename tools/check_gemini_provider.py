#!/usr/bin/env python3
"""Check app-generated request fixtures with a locally supplied key; never run in public CI."""
import argparse
import asyncio
import base64
import copy
import io
import json
import os
import struct
import time
import urllib.error
import urllib.request
import wave
from pathlib import Path

BASE = "https://generativelanguage.googleapis.com/v1beta/"


def interaction(key, body):
    request = urllib.request.Request(BASE + "interactions", data=json.dumps(body).encode(),
        headers={"x-goog-api-key": key, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            root = json.loads(response.read())
            content = [part for step in root.get("steps", []) if step.get("type") == "model_output" for part in step.get("content", [])]
            return {"http": response.status, "status": root.get("status")}, content
    except urllib.error.HTTPError as error:
        return {"http": error.code, "error": error.read().decode().replace(key, "[REDACTED]")[:1000]}, []


def input_pcm(wav):
    with wave.open(io.BytesIO(wav), "rb") as source:
        assert source.getnchannels() == 1 and source.getsampwidth() == 2, "Expected mono PCM16 WAV"
        rate = source.getframerate()
        pcm = source.readframes(source.getnframes())
    samples = struct.unpack("<%dh" % (len(pcm) // 2), pcm)
    # Sample-count preserving resampling for the short synthetic learner fixture.
    converted = [samples[min(len(samples) - 1, i * rate // 16000)] for i in range(len(samples) * 16000 // rate)]
    return struct.pack("<%dh" % len(converted), *converted)


async def live(key, setup, wav):
    import websockets
    result = {"setup": False, "tool_calls": 0, "turns": 0, "audio_bytes": 0, "heard_text": "", "spoken_text": ""}
    uri = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + key
    pcm = input_pcm(wav)
    try:
        async with websockets.connect(uri, open_timeout=25, close_timeout=3, max_size=12_000_000) as socket:
            await socket.send(json.dumps(setup))
            deadline = time.monotonic() + 55
            while time.monotonic() < deadline:
                root = json.loads(await asyncio.wait_for(socket.recv(), timeout=20))
                if "error" in root:
                    result["error"] = root["error"]
                    break
                if "setupComplete" in root:
                    result["setup"] = True
                    await socket.send(json.dumps({"clientContent": {"turns": [{"role": "user", "parts": [{"text": "Start one greeting phrase. Say it briefly, then wait for me."}]}], "turnComplete": True}}))
                calls = root.get("toolCall", {}).get("functionCalls", [])
                if calls:
                    result["tool_calls"] += len(calls)
                    await socket.send(json.dumps({"toolResponse": {"functionResponses": [{"id": call.get("id"), "name": call["name"], "response": {
                        "stage": "REPEAT", "targetSentence": "안녕하세요. 잘 부탁드립니다.", "instruction": "Say one phrase, then wait for the learner."}} for call in calls]}}))
                server = root.get("serverContent", {})
                for part in server.get("modelTurn", {}).get("parts", []):
                    result["audio_bytes"] += len(base64.b64decode(part.get("inlineData", {}).get("data", "")))
                result["spoken_text"] += server.get("outputTranscription", {}).get("text", "")
                result["heard_text"] += server.get("inputTranscription", {}).get("text", "")
                if server.get("turnComplete"):
                    result["turns"] += 1
                    if result["turns"] >= 2:
                        break
                    # Simulate the phone's 16 kHz microphone, including the answer pause.
                    for chunk in [pcm[i:i+6400] for i in range(0, len(pcm), 6400)] + [bytes(6400)] * 12:
                        await socket.send(json.dumps({"realtimeInput": {"audio": {"data": base64.b64encode(chunk).decode(), "mimeType": "audio/pcm;rate=16000"}}}))
                        await asyncio.sleep(.2)
                    await socket.send(json.dumps({"realtimeInput": {"audioStreamEnd": True}}))
        result["passed"] = bool(result["setup"] and result["turns"] >= 2 and result["heard_text"] and result["audio_bytes"] and result["tool_calls"])
    except Exception as error:
        result.update(passed=False, error=type(error).__name__ + ": " + str(error).replace(key, "[REDACTED]"))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixtures", type=Path, help="requests.json exported in build-test-reports by ProviderContractTest")
    parser.add_argument("--key-file", type=Path, help="Private local key file; alternatively set GEMINI_API_KEY")
    parser.add_argument("--report", type=Path, help="Write a credential-free JSON result")
    args = parser.parse_args()
    key = args.key_file.read_text().strip() if args.key_file else os.environ.get("GEMINI_API_KEY", "").strip()
    if not key: parser.error("Set GEMINI_API_KEY or supply --key-file")
    fixtures = json.loads(args.fixtures.read_text())
    report = {"text_attempts": []}
    try:
        for model in fixtures["text_models"]:
            body = copy.deepcopy(fixtures["text"])
            body["model"] = model
            result, content = interaction(key, body)
            report["text_attempts"].append(dict(result, model=model))
            if result.get("status") == "completed":
                tutor = json.loads("".join(item.get("text", "") for item in content if item.get("type") == "text"))
                report["text"] = {"passed": bool(tutor.get("reply") and tutor.get("assessment") in ("NONE", "PASSED", "RETRY", "UNSURE")), "model": model}
                break
            if result.get("http") not in (404, 429) and result.get("http", 0) < 500: break
        result, content = interaction(key, fixtures["speech"])
        audio = next((item for item in content if item.get("type") == "audio"), {})
        wav = base64.b64decode(audio.get("data", ""))
        report["speech"] = dict(result, passed=wav.startswith(b"RIFF"), audio_bytes=len(wav), mime=audio.get("mime_type"))
        if report["speech"]["passed"]:
            report["live"] = asyncio.run(live(key, fixtures["live"], wav))
        report["passed"] = all(report.get(item, {}).get("passed") for item in ("text", "speech", "live"))
    except Exception as error:
        report.update(passed=False, error=type(error).__name__ + ": " + str(error).replace(key, "[REDACTED]"))
    serialized = json.dumps(report, ensure_ascii=False, indent=2).replace(key, "[REDACTED]")
    print(serialized)
    if args.report: args.report.write_text(serialized)
    return 0 if report["passed"] else 1


if __name__ == "__main__": raise SystemExit(main())
