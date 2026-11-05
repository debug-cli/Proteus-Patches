package app.template.patches.reddit.contentfilter

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.string
import app.template.patches.reddit.premium.AccountFromJsonFingerprint
import app.template.patches.reddit.premium.AccountJsonAdapterConstructorFingerprint
import app.template.patches.reddit.premium.forceAccountFlags
import app.template.patches.reddit.shared.REDDIT_CONTENT_FILTER_HELPER
import app.template.patches.reddit.shared.injectListingChildrenFilter
import app.template.patches.shared.Constants.REDDIT_COMPATIBILITY
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import java.util.Base64

/**
 * Content filters — Reddit (com.reddit.frontpage)
 *
 * Two behaviours in one opt-in patch:
 *
 *   1. USER FILTERS — patch-time keyword, community, author and flair rules are
 *      applied to `Listing.children` (the same deserialization choke point used
 *      by "Remove ads").  The extension reads title/body/selftext/flair/author/
 *      subreddit reflectively, so obfuscated model names do not break the
 *      build.  Flair rules may be scoped with `community::flair`.  Comments can
 *      optionally be filtered too.
 *   2. BUILT-IN GATES — locally neutralises Reddit's over-18 / NSFW / spoiler /
 *      quarantine gating: known account preference booleans are forced at the
 *      AccountJsonAdapter.fromJson choke point, and the extension clears
 *      gate-shaped boolean fields on returned models (blur, NSFW, spoiler,
 *      quarantine, filtered, etc.).  This is best-effort: R8 may rename those
 *      fields, and content that Reddit never returns (server-side filters,
 *      quarantined communities, age assurance) cannot be restored.
 *
 * Configuration is baked into the helper's `<clinit>` as a Base64 blob at patch
 * time, so the listing hooks need only one register (the children list).  No
 * runtime settings UI is added; repatch to change the rules.
 *
 * Verified statically against the Reddit 2026.42.0 Account adapter / Listing
 * layouts used by the existing Reddit patches.  No APK was available in this
 * workspace for on-device testing.
 */
private const val CONFIG_PLACEHOLDER = "PROTEUS_REDDIT_FILTER_CONFIG_V1"

/** The `const-string` carrying [CONFIG_PLACEHOLDER] in the helper's static initializer. */
private val HelperConfigFingerprint = Fingerprint(
    definingClass = REDDIT_CONTENT_FILTER_HELPER,
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(string(CONFIG_PLACEHOLDER)),
)

private fun encodeField(value: String?): String =
    Base64.getUrlEncoder().encodeToString((value ?: "").toByteArray(Charsets.UTF_8))

@Suppress("unused")
val redditContentFiltersPatch = bytecodePatch(
    name = "Content filters",
    description = "Adds patch-time keyword, community, author and flair filters for feed posts " +
        "(optionally comments), and locally bypasses Reddit's NSFW/spoiler/quarantine gates " +
        "and over-18 account flags. Server-side filtering still applies. Off by default.",
    default = false,
) {
    compatibleWith(REDDIT_COMPATIBILITY)

    extendWith("extensions/extension.mpe")

    val keywords by stringOption(
        key = "redditContentFilterKeywords",
        default = "",
        title = "Keywords to hide",
        description = "Comma-separated words or phrases hidden from titles, bodies and flairs " +
            "(case-insensitive, literal match).",
    )
    val communities by stringOption(
        key = "redditContentFilterCommunities",
        default = "",
        title = "Communities to hide",
        description = "Comma-separated subreddit names, with or without the r/ prefix.",
    )
    val authors by stringOption(
        key = "redditContentFilterAuthors",
        default = "",
        title = "Authors to hide",
        description = "Comma-separated user names, with or without the u/ prefix.",
    )
    val flairs by stringOption(
        key = "redditContentFilterFlairs",
        default = "",
        title = "Flairs to hide",
        description = "Comma-separated flair texts. Use community::flair to scope a rule " +
            "to one community.",
    )
    val matchMode by stringOption(
        key = "redditContentFilterMatchMode",
        default = "any",
        values = mapOf(
            "Any rule" to "any",
            "All configured categories" to "all",
        ),
        title = "Match mode",
        description = "Hide a row when any rule matches, or only when every configured " +
            "category matches.",
    )
    val bypassGates by booleanOption(
        key = "redditContentFilterBypassGates",
        default = true,
        title = "Bypass built-in content gates",
        description = "Force over-18 account flags and clear local NSFW/spoiler/quarantine " +
            "blur gates. Server-side filters still apply.",
    )
    val filterComments by booleanOption(
        key = "redditContentFilterComments",
        default = false,
        title = "Also filter comments",
        description = "Apply the keyword/author filters to comment rows in threads as well " +
            "as feed posts.",
    )

    execute {
        // Base64 keeps arbitrary user text out of the smali string grammar.
        // Five URL-safe fields: keywords|communities|authors|flairs|flags.
        val config = listOf(
            encodeField(keywords),
            encodeField(communities),
            encodeField(authors),
            encodeField(flairs),
            encodeField(
                listOf(
                    "mode=${matchMode ?: "any"}",
                    "bypass=${if (bypassGates ?: true) 1 else 0}",
                    "comments=${if (filterComments ?: false) 1 else 0}",
                ).joinToString(";"),
            ),
        ).joinToString("|")

        val clinit = HelperConfigFingerprint.method
        // instructionMatches belongs to the Fingerprint (it carries the match
        // result), not to the resolved MutableMethod.
        val index = HelperConfigFingerprint.instructionMatches.firstOrNull()?.index
            ?: throw PatchException(
                "Content filters: config placeholder '$CONFIG_PLACEHOLDER' not found in " +
                    "$REDDIT_CONTENT_FILTER_HELPER <clinit>. Rebuild the extension module.",
            )
        val register = clinit.getInstruction<OneRegisterInstruction>(index).registerA
        clinit.replaceInstruction(index, "const-string v$register, \"$config\"")
        println("Content filters: baked ${config.length}-character config into helper <clinit>.")

        injectListingChildrenFilter(
            helperMethod = "filterContent",
            logTag = "Content filters",
        )

        if (bypassGates ?: true) {
            // Account-level content preferences.  Missing keys are skipped by
            // forceAccountFlags; the whole layer is optional so an adapter
            // layout change cannot break the user filters.
            runCatching {
                forceAccountFlags(
                    AccountFromJsonFingerprint.method,
                    AccountJsonAdapterConstructorFingerprint.method,
                    listOf(
                        "over_18" to true,
                        "search_include_over_18" to true,
                        "show_nsfw" to true,
                        "show_mature_content" to true,
                        "show_spoilers" to true,
                        "blur_nsfw" to false,
                        "blur_nsfw_images" to false,
                        "hide_nsfw" to false,
                        "hide_spoilers" to false,
                    ),
                    "Content filters",
                )
            }.onFailure {
                println("Content filters: account content-preference layer skipped: ${it.message}")
            }
        }
    }
}
