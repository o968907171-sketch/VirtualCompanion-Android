# Custom HTTP TTS Provider Spec — v0.6

Endpoint MUST use HTTPS.

Request:

```json
{
  "text": "角色要朗讀的文字",
  "voice": "optional-voice-id"
}
```

Optional authentication:

`Authorization: Bearer <user supplied key>`

Accepted responses:

1. Raw audio bytes with an `audio/*` Content-Type.
2. JSON with base64 audio:

```json
{"audio_base64":"..."}
```

or

```json
{"audio":"..."}
```

If the endpoint fails, Android TTS is used as fallback.
