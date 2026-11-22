# Where to publish — and why not Codeberg

## The Codeberg problem (as of 2026-11)

Codeberg changed its Terms of Use: **"You must not share projects that mostly consist of code
written by 'generative AI'-tools"** — the stated reasons are an unclear copyright status and
insufficient safeguards against harmful code. That applies to anything hosted there, issues
included.

KaoriReader says out loud in its README that it was **vibecoded**. That is exactly what the
clause describes. So:

- **do not file the IzzyOnDroid request through Codeberg** — it risks a flag on the account
  and the content being removed,
- do not "just not mention the AI part" in the request. That would be a lie, and a pointless one:
  the code is public and the README already says it.

IzzyOnDroid stays on the shelf. The ready-to-paste request is kept in git history if their
tracker ever moves off Codeberg or the policy changes.

## What still works, ranked by effort vs reach

| Channel | Cost | Reach | Notes |
|---|---|---|---|
| **GitHub Releases + Obtainium** | 0 | small but loyal | already live; the only channel with no rules about AI |
| **Reddit** (r/androidapps, r/LightNovels, r/ebooks) | 0 | big, one-shot | best reach per hour; posts are ready in `promotion.md` |
| **Show HN** | 0 | lottery | the hook: "an AI companion that physically cannot spoil the book" |
| **Google Play** | 25 USD once | biggest by far | does not ban AI apps; requires disclosure. Shortest real path to 100+ downloads |
| **Aptoide / APKPure** | 0 | medium, sideload crowd | accept a plain APK, no source build; they are proprietary stores though |
| **F-Droid (main)** | 0 | big among purists | requires **building from source** on their servers (recipe + `fdroiddata` merge request); no AI ban, but weeks of process, and we would carry a `NonFreeNet` anti-feature (bring your own paid API key) |

## Recommendation

1. **Reddit** — one good post in r/androidapps plus r/LightNovels. Free, measurable in 48 h.
2. **Google Play** — if 100 downloads has to be a fact and not a "maybe". 25 USD, a few hours of
   metadata (description, icon and screenshots are already done; a public privacy-policy URL is not).
   Play allows AI features, it only requires disclosure in Data Safety and Content Rating.
3. Aptoide/APKPure as a free extra if Play feels like too much.
4. IzzyOnDroid — only once their tracker leaves Codeberg.

## What to prepare for Play

- [ ] developer account (25 USD, identity verification)
- [ ] a public privacy-policy URL (a `PRIVACY.md` in the repo plus GitHub Pages, or any hosted page)
- [ ] Data Safety form: nothing collected, except what the user themselves sends to their own LLM
- [ ] Content rating questionnaire (~10 minutes)
- [ ] a signed **AAB** (`bundleRelease`) — Play does not accept APKs for new apps
- [ ] disclose the AI feature in the listing (already in the description)
