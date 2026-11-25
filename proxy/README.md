# ClestialKeyboard 9router Thin Proxy

Proxy tipis tanpa dependency eksternal (`Node.js >= 20`) untuk menghubungkan **ClestialKeyboard** ke endpoint OpenAI-compatible milik **9router** tanpa menyimpan API key di aplikasi Android.

## Variabel Lingkungan (Environment Variables)

- `NINE_ROUTER_BASE_URL` — Default: `http://127.0.0.1:20128/v1`
- `NINE_ROUTER_API_KEY` — API key 9router Anda (**wajib** diisi lewat env, jangan di-hardcode)
- `NINE_ROUTER_MODEL` — Nama model atau combo di 9router (misal combo dari 3 akun AGY + 1 akun Codex Plus; default: `agy-codex-combo`)
- `NINE_ROUTER_FALLBACK_MODELS` — Daftar model cadangan dipisah koma (opsional)
- `PROXY_PORT` — Default: `8787`
- `UPSTREAM_TIMEOUT_MS` — Default: `15000`
- `MAX_RETRIES` — Default: `1`
- `RATE_LIMIT_MAX` — Default: `20` permintaan per jendela waktu
- `RATE_LIMIT_WINDOW_MS` — Default: `60000` ms

## Cara Menjalankan

```bash
export NINE_ROUTER_BASE_URL="http://127.0.0.1:20128/v1"
export NINE_ROUTER_API_KEY="kunci_rahasia_anda"
export NINE_ROUTER_MODEL="agy-codex-combo"
node proxy/server.mjs
```

Di emulator Android, isi URL Proxy di **ClestialKeyboard → Tools Keyboard / Kalkulator → URL Proxy AI** dengan `http://10.0.2.2:8787` (atau IP LAN/HTTPS server proxy Anda bila dijalankan di perangkat fisik).

## Menjalankan Uji Proxy

```bash
node proxy/test_proxy.mjs
```
