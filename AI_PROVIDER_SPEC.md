# AI Provider Spec — v0.6

## Providers

### Local
No network and no API key. Uses DialogueEngine.

### Gemini
- Endpoint family: Google Gemini `models.generateContent`.
- Auth: `x-goog-api-key`.
- Default model at v0.6 development time: `gemini-3.8-flash`.
- Vision uses inline image data.

### Grok
- Base URL: `https://api.x.ai/v1`
- Auth: Bearer API key.
- Uses OpenAI-compatible `/chat/completions` mapping.
- Default model at v0.6 development time: `grok-4.7`.

### OpenAI-compatible
User supplies HTTPS Base URL and model name. Virtual Companion appends `/v1/chat/completions` unless a full chat-completions URL is supplied.

## Fallback
Any missing key, network error, HTTP error, invalid JSON, or empty response falls back to the local Dialogue / PhotoReaction engine. The in-character response remains usable even when cloud AI is unavailable.

## Privacy payload
Chat may send current text, up to 18 recent messages, up to 8 relevant long-term memories, and character persona settings.
Image bytes are sent only after explicit user image selection and Vision is enabled.
