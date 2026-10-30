# Pinlock

A small Android app that pins its TLS certificate, and the API it talks to. It is
a target you own: something to practise certificate pinning bypass on without
touching anyone else's app.

Everything in it is made up. The accounts, orders, addresses and card numbers
are fake, and the API holds nothing else.

From [Clickswave](https://clickswave.org). We use it in the Crossfyre mobile
videos.

## What it pins

Two independent layers, the way real apps tend to do it:

1. **Android's network security config**
   ([network_security_config.xml](android/app/src/main/res/xml/network_security_config.xml)).
   The platform checks the pins during the handshake, before the app sees a byte.
2. **OkHttp's `CertificatePinner`** ([Pins.kt](android/app/src/main/java/org/clickswave/pinlock/Pins.kt)).
   OkHttp checks the chain again after the platform has accepted it.

Either one alone is enough to refuse a proxy. Remove the pin-set from the XML
and OkHttp still says no.

The pins are SHA-256 hashes of the public keys of the root CAs Cloudflare issues
edge certificates from:

| Root | Pin |
|---|---|
| GTS Root R1 | `hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=` |
| GTS Root R4 | `mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=` |
| ISRG Root X1 | `C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=` |
| ISRG Root X2 | `diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=` |
| SSL.com TLS ECC Root CA 2022 | `G/ANXI8TwJTdF+AFBM8IiIUPEv0Gf6H5LA/b9guG4yE=` |
| SSL.com TLS RSA Root CA 2022 | `K89VOmb1cJAN3TK6bf4ezAbJGC1mLcG2Dh97dnwr3VQ=` |

Pinning roots instead of the leaf certificate keeps the app working when
Cloudflare renews or switches the certificate, and still refuses every proxy CA.

The app also trusts user-installed CAs, which most apps don't. That is
deliberate. Without it, a proxy CA you install on the phone is rejected by the
trust store before the pin is ever checked. With it, the proxy gets past the
trust store and the pin is visibly what stops it.

What you should see:

| Setup | Result |
|---|---|
| No proxy | Signs in and lists two orders |
| Proxy, its CA not installed on the phone | Blocked: certificate not trusted |
| Proxy, its CA installed as a user certificate | Blocked by certificate pinning |

## Demo accounts

| Email | Password |
|---|---|
| `demo@example.com` | `correct-horse` (prefilled in the app) |
| `jordan@example.com` | `battery-staple` |

## API

Base URL: `https://pinlock.clickswave.org`

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/v1/config` | none | Feature flags. The app fetches this at launch and never shows it |
| POST | `/v1/login` | none | `{ token, user }` for `{ email, password }` |
| GET | `/v1/me` | Bearer | The signed-in user |
| GET | `/v1/orders` | Bearer | The signed-in user's orders |
| GET | `/v1/orders/{id}` | Bearer | Any order, whoever owns it |

`GET /v1/orders/{id}` does not check ownership, on purpose. Sign in as one user
and read the other's order: that is the IDOR exercise. Nothing else in the API
is meant to be vulnerable.

## Building

Android, with JDK 17 and the Android SDK (compile SDK 36):

```sh
cd android
./gradlew assembleRelease
```

The APK lands in `android/app/build/outputs/apk/release/`. Without a release key
it is signed with your debug key, which is fine for a demo. To point the app at
another host under `clickswave.org`, pass `-Ppinlock.host=<host>`. For a host
outside that domain, edit the `<domain>` in the network security config too.

The API is a Cloudflare Worker with no dependencies:

```sh
cd api
npm test            # node --test
npx wrangler dev    # local server on :8787
```

## Use it on this, not on other people's apps

Bypassing pinning on an app you don't own or aren't authorised to test can
breach its terms and, depending on where you are, the law. That is the reason
this app exists.

## Licence

MIT. See [LICENSE](LICENSE).
