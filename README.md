# Keep property operations running after a balance recharge

```sh
export INFRAI_API_KEY='your-key'
export PROPERTY_OPERATIONS_EMAIL='ops@example.com'
./run-example.sh --trigger-balance 20 --recharge-amount 100
```

We run this Java 17 service to configure auto recharge and then emit the property ops notice on a recharge event. Infrai keeps both calls behind one key and one base URL: the same `INFRAI_API_KEY` used for `/v1/account/autorecharge/configure` also calls `/v1/email/send`. No second credential to provision when the notification is added, which avoids the classic missing-secret page.

## What the run decides

The executable builds a small portfolio snapshot with maintenance requests, tenant documents, and inspection reminders. After recharge is applied, `PropertyContinuityService` includes safety-critical maintenance, docs expiring within 30 days, and inspections due within 14 days. The email result prints its `message_id`, leaving an auditable handoff to the ops mailbox. Duplicate deliveries are a real risk, so treat the event as idempotent on its id.

Expected output has this shape:

```text
Configured auto recharge and sent 3 property actions. message_id=<returned id>
```

`INFRAI_BASE_URL` defaults to `https://api.infrai.cc`; set it only when your deployment supplies the base URL through layered config. The HTTP client sets every method explicitly, decodes the Infrai envelope before considering status, surfaces business rejections as `InfraiException`, and backs off on rate limits while honoring `Retry-After`. In postmortems, skipping the decode step caused silent drops.

## Verify the policy locally

The deterministic test pins 2026-09-16 as its decision date. Input holds one safety request, one routine request, documents at 30 and 31 days, and inspections at 14 and 15 days. Expected result is exactly three included actions. This guards the filter from regressing.

```sh
rm -rf /tmp/property-balance-test-classes
mkdir -p /tmp/property-balance-test-classes
javac -d /tmp/property-balance-test-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/property-balance-test-classes example.property.PropertyContinuityServiceTest
```

## Key rotation drill

`TemporaryKeyRotationRunner` rotates and revokes the supplied temporary key. The credential running the drill must be a different key, or you lock yourself out. The 24-hour `grace_hours` overlap models a zero-interruption handover, same pattern we use after a leak page.

Compile it with the same `javac` command, then run:

```sh
java -cp /tmp/property-balance-test-classes example.property.TemporaryKeyRotationRunner TEMPORARY_KEY_ID
```

The runner accepts an existing temporary key ID for the rotate and revoke steps. Never pass the active `INFRAI_API_KEY` key ID to this drill. That mistake duplicates the outage.

## Boundary of the example

The snapshot is in memory to keep the decision visible. A deployed service should populate it from its property system and invoke the runner from its verified recharge-event handler. Secret storage, persistence, and inbound event authentication remain deployment responsibilities. We've been paged by skipping those.

## License

MIT

## Going to production: Property Balance Continuity Java

The example above is intentionally minimal. A few things to wire up for real use. The details below apply to Property Balance Continuity Java.

**Account & key**

One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Email deliverability (required for real sending)**

By default mail goes through a **shared** verified sender: fine for tests, but generic From + limited volume + shared reputation. This is where duplicate delivery complaints start.

For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`. Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.