# Conservative in-memory DNS response cache (1.6.0)

The VPN now has a **128-entry** process-memory LRU cache for simple positive
IPv4 A and IPv6 AAAA DNS answers delivered to UDP clients. It does not
persist query history or send usage analytics anywhere.

To prevent unsafe replay, the cache only accepts a single untruncated,
successful IN-class A/AAAA answer with one standard question and no
additional/authority sections. EDNS, multi-record, DNSSEC, TCP, NXDOMAIN,
SERVFAIL and responses containing extra data are not cached.

The key includes the entire query body (except transaction ID), and cached
answers receive a fresh transaction ID. TTL decreases with elapsed
monotonic time, never exceeding a 60-second cache lifetime. Expired data
is not served. The cache is cleared at VPN startup and whenever resolver
recovery/network handoff is requested. Local blocklist and temporary
allow/block decisions still happen **before** cache lookup.

Because of the strict acceptance criteria, many modern DNS queries using
EDNS or complex answers intentionally bypass the cache. This is an initial
low-risk performance improvement, not a general-purpose recursive resolver.

Remaining QA: real DNS responses with A and AAAA records, changing TTL,
truncated DNS, EDNS, Wi-Fi/cellular handoffs, provider failover and
high-concurrency access. No real-device performance gain is claimed yet.
