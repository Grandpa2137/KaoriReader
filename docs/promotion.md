# Where to post (ready to paste)

Repo: https://github.com/Grandpa2137/KaoriReader
APK: https://github.com/Grandpa2137/KaoriReader/releases/latest

Rule of thumb: lead with the mechanism, not with "another reader app". The spoiler guard is the
only thing here that nobody else has.

---

## 1. IzzyOnDroid (best real traffic, free, no account needed)

Open an issue here: https://github.com/IzzyOnDroid/repo/issues/new?template=request_app.yml

Text to paste:

```
App name: KaoriReader
Package: dev.kaori.reader
Repo: https://github.com/Grandpa2137/KaoriReader
License: MIT
APK: https://github.com/Grandpa2137/KaoriReader/releases
Metadata: fastlane/ present in the repo (en-US), icon 512px included
Summary: EPUB reader with a companion who reads with you — and cannot spoil the book.
No trackers, no ads, no network calls besides the LLM endpoint the user configures.
```

They check: open source licence, no tracking, signed APK, fastlane metadata. All four are done.

---

## 2. Reddit — r/androidapps (weekend, ~Saturday)

Title: `KaoriReader — an EPUB reader whose AI companion literally cannot spoil the book`

```
I read a lot of light novels with an AI "reading buddy" and kept getting one problem:
the model would casually mention something from chapter 200 while I was on chapter 12.

So I built a reader where that cannot happen, because it is not a prompt: the app records
which character ranges you have actually **read** (a page counts after ~2s of dwell time,
jumping with the table of contents or search marks nothing), and only that text is ever
sent to the model. The rest of the book is not in the context at all, so it cannot leak,
even if the model has seen the book during training.

You can check what she was given: the badge in the chat header opens a preview of the
exact text that was sent.

Other bits people usually ask about:
- bring your own key (OpenAI / DeepSeek / OpenRouter / Groq / custom OpenAI-compatible URL)
- memory is local SQLite-ish JSON on the phone, viewable and deletable per item
- personas: Kaori is built in, but you can duplicate her card and rewrite it, or describe
  your own character and let the model fill in the style and rules
- no account, no server, no telemetry, MIT licence
- EPUB 2/3, pages or scroll, library-wide search that jumps to the exact line, TTS,
  bookmarks, backup export/import

GitHub only, no Play Store: https://github.com/Grandpa2137/KaoriReader
APK is in Releases; Obtainium works for auto-updates.
```

Also worth posting (shorter, adjusted):
- r/LightNovels — "an EPUB reader that won't let an AI spoil the LN you're reading"
- r/ebooks — focus on the reader side, mention the companion last
- r/selfhosted — focus on: local memory, own API key, no server
- Wykop / polskie grupy LN — po polsku, krótko, z linkiem do Releases

---

## 3. Show HN

Title: `Show HN: EPUB reader whose AI companion can't spoil the book`

```
The trick is not a prompt, it is an absence of data: the app tracks which character ranges
you have read (page dwell >= 2s, TOC and search jumps don't count) and sends only that to
the model. Unread text is never in the request, so a model that memorised the book still
can't leak it.

Android, Kotlin + Compose, MIT, bring your own API key, memory stored locally.
https://github.com/Grandpa2137/KaoriReader
```

Post between 08:00 and 10:00 UTC on a weekday; do not touch it for the first two hours.

---

## 4. Chasing the 100 downloads

Realistic mix:

| Channel | Expected | Effort |
|---|---|---|
| IzzyOnDroid listing | 30–60 over a month, plus every update | one issue |
| One good r/androidapps post | 20–60 in 48h | one post |
| r/LightNovels + r/ebooks | 10–30 | two posts |
| Show HN | 0–50, variance | one post |
| Polish Discord/fora LN | 10–20 | three messages |

Do not post the same text everywhere on the same day; r/androidapps mods delete anything that
looks like a campaign. Space them out, answer comments — comments are what keep a post visible.

Numbers to watch: GitHub release asset `download_count` (API) is the only real metric.

---

## UWAGA (2026-11-23): IzzyOnDroid odpada

IzzyOnDroid przeniósł zgłoszenia na Codeberg, a Codeberg w nowym ToS zabrania projektów
"mostly consisting of code written by generative AI tools". KaoriReader jest oznaczony jako
vibecoded, więc zgłoszenie tam to ryzyko dla konta. Szczegóły i plan B: `docs/izzyondroid.md`.
Ścieżki, które zostają: Reddit, Show HN, Google Play, Aptoide/APKPure, Obtainium.

