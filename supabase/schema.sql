-- 시동편지 Supabase 스키마. Supabase 대시보드 > SQL Editor에서 한 번 실행한다.
-- 보안 한계(시연용): 가족 코드가 사실상 비밀번호다. anon key는 공개 전제이므로
-- 코드를 아는 사람은 그 가족의 편지를 읽을 수 있다. 정식 출시 전에는 인증을 붙인다.

create table if not exists families (
  code text primary key check (code ~ '^[A-Z2-9]{6}$'),
  created_at timestamptz not null default now()
);

create table if not exists letters (
  id uuid primary key default gen_random_uuid(),
  family_code text not null references families(code) on delete cascade,
  sender_name text not null check (char_length(sender_name) between 1 and 20),
  aspect real not null check (aspect > 0),
  strokes jsonb not null default '[]',
  text text check (text is null or char_length(text) <= 200),
  texts jsonb not null default '[]',  -- 스케치북 위 글상자 [{x,y,text,color,size,t}]
  created_at timestamptz not null default now(),
  opened_at timestamptz
);
create index if not exists letters_family_unopened on letters (family_code, created_at) where opened_at is null;

create table if not exists replies (
  id uuid primary key default gen_random_uuid(),
  letter_id uuid not null references letters(id) on delete cascade,
  preset text,
  text text check (text is null or char_length(text) <= 200),
  created_at timestamptz not null default now()
);

alter table families enable row level security;
alter table letters  enable row level security;
alter table replies  enable row level security;

drop policy if exists families_select on families;
drop policy if exists families_insert on families;
drop policy if exists letters_select on letters;
drop policy if exists letters_insert on letters;
drop policy if exists letters_open on letters;
drop policy if exists replies_select on replies;
drop policy if exists replies_insert on replies;

create policy families_select on families for select to anon using (true);
create policy families_insert on families for insert to anon with check (true);
create policy letters_select  on letters  for select to anon using (true);
create policy letters_insert  on letters  for insert to anon with check (opened_at is null);
create policy letters_open    on letters  for update to anon using (true) with check (true);
create policy replies_select  on replies  for select to anon using (true);
create policy replies_insert  on replies  for insert to anon with check (true);

-- 편지는 opened_at 열만 고칠 수 있다(차량 앱이 '열어봄'을 기록).
revoke update on letters from anon;
grant update (opened_at) on letters to anon;
