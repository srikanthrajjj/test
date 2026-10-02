# Nest sync + agent (Cloudflare Worker + D1)

Keeps both phones in step and powers the Nest agent. Everything runs in **your** Cloudflare account.

```
cd worker && npm install
npx wrangler login
npx wrangler d1 create nest                 # copy the database_id into wrangler.toml
npm run db:init                             # create tables (remote)
npx wrangler secret put ANTHROPIC_API_KEY   # enables the agent (sync works without it)
npx wrangler deploy                         # prints https://nest-sync.<you>.workers.dev
```
Then build the app pointed at it (or paste the URL in the app when creating your Nest):
`NEST_SERVER=https://nest-sync.<you>.workers.dev ./android/build.sh`

## API (all JSON, `Authorization: Bearer <member token>`)
| | |
|---|---|
| `POST /v1/pair/create` `{name,color}` | new Nest -> `{couple, member:'a', token, code}` |
| `POST /v1/pair/join` `{code,name,color}` | partner joins -> `{member:'b', token}` (code works once) |
| `POST /v1/sync` `{since, changes[]}` | push local changes, pull everything newer than `since` |
| `GET /v1/agent/digest` | today's AI brief (cached per day / data change) |
| `POST /v1/agent/chat` `{messages[]}` | agent reply + proposed actions (`add_expense`, `settle_up`) |

Sync model: records are last-write-wins by `u` (client time), deletes are tombstones, a per-couple sequence number drives
incremental pulls. Members are absolute (`a`/`b`) on the wire; each phone maps them to "me/partner". You can only edit your own profile.

Agent: `claude-opus-5-5` (override with the `MODEL` var), Messages API via the official SDK. It gets a server-computed summary
(combined + per-person metrics) and a `query_expenses` tool for exact lookups; `add_expense` / `settle_up` are *proposals* the
app shows as confirm cards - the agent never writes data itself. Capped at 80 calls per couple per day.

## Test
`npm test` runs the Worker locally (workerd + local D1) against a mocked Anthropic API: pairing, two-device sync, LWW,
tombstones, validation, digest caching, tool loop. The real Anthropic call is not exercised by the tests.
