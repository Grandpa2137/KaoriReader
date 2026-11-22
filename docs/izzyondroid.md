# IzzyOnDroid — gotowe zgłoszenie

Ważne: IzzyOnDroid **nie ma już issue trackera na GitHubie** (to dlatego link z `?template=request_app.yml`
dawał 404). Zgłoszenia idą na **Codeberg**:

**https://codeberg.org/IzzyOnDroid/repodata/issues/new**

Potrzebujesz tam konta Codeberg (darmowe, rejestracja przez e-mail albo GitHub).

## Co już jest w repo (spełnia ich checklistę)

| Ich wymóg | U nas |
|---|---|
| licencja FOSS | MIT (`LICENSE`), plus sekcja o oryginalnych assetach |
| `fastlane/metadata/android/en-US/` | `title.txt`, `short_description.txt` (60 znaków), `full_description.txt` (3 027 znaków) |
| `changelogs/<versionCode>.txt` | `9.txt` (480 B, czysty ASCII) — zgodne z `versionCode = 9` |
| ikona | `images/icon.png`, 512×512 |
| zrzuty | `images/phoneScreenshots/1–4.png`, dociągnięte do proporcji 2:1 |
| release z APK | `v0.1.9`, APK 12,1 MB (limit 30 MB) |
| tag = wersja | `v0.1.9` ↔ `versionName = "0.1.9"` |

## Tekst zgłoszenia (do wklejenia)

Tytuł: `[Request] KaoriReader — EPUB reader with a non-spoiling AI companion`

```
App name: KaoriReader
Package: dev.kaori.reader
Version: 0.1.9 (versionCode 9)
Source: https://github.com/Grandpa2137/KaoriReader
Releases: https://github.com/Grandpa2137/KaoriReader/releases
Licence: MIT (code, persona text and artwork are original work by the author, released under MIT)

Summary: An EPUB reader with a companion who reads with you — and cannot spoil the book.

What it does:
- EPUB 2/3 reader (pages or continuous scroll), library, collections, folder import,
  full-text search inside a book and across the library with exact jump, bookmarks, TTS, stats
- optional AI companion, bring your own OpenAI-compatible key (OpenAI, DeepSeek, OpenRouter,
  Groq, or a custom https endpoint)
- spoiler guard: the app records which character ranges you have actually read (page dwell
  >= 2s; TOC/search jumps do not count) and sends only that text. Unread text never leaves
  the device, so a model that memorised the book still cannot leak it
- local memory (facts, open threads, summaries) stored on the device, no embeddings
- personas: built-in character, duplicate-and-rewrite, or describe your own
- translation of a page or a whole chapter into 19 languages, with a token estimate before
  the request, results cached on disk, and a READ view that shows the translation as its own
  document while the original book is untouched

Network: only what the user triggers — the LLM endpoint they configured, a page fetch when
they paste a URL, and (built-in persona only) a keyless Open-Meteo weather lookup, cached 30 min.

Privacy: no analytics, no crash reporting, no ads, no accounts. API key in Android Keystore,
allowBackup=false.

Fastlane: present in the repo at android/fastlane/metadata/android/en-US/ (title, short and
full description, icon 512px, phoneScreenshots 1-4, changelogs/9.txt).
```

## Po wysłaniu

- dostaniesz etykietę `needs/on-device-testing` — ktoś z ich ekipy (albo ochotnik) testuje apkę,
- jeśli poproszą o poprawki, prawie zawsze chodzi o `short_description` > 80 znaków,
  changelog niezgodny z `versionCode` albo proporcje zrzutów — **te trzy rzeczy są już pilnowane**,
- po akceptacji apka pojawia się w `apt.izzysoft.de/fdroid` i w klientach Droid-ify / Neo Store,
- każdy nowy release na GitHubie **musi** mieć `changelogs/<versionCode>.txt` w tym samym commicie/tagu,
  inaczej aktualizacja przyjdzie z pustym changelogiem.

## Pamiętaj o rytmie wersji

`versionCode` musi rosnąć przy każdym release:
`9` → następny to `10` + `changelogs/10.txt`.
