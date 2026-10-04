# Nest backend on Supabase (free tier)

Live project: `cqtywrtbqfuxfcsbctsu` (ap-south-1) · API base: `https://cqtywrtbqfuxfcsbctsu.supabase.co/functions/v1/nest`
(baked into the app as `DEFAULT_SERVER`, so nobody types a URL).

- `functions/nest/index.ts` — the Edge Function (pairing, sync, delete, optional AI agent). `verify_jwt` is off; auth is a random per-member token (stored hashed).
- `migrations/001_nest_schema.sql` — tables (RLS on, no policies: only the function's service role touches them). The SQL functions (`nest_sync` etc.) were applied to the project and verified with assertions (create / peek / join / LWW / tombstones / incremental pull / meter / cascade delete).
- `test.mjs` — router test with an in-memory stand-in for the SQL functions.
- **Agent:** set the secret `ANTHROPIC_API_KEY` on the function (Dashboard → Edge Functions → Secrets). Until then the app's chat says the agent isn't switched on; sync works regardless.
- Free projects pause after ~7 days of no activity; opening the app (or any sync) wakes it.
- Cloudflare alternative: `worker/` (same API).
