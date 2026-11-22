# Where to post (ready to paste)

Repo: https://github.com/Grandpa2137/KaoriReader
APK: https://github.com/Grandpa2137/KaoriReader/releases/latest

Two hooks, in this order: **the spoiler guard** (nobody else has it) and **chapter translation
with a visible price and an on-device cache** (light-novel readers ask for it constantly).
Everything else is table stakes — mention it at the end, briefly.

**IzzyOnDroid is out** — their tracker moved to Codeberg, and Codeberg's ToS forbids projects
"mostly consisting of code written by generative AI tools". This project says vibecoded in the
README, so filing there is a risk to the account, not a shortcut. Details: `docs/izzyondroid.md`.

---

## 1. Reddit — r/androidapps (weekend, ~Saturday), then r/LightNovels

Title: `KaoriReader — an EPUB reader whose AI companion literally cannot spoil the book`

```
I read a lot of light novels with an AI "reading buddy" and kept hitting the same wall:
the model would mention something from chapter 200 while I was on chapter 12. Prompting it
not to is a coin flip.

So the app does not ask the model nicely — it just doesn't send the text. It records which
character ranges you have actually READ (a page counts after ~2s of dwell time; jumping with
the table of contents or search marks nothing), and only those ranges go into the request.
The rest of the book is never in the context, so it cannot leak, even if the model has seen
the book during training. The badge in the chat header opens a preview of exactly what was sent.

The second thing people keep asking about, so I built it properly:

Translation. One tap in the reader translates the page you're on or the whole chapter, into
19 languages. Two details I cared about:
- it tells you roughly how many tokens it will cost BEFORE the request (chars, tokens in/out),
- the result is cached on the device, so the same chapter never costs twice ("saved, 0 tokens").
Tap READ and the translated chapter opens in the reader as its own document — real pagination,
page counter, TTS — and the original book stays untouched.

The rest:
- bring your own key: OpenAI / DeepSeek / OpenRouter / Groq / custom https endpoint
- memory is local JSON on the phone (facts, open threads, summaries), viewable and deletable
  one by one, no embeddings, no server
- personas: Kaori is built in; duplicate her card and rewrite it, or describe your own character
  (the builder even works without an API key, filling from a template)
- reading marks ("read up to here" / "unread from here") for when you read the same book elsewhere
- no account, no telemetry, MIT, EPUB 2/3, search that jumps to the exact line, TTS, backups

Honest labelling: the app was vibecoded — architecture and every test on a real phone by me,
large parts of the code in pair-programming sessions with an AI assistant. It's in the README
too, I'm not hiding it.

GitHub only, no Play Store: https://github.com/Grandpa2137/KaoriReader
APK in Releases, Obtainium for auto-updates. Happy to answer anything.
```

Shorter variant for r/LightNovels (different opening, same content):

```
TL;DR: an EPUB reader for LN with an AI companion that cannot spoil the book you're reading,
plus one-tap chapter translation that shows the token cost first and caches the result.

The spoiler thing is not a prompt: the app tracks which character ranges you've actually read
and only sends those, so unread pages are simply not in the request.
Translation: page or whole chapter, 19 languages, price shown before you send, results cached
so a re-read costs nothing, and a READ view that pages the translation like a real book while
the original EPUB stays untouched.

BYOK (your own OpenAI-compatible key), local memory, MIT, GitHub only:
https://github.com/Grandpa2137/KaoriReader
```

## 2. Show HN

Title: `Show HN: EPUB reader with an AI companion that cannot spoil the book`

```
The trick is not a prompt but an absence of data. The app records which character ranges you
have actually read (dwell >= 2s per page; TOC and search jumps count for nothing) and sends
only that text. Unread text is never in the request, so a model that memorised the novel still
cannot leak it.

Also: on-demand page/chapter translation with a token-cost estimate before the call and a
device-side cache, so re-reading a chapter is free. BYOK, local memory, MIT, Android + Compose.
https://github.com/Grandpa2137/KaoriReader
```

Post 08:00-10:00 UTC on a weekday and don't touch it for two hours.

## 3. Aptoide / APKPure (free, sideload crowd)

Both accept a plain signed APK, no source build, no AI clause. Upload
`KaoriReader-0.1.9.apk`, paste the short description, use the same screenshots from
`android/fastlane/metadata/android/en-US/images/`. Not open source infrastructure, but it is
free reach — and it does not conflict with the GitHub release.

## 4. What a realistic first month looks like

| Channel | Expected | Effort |
|---|---|---|
| One r/androidapps post | 20-60 in 48 h | one post, answer comments |
| r/LightNovels + r/ebooks | 10-30 | two posts, spaced out |
| Show HN | 0-50, variance | one post |
| Aptoide + APKPure | 10-40 over weeks | two uploads |
| Google Play (if ever) | 100+ organic | 25 USD + AAB + policy URL |
| Polish Discord/fora LN | 10-20 | three messages |

Do not paste the same text everywhere on the same day; r/androidapps mods delete anything that
looks like a campaign. Space the posts, answer every comment — comments are what keeps a post
alive. The only metric that matters is the release asset `download_count` from the GitHub API.
