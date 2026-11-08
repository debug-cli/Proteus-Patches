# WhatsApp — patch status

The WhatsApp catalog targets `com.whatsapp` as an XAPK, with configured versions
2.26.43.76, 2.26.43.74 and 2.26.41.77. These targets and all WhatsApp entries
are **unverified on an actual APK or device**. Back up chats and test on a
non-primary device before considering any patched build.

## Heuristic entries

The following 10 entries are off by default. Each enables a shared, app-agnostic
heuristic; none has been verified against WhatsApp bytecode. A successful patch
run can still match zero call sites and does not guarantee the desired behavior.

- Block analytics & tracking SDKs
- Block ads & ad SDKs
- Prefer external browser over Custom Tabs
- Allow screenshots & screen recording
- Remove dangerous permissions
- Disable video autoplay (Status)
- Strip logging (android.util.Log)
- Block screenshots & screen recording
- Empty telephony identifiers
- Limit usage data (diagnostics & analytics; same analytics heuristic as above)

**Do not select both screen-capture entries:** they have opposite goals. Blocking
capture is best effort, not a confidentiality guarantee. Removing permissions
uses the universal dependency's aggressive defaults and can break calls, media,
contacts or registration. Use the universal permission patch directly when its
options are needed. Emptying phone identifiers may also break registration.

## Unimplemented entries

These seven opt-in entries are **placeholders** and make no changes. Selecting
one deliberately fails with an explanatory error instead of silently reporting
success. App-specific APK analysis and device testing are required:

- Keep deleted messages
- Read receipts (no blue ticks)
- Typing indicator (hide when typing)
- View once media (allow reopen & save)
- App icon (classic or premium style)
- Mods settings entry
- Mods settings resources

No placeholder should be relied on for privacy or security. The app-scoped
entries are not WhatsApp Business support, and they do not alter server-side
features or provide access to other users' encrypted messages.
