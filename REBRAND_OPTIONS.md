# Rename options for `Halo-patches`

_Research note — 3 November 2026. **Decision:** `Proteus Patches` (`proteus-patches`).
Local identity files are renamed; the GitHub repository rename is still pending because
the current token receives HTTP 403 from the rename API. Use GitHub → Settings → Repository
name, then update the local remote (`git remote set-url origin ...`). See §7._

The repo is `ineednotitle/Halo-patches`: 236 packages, 304 patch entries, 1 star, and
currently **no description and no topics** on GitHub. A rename is a good moment to fix
all of that at once.

---

## 1. Why renaming is a good idea

- **"Halo" is a Microsoft game trademark**, and in Android/ROM circles it reads as either
  the Xbox franchise or a custom-ROM project. For a public patch bundle, that is search
  noise at best.
- **The name says nothing about the product.** The bundle's actual identity is
  "one Morphe bundle that patches hundreds of apps".
- **Internal naming is already inconsistent**, which makes the public name feel
  unmaintained: `package.json` is still `doom-reddit-patches`, `settings.gradle.kts`
  mentions a "Holopatch Patches bundle", and `group = "app.Halo.patches"`.
- **Greek-epic names are memorable, brandable, and uncrowded** in this space — most
  competing bundles use literal names ("XYZ patches", "ReVanced Extended", etc.).

---

## 2. What makes a good name here

1. One to three syllables, easy to say and spell after hearing it once.
2. Works as a lowercase GitHub slug (`nostos-patches`), as a display name
   ("Nostos Patches"), and in a sentence ("add the Nostos source").
3. Evokes *transformation, protection, craft, or homecoming* — what a patch bundle does.
4. Not a malware-adjacent word (see §6).
5. Not already used by a major app, framework, or ROM in the same niche.

---

## 3. Shortlist

All slugs below were checked with the GitHub API on 3 Nov 2026 and are **free under
`ineednotitle`**. GitHub names are per-owner, so your namespace is what matters; the
"global exact-name hits" column is only about discoverability.

| Greek | Meaning | Why it fits a patch bundle | Suggested slug | Global exact-name hits |
|---|---|---|---|---|
| **Hoplon** (ὅπλον) | heavy infantry shield; later "tool, implement" | Protection + tooling; keeps the **H** of Halo; Halo → Hoplon reads as an evolution | `hoplon-patches` | 0 |
| **Nostos** (νόστος) | homecoming — the Odyssey's whole arc | Every app "comes home" to how you want it; short, calm, unique | `nostos-patches` | 0 |
| **Rhapsode** (ῥαψῳδός) | "stitcher of songs" (*rhaptein* = stitch + *aoidē* = song) | Patching is literally stitching code together; the most conceptually apt name on the list | `rhapsode-patches` | 0 |
| **Polytropos** (πολύτροπος) | "of many turns" — the first epithet of Odysseus | A bundle that turns apps many ways; distinctive, zero ambiguity | `polytropos` or `polytropos-patches` | 19 loose matches |
| **Proteus** (Πρωτεύς) | shape-shifting sea god | Patches reshape an app without changing what it is | `proteus-patches` | 0 |
| **Oneiros** (ὄνειρος) | dream | Morphe (the framework) is the god of dreams; thematically aligned | `oneiros-patches` | 0 |
| **Metis** (μῆτις) | cunning intelligence | Clever, surgical patches rather than brute force | `metis-patches` | 0 |
| **Penelope** (Πηνελόπη) | weaves by day, unweaves by night | Patch / revert symmetry; memorable story | `penelope-patches` | 0 |
| **Ithaka** (Ἰθάκη) | Odysseus's island, the destination | "The app you actually wanted"; also a famous Cavafy poem | `ithaka-patches` | 0 |
| **Moly** (μῶλυ) | herb Hermes gives Odysseus to resist Circe | Antidote/protection — fits privacy and anti-tracking patches | `moly-patches` | 0 |
| **Kleos** (κλέος) | glory, renown | Short, punchy; less descriptive | `kleos-patches` | 0 |
| **Aegis** (αἰγίς) | Zeus/Athena's shield | Protection; common in security branding | `aegis-patches` | 2 |
| **Homer** | the poet himself | Instantly recognisable, easy to spell | `homer-patches` | 0 |
| **Iliad** | the Trojan War epic | You asked for it; it is free. But it is *war*, not *homecoming*, and the bundle is not war-themed | `iliad-patches` | 0 |

---

## 4. Top three, with a pitch

### 4.1 Hoplon Patches — `hoplon-patches`

> *The shield and the toolkit.*

- **Best continuity:** Halo → Hoplon keeps the initial, the two-syllable rhythm, and the
  "armour" association, so existing users recognise it instantly.
- A hoplon is both **defensive gear and an implement** — exactly what this bundle is:
  privacy/security patches plus tools that reshape apps.
- Display name: **Hoplon Patches**. Tagline: *"One bundle. Every app, yours."*
- Risk: "hoplon" is also a Lisp dialect, so search results will include a little noise.

### 4.2 Nostos Patches — `nostos-patches`

> *Bring every app home.*

- The Odyssey's central idea, and the most elegant word on the list. Two syllables,
  easy to spell, zero exact-name collisions.
- Scales beyond patches if you ever ship a manager or a theme: `nostos.dev`,
  `Nostos Manager`, `Nostos Patches`.
- Risk: abstract — a first-time visitor won't know it patches Android apps until they
  read the description. Fix that with a clear GitHub description and README subtitle.

### 4.3 Rhapsode Patches — `rhapsode-patches`

> *Stitched together, line by line.*

- The etymology is the pitch: a rhapsode is a **stitcher of songs**; a patcher stitches
  code into an app. It is the only name here that *means* what you do.
