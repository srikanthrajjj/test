-- Nest sync schema (applied to project cqtywrtbqfuxfcsbctsu). Tables are RLS-locked; only the edge function (service role) calls the functions below.
create table if not exists public.couples (id text primary key, code text unique, seq bigint not null default 0, created bigint not null);
create table if not exists public.members (token_hash text primary key, couple text not null references public.couples(id) on delete cascade, member text not null check (member in ('a','b')), created bigint not null, unique (couple, member));
create table if not exists public.rec (couple text not null references public.couples(id) on delete cascade, id text not null, kind text not null, data jsonb not null default '{}'::jsonb, u bigint not null, del boolean not null default false, seq bigint not null, primary key (couple, id));
create index if not exists rec_seq on public.rec (couple, seq);
create table if not exists public.usage (couple text not null references public.couples(id) on delete cascade, day text not null, n int not null default 0, primary key (couple, day));
alter table public.couples enable row level security; alter table public.members enable row level security; alter table public.rec enable row level security; alter table public.usage enable row level security;
-- Functions nest_auth / nest_create / nest_peek / nest_join / nest_sync / nest_rows / nest_meter / nest_delete:
-- see the Supabase project (Database > Functions) — they implement last-write-wins sync with a per-couple sequence number.
