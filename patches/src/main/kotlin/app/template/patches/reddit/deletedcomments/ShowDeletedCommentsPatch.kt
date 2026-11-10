package app.template.patches.reddit.deletedcomments

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.REDDIT_COMPATIBILITY
import app.template.patches.reddit.shared.injectListingChildrenFilter

/**
 * Show deleted posts and comments — Reddit (com.reddit.frontpage)
 *
 * Reddit renders posts and comments whose author deleted them (or that a
 * moderator removed) as `[deleted]` / `[removed]` and does NOT send the
 * original text to clients, so an archive is the only source.  This patch
 * hooks the same verified `Listing.children` choke point as "Block AutoMod
 * comments" / "Content filters" and, for each deleted row, looks the id up in
 * a public archive and swaps in the archived text (prefixed with
 * `[archived] `).
 *
 * HOW IT WORKS
 *   1. `Listing.<init>` (and `getChildren()` when present) is wrapped with
 *      `RedditDeletedCommentHelper.undeleteContent(List)`.  Listings are
 *      deserialized off the main thread, so the synchronous archive request
 *      does not run on the UI thread.
 *   2. The helper collects the ids of exactly the deleted/removed rows in the
 *      listing, issues ONE batched HTTP GET per listing to the free Arctic
 *      Shift id lookup (`/api/comments/ids` and `/api/posts/ids`, no API key)
 *      and falls back to PullPush (`/reddit/search/comment`,
 *      `/reddit/search/submission`, also keyless) when Arctic Shift errors or
 *      has no copy.  Listings with no deleted content never touch the network.
 *   3. Restored text is prefixed with `[archived] ` so provenance is always
 *      visible.  For self-posts both the selftext and a deleted title are
 *      restored.  Results (including misses) are cached in memory for the
 *      process lifetime.
 *
 * HONEST LIMITS
 *   - Coverage depends entirely on the archive: rows archived before the
 *     deletion can be restored; rows never archived (very recent, or posted
 *     after the archive's collection horizon) stay `[deleted]`.
 *   - The archive lags live Reddit; content deleted minutes after posting may
 *     be missing.
 *   - Every listing that contains deleted content sends those ids (and only
 *     the ids) to the archive host.  Enable only if you're comfortable with
 *     that; hence off by default.
 *   - Link posts have no selftext to restore; only a deleted title can be
 *     recovered for them.
 *
 * Verified endpoints on 2026-11-06 against
 *   GET https://arctic-shift.photon-reddit.com/api/comments/ids?ids=t1_dppum98&fields=id,body
 *   → HTTP 200, {"data":[{"body":"…","id":"dppum98"}]} (no key required).
 *   GET https://arctic-shift.photon-reddit.com/api/posts/ids?ids=t3_…&fields=id,selftext,title
 *   → HTTP 200, {"data":[{"id":"…","selftext":"…","title":"…"}]}.
 *
 * Implementation status: verified statically against a decompiled Reddit
 * 2026.42.0 APK.  The injection rides the already-verified `Listing.children`
 * anchor used by "Block AutoMod comments" (see
 * `reddit/shared/RedditListingChildren.kt`), so the bytecode side is low-risk.
 * The runtime restore path (reflection into the post/comment models + archive
 * fetch) still fails safe to the stock `[deleted]` row and never throws into
 * app code.
 */

private const val DELETED_COMMENT_HELPER =
    "Lapp/template/extension/extension/RedditDeletedCommentHelper;"

@Suppress("unused")
val redditShowDeletedCommentsPatch = bytecodePatch(
    name = "Show deleted posts and comments",
    description = "Shows the original text of posts and comments deleted or removed on " +
        "Reddit, restored at render time from the free Arctic Shift archive (no API key; " +
        "falls back to PullPush). Only the ids of deleted rows in the listings you open " +
        "are sent, and restored text is prefixed with [archived]. Coverage depends on the " +
        "archive; needs network. Off by default.",
    default = false,
) {
    compatibleWith(REDDIT_COMPATIBILITY)

    extendWith("extensions/extension.mpe")

    execute {
        injectListingChildrenFilter(
            helperMethod = "undeleteContent",
            logTag = "Show deleted posts and comments",
            helperClass = DELETED_COMMENT_HELPER,
        )
    }
}
