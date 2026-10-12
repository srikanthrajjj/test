-- SafeGuard pairing + live-location relay.
-- Run this once in the Supabase SQL editor for the project you create for SafeGuard.
--
-- NOTE ON SECURITY: these policies allow anyone holding the project's anon key
-- to read/write any row, keyed only by a 6-digit pairing code. That is fine for
-- a prototype / internal test, but a 6-digit code is guessable (1,000,000
-- combinations). Before any real deployment, add Supabase Auth and scope rows
-- to the authenticated user instead of relying on the code alone.

create table if not exists pairings (
  code text primary key,
  created_at timestamptz not null default now()
);

create table if not exists locations (
  code text primary key references pairings(code) on delete cascade,
  lat double precision not null,
  lon double precision not null,
  updated_at timestamptz not null default now()
);

create table if not exists alerts (
  id bigserial primary key,
  code text not null references pairings(code) on delete cascade,
  title text not null,
  body text not null,
  lat double precision,
  lon double precision,
  created_at timestamptz not null default now()
);

alter table pairings enable row level security;
alter table locations enable row level security;
alter table alerts enable row level security;

create policy "anon full access" on pairings for all using (true) with check (true);
create policy "anon full access" on locations for all using (true) with check (true);
create policy "anon full access" on alerts for all using (true) with check (true);

-- Housekeeping: drop pairings (and their locations/alerts, via cascade) older than 30 days.
-- Run manually or wire to a Supabase cron job.
-- delete from pairings where created_at < now() - interval '30 days';
