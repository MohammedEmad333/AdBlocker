# Optional DNS over HTTPS (DoH)

This option is **off by default**. In Settings, enable **Encrypt outgoing DNS
using Cloudflare DoH**, then stop and restart VPN protection. Enabling it
sends the DNS requests handled by AdBlocker to the Cloudflare RFC 8484
endpoint `https://cloudflare-dns.com/dns-query` using HTTPS POST with
`application/dns-message`.

Connections explicitly use Android's underlying (non-VPN) Network. The
response must be HTTP 200, use the expected media type, respect the bounded
DNS response size, and match the transaction ID and DNS question. Redirects
and compressed response bodies are rejected. On timeout or error, the DNS
request fails rather than silently falling back to an unencrypted resolver.
Startup fails if the DoH provider cannot be reached.

The existing local blocking policy still applies *before* the query leaves
the app. This mode does not decrypt or filter DoH/DoT traffic sent directly
by other apps, and is not a full-system encryption tunnel. The chosen DoH
operator sees queried domain names and the connection IP; review its
privacy policy before enabling.

## Release validation still required

- TLS certificate failure, DNS captive portal, offline startup and retries.
- Wi-Fi/cellular handoffs, airplane mode, VPN revocation, cancellation.
- DNS over UDP and TCP from client apps, large/truncated responses, IPv6.
- HTTPS provider unavailability, DNS errors, no plaintext fallback, UI mode
  change and restart semantics on supported Android devices.

Automated JVM tests cover DNS reply matching. Emulator smoke is not a
substitute for real DoH network interoperability and VPN tests.

## Selecting a trusted encrypted DNS provider (1.6.0)

Choose Cloudflare (default), Google Public DNS, or Quad9 in Settings.
Selection is stored locally; stop/start the VPN to apply it. The currently
running session uses a snapshot of the selected provider, including after
network handoffs. The app accepts only these fixed HTTPS endpoints and does
not accept custom provider URLs.

A failing provider returns DNS failure and does **not** silently downgrade
to plaintext DNS or automatically send queries to another operator. Users
may manually switch providers and restart protection. Provider changes can
affect DNS privacy policies and filtering semantics.

Physical testing remains mandatory for all three providers on real Wi-Fi,
mobile, TLS failure, captive portal and Android 8-16 configurations.