- Distinctive and memorable; works well as a personal brand.
- Risk: spelling/pronunciation friction ("rap-sode"), and it sounds slightly literary
  for a tooling project.

---

## 5. Honourable mentions

- **Polytropos Patches** — the smartest deep cut; "of many turns" is literally the
  Odyssey's first description of its hero. Longer, but very distinctive.
- **Oneiros Patches** — the natural companion to the Morphe framework (dream god), if you
  want the bundle to feel part of that ecosystem rather than independent.
- **Proteus Patches** — best one-word metaphor for transformation; slightly generic in
  security branding.
- **Ithaka Patches** — warm, aspirational, and pairs beautifully with "Nostos" if you
  ever want a two-product naming system (Nostos = the journey, Ithaka = the destination).
- **Homer Patches** — the safest, most recognisable option; also the least distinctive.
- **Iliad Patches** — fine if you love it, but note the Iliad is the Trojan War epic, not
  the Odyssey; the tone is siege and wrath, not homecoming.

---

## 6. Names to avoid

| Name | Why |
|---|---|
| **Trojan** | The original "payload hidden inside a gift"; in an APK-patching project this reads as malware. |
| **Achilles** | "Achilles heel" is a vulnerability; ironic for a security-conscious bundle. |
| **Cassandra** | The prophet nobody believes — bad omen for update reliability. |
| **Pandora** | The box of bugs; avoid. |
| **Sirens / Charybdis / Scylla** | Lures and shipwrecks; also generic security/alert branding. |
| **Odyssey** | Heavily used (Honda, many apps); hard to rank for. |
| **Halo (current)** | Microsoft trademark and unrelated to the product. |

---

## 7. Rename checklist

Do this **between releases**, not during a release PR, so the generated
`patches-bundle.json` picks up the new slug cleanly.

### 7.1 Rename on GitHub

```bash
cd ~/Halo-patches
gh repo rename hoplon-patches --yes
git remote set-url origin https://github.com/ineednotitle/hoplon-patches.git
git remote -v   # verify
```

GitHub keeps redirects from the old slug for web, git, API and release downloads, but do
not rely on them forever.

### 7.2 Update repository metadata (currently empty)

```bash
gh repo edit \
  --description "Morphe patch bundle for 230+ Android apps — premium, privacy, UX, TV." \
  --add-topic morphe,revanced,android,patches,patch-bundle,apk
```

### 7.3 Update the files that hard-code the name

| File / line | Current | Change to |
|---|---|---|
| `settings.gradle.kts:1` | `rootProject.name = "Halo-patches"` | new slug |
| `settings.gradle.kts:24` | comment: "Holopatch Patches bundle" | fix the stale comment |
| `patches/build.gradle.kts:1` | `group = "app.Halo.patches"` | e.g. `app.hoplon.patches` (Maven coords only) |
| `patches/build.gradle.kts:5` | `name = "Halo Patches"` | display name (what Morphe Manager shows) |
| `patches/build.gradle.kts:7` | `source = ".../Halo-patches"` | new URL |
| `patches/build.gradle.kts:10` | `website = "…github=ineednotitle/Halo-patches"` | new add-source URL |
| `README.md:1` | `# 👋🧩 Halo Patches` | new title |
| `README.md:40` | release link with old slug | regenerated on next release; can update now |
| `README.md:3736` | `https://github.com/YOU/Halo-patches` | new slug |
| `package.json` | `"name": "doom-reddit-patches"` | new slug + real description |
| `patches-bundle.json` | old slug in description/download URL | **do not hand-edit**; regenerated by semantic-release |
| `CHANGELOG.md` | historical links to old slug | leave as-is; redirects cover them |

### 7.4 Decide on bundle identity (separate from repo name)

- **Repo slug** = URL. Safe to change; redirects.
- **`about { name = ... }`** = the bundle name shown inside Morphe Manager. Changing it is
  cosmetic for users, but it is a visible rebrand; patch names and selections stay intact.
- **`patches-*.mpp` asset name and Maven group** = infrastructure. Keep stable unless you
  have a reason to change them.
- Recommended sequencing: rename the **repo** first, release once, then change the
  **bundle display name** in the following release. Two small steps beat one confusing
  release where both URL and in-app name change.

### 7.5 Verify after renaming

```bash
gh repo view --json name,url,description,repositoryTopics
git ls-remote origin HEAD                 # remote still reachable
curl -sIL https://github.com/ineednotitle/<old-slug> | head -1   # 301 redirect
```

Then, from a clean machine/profile:

1. Open `https://morphe.software/add-source?github=ineednotitle/<new-slug>` and add the
   source in Morphe Manager.
2. Confirm the bundle loads and the patch list renders.
3. Download the latest `.mpp` from the new release URL and apply one patch.
4. Confirm an old `patches-bundle.json` URL still resolves via redirect.

### 7.6 Announce it

Add a short note at the top of the README and in the next release notes:

> **Renamed:** `Halo-patches` → `Hoplon-patches`. Old links redirect, but update your
> patch source to `ineednotitle/hoplon-patches`.

---

## 8. Recommendation

If you want the smoothest transition and a name that still "reads" like the old one:
**Hoplon Patches** (`hoplon-patches`).

If you want the most elegant, ownable brand: **Nostos Patches** (`nostos-patches`).

If you want the name with the best story: **Rhapsode Patches** (`rhapsode-patches`).

All three are free under your account, short enough to type, and distinct from the
Halo/Microsoft namespace. My pick: **Hoplon Patches** — the Halo → Hoplon bridge keeps
whatever recognition the current name has, while the meaning (shield + tool) matches what
the bundle actually does.
