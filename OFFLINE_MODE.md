# Offline Mode / 離線模式

Virtual Companion v0.6 is **offline-first**. The app must remain useful without Internet access.

## Works without Internet

- Android overlay companion and multi-character mode
- Character images, 7–38 portrait slots, stickers and kaomoji
- Tap / long press / drag interactions
- Local rule-based dialogue and emotion engine
- Recent memory + local long-term memory recall
- Sleep / wake and local-time greetings
- Battery and headphone reactions
- Character Pack import / export
- Imported Gemini / Character.AI / Grok / generic chat history after the file has been placed on the phone
- Android TTS **only when the installed TTS engine provides an offline voice**

## Strict Offline Mode

When enabled, the app does not issue requests to cloud AI, Vision, or Custom HTTP TTS endpoints. Provider keys and settings are retained locally so the user can switch online later.

If strict mode is off but the phone has no validated Internet connection, cloud chat / Vision automatically fall back to the local engines instead of waiting for a remote failure.

## Not the same as a local LLM

The current offline dialogue engine is rule/personality/memory based. It is useful offline but is not presented as equivalent to Gemini/Grok-class models. A future optional local-model pack can add on-device LLM inference without making a multi-gigabyte model mandatory for every user.
