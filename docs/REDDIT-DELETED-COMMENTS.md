# Show deleted comments — Reddit

**App:** Reddit (`com.reddit.frontpage`) · **Patch:** Show deleted comments · **Default:** off

Shows the original text of comments that were deleted by their author or removed by a
moderator. Reddit does not send the text of such comments back to clients, so the only
possible source is a third-party archive — this patch queries one at view time and marks
what it restored.

## How it works

1. The patch hooks the same verified `Listing.<init>` children choke point used by
   "Block AutoMod comments" / "Content filters" (plus `getChildren()` when present), via
   `injectListingChildrenFilter(...)` in `reddit/shared/RedditListingChildren.kt`.
2. `RedditDeletedCommentHelper.undeleteComments(List)` walks every row. A row is in
   scope only when it is comment-shaped (`t1_*` kind, a comment-model class name, or the
   usual parent/depth markers) **and** its body is exactly `[deleted]` or `[removed]`.
3. All in-scope comment ids in the listing are looked up in **one batched request**
   against the free [Arctic Shift](https://github.com/ArthurHeitmann/arctic_shift) id
   endpoint (`GET /api/comments/ids?ids=…&fields=id,body`, no API key). If Arctic Shift
   errors or has no copy, the helper falls back to [PullPush](https://pullpush.io)
   (`GET /reddit/search/comment/?ids=…&size=…`, also keyless). Listings with no deleted
   comments never touch the network.
4. Restored text is written back into the comment model prefixed with `[archived] ` so
   provenance is always visible. Bodies are replaced through a `setBody`-style setter
   when the model exposes one; otherwise through instance `String` fields whose current
   value is exactly the placeholder. Author/user-like fields are never touched.
5. Results — including misses — are cached in memory for the lifetime of the process.

## Privacy notes

- Opening a thread that contains deleted comments sends **only the ids** of those
  comments to the archive host. No account information, no other comment ids, nothing
  else. The archive can infer "someone viewed thread X recently", nothing more.
- The query is read-only. Nothing is posted, voted, or written anywhere.
- This is why the patch is off by default.

## Limits (honest)

- **Coverage is archive coverage.** A comment is restored only if it was archived at
  some point (Arctic Shift covers public Reddit roughly 2005–present with a lag of
  hours to weeks; PullPush similar). Very recent deletions and comments never archived
  stay `[deleted]`.
- **Delete timing matters.** Deleted self-posts (`t3_*`) are out of scope; only comments.
- **Render timing.** Restoration happens when the listing is built, i.e. before the
  comment UI binds its text. If Reddit someday binds comment text lazily from a second
  model, re-verify on-device.
- **Network on the load path.** Comment pages are deserialized off the main thread, so
  the batched request (≤ 100 ids per call, 2.5 s connect / 4 s read timeout) adds
  latency there, not on the UI thread. Misses are cached for the process lifetime.

## Verification status

- Archive endpoints verified directly on 2026-11-06 (HTTP 200, `{"data":[…]}` envelope).
- The bytecode injection rides the `Listing.children` anchor already verified on Reddit
  2026.42.0 by "Block AutoMod comments".
- The runtime restore path (reflection into the comment model) has **not** been
  exercised on-device. It fails safe: any exception, missing setter, or field-layout
  drift leaves the stock `[deleted]` row intact and logs to stdout.
- Static verification only in this workspace (no JDK/APK); run the CI build and a real
  2026.42.0 APK before release.

## Re-verifying after a Reddit update

1. Confirm `Lcom/reddit/domain/model/listing/Listing;->children` still exists with the
   same constructor writes (see `reddit/shared/RedditListingChildren.kt` header).
2. Confirm comment rows flow through that container (they did on 2026.42.0).
3. If the archive lookup fails silently on-device, check `adb logcat` for
   `[Proteus] Show deleted comments:` lines — endpoint changes or rate limits are
   logged there, and the comment is left `[deleted]`.
