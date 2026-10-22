-- =====================================================================================
-- CampusConnect v2: merged, idempotent schema for Supabase (Postgres 15+)
--
-- One migration that contains everything: tables, row-level security (RLS), per-role
-- limits, audit log, storage rules and the server-side functions the app calls.
--
-- The rules live HERE, inside the database. Editing the app or calling the API directly
-- cannot get around them: "You are not allowed to see that."
--
-- Safe to run more than once. Order: types -> tables -> helpers -> triggers -> RPCs ->
-- views -> grants -> RLS policies -> storage.
-- =====================================================================================

-- ---------- 0. Types ----------------------------------------------------------------

do $$ begin create type public.app_role as enum ('student', 'lecturer', 'admin');
exception when duplicate_object then null; end $$;

do $$ begin create type public.account_status as enum ('pending', 'approved', 'rejected', 'suspended');
exception when duplicate_object then null; end $$;

do $$ begin create type public.content_status as enum ('draft', 'pending_review', 'approved', 'rejected');
exception when duplicate_object then null; end $$;

do $$ begin create type public.audience_type as enum ('campus', 'programme', 'course');
exception when duplicate_object then null; end $$;

do $$ begin create type public.announcement_priority as enum ('normal', 'academic', 'urgent');
exception when duplicate_object then null; end $$;

do $$ begin create type public.session_status as enum ('scheduled', 'changed', 'cancelled');
exception when duplicate_object then null; end $$;

do $$ begin create type public.notification_type as enum
  ('announcement', 'timetable_change', 'deadline_alert', 'grade_posted', 'submission_received', 'approval');
exception when duplicate_object then null; end $$;

-- ---------- 1. Reference data -------------------------------------------------------

create table if not exists public.programmes (
  id   uuid primary key default gen_random_uuid(),
  name text not null unique
);

create table if not exists public.courses (
  code  text primary key check (code ~ '^[A-Z]{2,3}[0-9]{3}$'),
  title text not null
);

create table if not exists public.programme_courses (
  programme_id uuid not null references public.programmes (id) on delete cascade,
  course_code  text not null references public.courses (code) on delete cascade,
  primary key (programme_id, course_code)
);

-- The university's own lists. Used to flag sign-ups that do not match. Nobody but the
-- registry (through registry_match) can read them.
create table if not exists public.university_students (student_number text primary key);
create table if not exists public.university_staff    (staff_number   text primary key);

create table if not exists public.app_settings (
  key   text primary key,
  value text not null
);

insert into public.app_settings (key, value) values
  ('email_domain', 'campus.ac.tz'),
  ('require_admin_mfa', 'true')
on conflict (key) do nothing;

-- ---------- 2. People ---------------------------------------------------------------

create table if not exists public.profiles (
  id             uuid primary key references auth.users (id) on delete cascade,
  full_name      text not null check (length(trim(full_name)) >= 3),
  email          text not null unique,
  email_verified boolean not null default false,
  role           public.app_role not null default 'student',
  -- What the person asked for at sign-up. A hint for the registry. It grants nothing.
  requested_role public.app_role not null default 'student'
                   check (requested_role in ('student', 'lecturer')),
  status         public.account_status not null default 'pending',
  student_number text unique check (student_number is null or student_number ~ '^T[0-9]{2}-[0-9]{2}-[0-9]{5}$'),
  staff_number   text unique check (staff_number   is null or staff_number   ~ '^STF-[A-Z]{2,3}-[0-9]{3}$'),
  programme_id   uuid references public.programmes (id),
  department     text,
  cohort         text,
  approved_by    uuid references public.profiles (id),
  approved_at    timestamptz,
  created_at     timestamptz not null default now(),
  check (student_number is not null or staff_number is not null)
);

create table if not exists public.enrollments (
  student_id  uuid not null references public.profiles (id) on delete cascade,
  course_code text not null references public.courses (code) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (student_id, course_code)
);

create table if not exists public.teaching_assignments (
  lecturer_id uuid not null references public.profiles (id) on delete cascade,
  course_code text not null references public.courses (code) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (lecturer_id, course_code)
);

create index if not exists enrollments_course_idx on public.enrollments (course_code);
create index if not exists teaching_course_idx    on public.teaching_assignments (course_code);

-- ---------- 3. Limits by role and membership status ---------------------------------
-- NULL means "no limit". Every (role, status) pair has a row. Anyone who is not approved
-- has zero for everything: pending, rejected and suspended accounts can do nothing.

create table if not exists public.role_limits (
  role                  public.app_role       not null,
  status                public.account_status not null,
  announcements_per_day int,
  urgent_per_day        int,
  materials_per_day     int,
  material_max_bytes    bigint,
  deadlines_per_day     int,
  submissions_per_day   int,
  submission_max_bytes  bigint,
  devices_max           int,
  primary key (role, status)
);

insert into public.role_limits
  (role, status, announcements_per_day, urgent_per_day, materials_per_day, material_max_bytes,
   deadlines_per_day, submissions_per_day, submission_max_bytes, devices_max)
values
  -- approved students: hand in work, nothing else
  ('student',  'approved',  0, 0,  0,        0,  0, 20, 15728640, 3),
  -- approved lecturers: post to own courses, share files, set deadlines
  ('lecturer', 'approved', 30, 2, 20, 52428800, 10,  0,        0, 3),
  -- approved administrators (registry): unlimited content, 5 phones
  ('admin',    'approved', null, null, null, null, null, null, null, 5),
  -- everyone not approved: zero everything. Pending people may keep one phone signed in.
  ('student',  'pending',   0, 0, 0, 0, 0, 0, 0, 1),
  ('lecturer', 'pending',   0, 0, 0, 0, 0, 0, 0, 1),
  ('admin',    'pending',   0, 0, 0, 0, 0, 0, 0, 1),
  ('student',  'rejected',  0, 0, 0, 0, 0, 0, 0, 0),
  ('lecturer', 'rejected',  0, 0, 0, 0, 0, 0, 0, 0),
  ('admin',    'rejected',  0, 0, 0, 0, 0, 0, 0, 0),
  ('student',  'suspended', 0, 0, 0, 0, 0, 0, 0, 0),
  ('lecturer', 'suspended', 0, 0, 0, 0, 0, 0, 0, 0),
  ('admin',    'suspended', 0, 0, 0, 0, 0, 0, 0, 0)
on conflict (role, status) do update set
  announcements_per_day = excluded.announcements_per_day,
  urgent_per_day        = excluded.urgent_per_day,
  materials_per_day     = excluded.materials_per_day,
  material_max_bytes    = excluded.material_max_bytes,
  deadlines_per_day     = excluded.deadlines_per_day,
  submissions_per_day   = excluded.submissions_per_day,
  submission_max_bytes  = excluded.submission_max_bytes,
  devices_max           = excluded.devices_max;

-- ---------- 4. Content --------------------------------------------------------------

create table if not exists public.class_sessions (
  id             uuid primary key default gen_random_uuid(),
  course_code    text not null references public.courses (code),
  kind           text not null default 'lecture' check (kind in ('lecture', 'lab', 'tutorial')),
  lecturer_id    uuid not null references public.profiles (id),
  day_of_week    smallint not null check (day_of_week between 1 and 6),   -- Monday to Saturday
  start_time     time not null,
  end_time       time not null,
  venue          text not null check (length(trim(venue)) > 0),
  original_venue text,
  status         public.session_status not null default 'scheduled',
  change_reason  text,
  approval       public.content_status not null default 'approved',
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  check (end_time > start_time)
);

create table if not exists public.announcements (
  id              uuid primary key default gen_random_uuid(),
  author_id       uuid not null references public.profiles (id),
  title           text not null check (length(trim(title)) between 3 and 140),
  body            text not null check (length(trim(body)) between 1 and 5000),
  audience        public.audience_type not null,
  programme_id    uuid references public.programmes (id),
  course_code     text references public.courses (code),
  priority        public.announcement_priority not null default 'normal',
  approval        public.content_status not null default 'approved',
  attachment_path text,
  attachment_size bigint,
  retracted_at    timestamptz,
  retracted_by    uuid references public.profiles (id),
  reminded_at     timestamptz,
  created_at      timestamptz not null default now(),
  check (
    (audience = 'campus'    and programme_id is null     and course_code is null) or
    (audience = 'programme' and programme_id is not null and course_code is null) or
    (audience = 'course'    and course_code  is not null and programme_id is null)
  )
);

-- Who opened what. A student only ever sees their own rows.
create table if not exists public.announcement_reads (
  announcement_id uuid not null references public.announcements (id) on delete cascade,
  user_id         uuid not null references public.profiles (id) on delete cascade,
  read_at         timestamptz not null default now(),
  primary key (announcement_id, user_id)
);

create table if not exists public.study_materials (
  id           uuid primary key default gen_random_uuid(),
  course_code  text not null references public.courses (code),
  title        text not null check (length(trim(title)) >= 2),
  description  text not null default '',
  uploader_id  uuid not null references public.profiles (id),
  storage_path text not null unique,
  file_name    text not null,
  mime_type    text not null,
  size_bytes   bigint not null check (size_bytes > 0),
  approval     public.content_status not null default 'approved',
  created_at   timestamptz not null default now()
);

create table if not exists public.deadlines (
  id               uuid primary key default gen_random_uuid(),
  -- NULL course_code = an academic-calendar entry from the registry, visible to everyone active
  course_code      text references public.courses (code),
  lecturer_id      uuid not null references public.profiles (id),
  title            text not null check (length(trim(title)) >= 2),
  instructions     text not null default '',
  due_at           timestamptz not null,
  accepted_formats text not null default 'PDF or images (max 15 MB)',
  approval         public.content_status not null default 'approved',
  created_at       timestamptz not null default now()
);

-- "Done" ticks are private. No policy lets a lecturer or administrator read them.
create table if not exists public.deadline_done (
  deadline_id uuid not null references public.deadlines (id) on delete cascade,
  student_id  uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  done_at     timestamptz not null default now(),
  primary key (deadline_id, student_id)
);

create table if not exists public.submissions (
  id           uuid primary key default gen_random_uuid(),
  deadline_id  uuid not null references public.deadlines (id) on delete cascade,
  course_code  text not null references public.courses (code),
  student_id   uuid not null references public.profiles (id) on delete cascade,
  notes        text not null default '',
  storage_path text not null unique,
  file_name    text not null,
  mime_type    text not null,
  size_bytes   bigint not null check (size_bytes > 0),
  status       public.content_status not null default 'pending_review',
  grade        text,
  feedback     text,
  graded_by    uuid references public.profiles (id),
  graded_at    timestamptz,
  submitted_at timestamptz not null default now()
);

create table if not exists public.notifications (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles (id) on delete cascade,
  title      text not null,
  message    text not null,
  type       public.notification_type not null default 'announcement',
  is_urgent  boolean not null default false,
  is_read    boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists notifications_user_idx   on public.notifications (user_id, created_at desc);
create index if not exists announcements_aud_idx    on public.announcements (audience, course_code, programme_id, created_at desc);
create index if not exists announcements_author_idx on public.announcements (author_id, created_at desc);
create index if not exists reads_user_idx           on public.announcement_reads (user_id);
create index if not exists sessions_course_idx      on public.class_sessions (course_code, day_of_week);
create index if not exists materials_course_idx     on public.study_materials (course_code);
create index if not exists deadlines_course_idx     on public.deadlines (course_code, due_at);
create index if not exists submissions_course_idx   on public.submissions (course_code, student_id);

-- Append-only record of everything powerful. Nobody can edit or erase a row.
create table if not exists public.audit_log (
  id         bigint generated always as identity primary key,
  at         timestamptz not null default now(),
  actor_id   uuid,
  actor_name text,
  actor_role text,
  action     text not null,
  details    text not null
);

create table if not exists public.devices (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references public.profiles (id) on delete cascade,
  device_key   text not null,
  label        text not null,
  trusted      boolean not null default false,
  created_at   timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  unique (user_id, device_key)
);

create table if not exists public.data_deletion_requests (
  user_id      uuid primary key references public.profiles (id) on delete cascade,
  requested_at timestamptz not null default now(),
  status       text not null default 'open'
);

-- ---------- 5. Helper functions (used by policies) ----------------------------------
-- SECURITY DEFINER so they can read profiles without recursion. They always answer
-- about auth.uid(), the person making the request, never about a value the caller sends.

create or replace function public.setting_text(p_key text, p_default text)
returns text language sql stable security definer set search_path = public as $$
  select coalesce((select value from public.app_settings where key = p_key), p_default)
$$;

create or replace function public.is_active()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = auth.uid() and status = 'approved')
$$;

create or replace function public.is_student()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = auth.uid() and status = 'approved' and role = 'student')
$$;

create or replace function public.is_lecturer()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = auth.uid() and status = 'approved' and role = 'lecturer')
$$;

-- Administrators must also have completed the second step (aal2) unless the setting is off.
create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = auth.uid() and status = 'approved' and role = 'admin')
     and (public.setting_text('require_admin_mfa', 'true') <> 'true'
          or coalesce(auth.jwt() ->> 'aal', '') = 'aal2')
$$;

create or replace function public.teaches(p_course text)
returns boolean language sql stable security definer set search_path = public as $$
  select public.is_lecturer() and exists (
    select 1 from public.teaching_assignments where lecturer_id = auth.uid() and course_code = p_course)
$$;

create or replace function public.enrolled_in(p_course text)
returns boolean language sql stable security definer set search_path = public as $$
  select public.is_student() and exists (
    select 1 from public.enrollments where student_id = auth.uid() and course_code = p_course)
$$;

create or replace function public.in_programme(p_programme uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public.is_student() and exists (
    select 1 from public.profiles where id = auth.uid() and programme_id = p_programme)
$$;

create or replace function public.teaches_programme(p_programme uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public.is_lecturer() and exists (
    select 1 from public.teaching_assignments t
    join public.programme_courses pc on pc.course_code = t.course_code
    where t.lecturer_id = auth.uid() and pc.programme_id = p_programme)
$$;

-- True only inside our own SECURITY DEFINER functions (they set it for the transaction).
create or replace function public.is_internal()
returns boolean language sql stable as $$
  select coalesce(current_setting('app.internal', true), '') = '1'
$$;

-- Start of "today" in Tanzania, so daily limits reset at local midnight.
create or replace function public.today_start()
returns timestamptz language sql stable as $$
  select date_trunc('day', now() at time zone 'Africa/Dar_es_Salaam') at time zone 'Africa/Dar_es_Salaam'
$$;

-- The limit for one kind of action for one person. NULL = unlimited. No profile = 0.
create or replace function public.limit_for(p_user uuid, p_kind text)
returns bigint language plpgsql stable security definer set search_path = public as $$
declare l public.role_limits%rowtype;
begin
  select rl.* into l
  from public.profiles p
  join public.role_limits rl on rl.role = p.role and rl.status = p.status
  where p.id = p_user;
  if not found then return 0; end if;
  return case p_kind
    when 'announcements_per_day' then l.announcements_per_day
    when 'urgent_per_day'        then l.urgent_per_day
    when 'materials_per_day'     then l.materials_per_day
    when 'material_max_bytes'    then l.material_max_bytes
    when 'deadlines_per_day'     then l.deadlines_per_day
    when 'submissions_per_day'   then l.submissions_per_day
    when 'submission_max_bytes'  then l.submission_max_bytes
    when 'devices_max'           then l.devices_max
    else 0
  end;
end $$;

create or replace function public.check_limit(p_user uuid, p_kind text, p_used bigint)
returns void language plpgsql stable security definer set search_path = public as $$
declare lim bigint := public.limit_for(p_user, p_kind);
begin
  if lim is not null and p_used >= lim then
    raise exception 'limit_reached: % (allowed %)', p_kind, lim using errcode = 'P0001', hint = p_kind;
  end if;
end $$;

create or replace function public.check_size(p_user uuid, p_kind text, p_bytes bigint)
returns void language plpgsql stable security definer set search_path = public as $$
declare lim bigint := public.limit_for(p_user, p_kind);
begin
  if lim is not null and p_bytes > lim then
    raise exception 'file_too_large: % (allowed % bytes)', p_kind, lim using errcode = 'P0001', hint = p_kind;
  end if;
end $$;

-- ---------- 6. Audit log ------------------------------------------------------------

create or replace function public.log_audit(p_action text, p_details text)
returns void language plpgsql security definer set search_path = public as $$
declare p public.profiles%rowtype;
begin
  select * into p from public.profiles where id = auth.uid();
  insert into public.audit_log (actor_id, actor_name, actor_role, action, details)
  values (auth.uid(), coalesce(p.full_name, 'system'), coalesce(p.role::text, 'system'), p_action, p_details);
end $$;

create or replace function public.audit_log_is_append_only()
returns trigger language plpgsql as $$
begin
  raise exception 'audit_log_is_append_only' using errcode = 'P0001';
end $$;

drop trigger if exists audit_no_change on public.audit_log;
create trigger audit_no_change before update or delete on public.audit_log
  for each row execute function public.audit_log_is_append_only();

drop trigger if exists audit_no_truncate on public.audit_log;
create trigger audit_no_truncate before truncate on public.audit_log
  for each statement execute function public.audit_log_is_append_only();

-- ---------- 7. Notifications --------------------------------------------------------

create or replace function public.audience_recipients(p_audience public.audience_type, p_programme uuid, p_course text)
returns setof uuid language sql stable security definer set search_path = public as $$
  select p.id from public.profiles p
  where p.status = 'approved' and (
        p_audience = 'campus'
     or (p_audience = 'programme' and p.role = 'student' and p.programme_id = p_programme)
     or (p_audience = 'course' and p.role = 'student' and exists (
           select 1 from public.enrollments e where e.student_id = p.id and e.course_code = p_course)))
$$;

create or replace function public.notify_users(
  p_users uuid[], p_title text, p_message text, p_type public.notification_type, p_urgent boolean default false)
returns void language sql security definer set search_path = public as $$
  insert into public.notifications (user_id, title, message, type, is_urgent)
  select distinct u, p_title, p_message, p_type, p_urgent from unnest(p_users) as u
$$;

-- ---------- 8. Sign-up: the only door into profiles ---------------------------------

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  meta     jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  v_staff  boolean := coalesce((meta ->> 'is_staff')::boolean, false);
  v_number text := upper(trim(coalesce(meta ->> 'id_number', '')));
  v_prog   uuid;
begin
  -- University email only. Enforced here, so a modified app cannot get around it.
  if split_part(lower(new.email), '@', 2) <> public.setting_text('email_domain', 'campus.ac.tz') then
    raise exception 'university_email_required' using errcode = 'P0001';
  end if;

  if not v_staff then
    select id into v_prog from public.programmes where name = meta ->> 'programme';
  end if;

  -- Whatever the app sends, a new account is a PENDING STUDENT. Role and status are never
  -- taken from user-supplied data. Only an administrator can change them (approve_account).
  insert into public.profiles
    (id, full_name, email, email_verified, role, requested_role, status,
     student_number, staff_number, programme_id, department)
  values
    (new.id, trim(coalesce(meta ->> 'full_name', '')), lower(new.email),
     new.email_confirmed_at is not null, 'student',
     case when v_staff then 'lecturer'::public.app_role else 'student'::public.app_role end,
     'pending',
     case when v_staff then null else v_number end,
     case when v_staff then v_number else null end,
     v_prog,
     case when v_staff then nullif(trim(meta ->> 'department'), '') else null end);
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

create or replace function public.sync_email_verified()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.email_confirmed_at is not null and old.email_confirmed_at is null then
    update public.profiles set email_verified = true where id = new.id;
  end if;
  return new;
end $$;

drop trigger if exists on_auth_user_confirmed on auth.users;
create trigger on_auth_user_confirmed after update of email_confirmed_at on auth.users
  for each row execute function public.sync_email_verified();

-- Defence in depth: even with a direct API call, a person can only change their own
-- name and department. (Column privileges below say the same thing.)
create or replace function public.protect_profile_columns()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is not null and not public.is_internal() and not public.is_admin() then
    if new.id is distinct from old.id
       or new.email is distinct from old.email
       or new.email_verified is distinct from old.email_verified
       or new.role is distinct from old.role
       or new.requested_role is distinct from old.requested_role
       or new.status is distinct from old.status
       or new.student_number is distinct from old.student_number
       or new.staff_number is distinct from old.staff_number
       or new.programme_id is distinct from old.programme_id
       or new.cohort is distinct from old.cohort
       or new.approved_by is distinct from old.approved_by
       or new.approved_at is distinct from old.approved_at then
      raise exception 'not_allowed: only the registry can change this' using errcode = '42501';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists profiles_protect on public.profiles;
create trigger profiles_protect before update on public.profiles
  for each row execute function public.protect_profile_columns();

create or replace function public.audit_profile_change()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.role is distinct from old.role then
    perform public.log_audit('ROLE_CHANGED', format('%s: %s -> %s', new.full_name, old.role, new.role));
  end if;
  if new.status is distinct from old.status then
    perform public.log_audit('ACCOUNT_' || upper(new.status::text), format('%s (%s)', new.full_name, new.email));
  end if;
  return new;
end $$;

drop trigger if exists profiles_audit on public.profiles;
create trigger profiles_audit after update on public.profiles
  for each row execute function public.audit_profile_change();

-- ---------- 9. Announcements: rules, limits, delivery -------------------------------

create or replace function public.announcements_before_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_role public.app_role;
begin
  select role into v_role from public.profiles where id = new.author_id;

  perform public.check_limit(new.author_id, 'announcements_per_day',
    (select count(*) from public.announcements where author_id = new.author_id and created_at >= public.today_start()));

  if new.priority = 'urgent' then
    -- Urgent stays meaningful: the registry freely, a lecturer only for their own course, and rationed.
    if v_role = 'lecturer' and new.audience <> 'course' then
      raise exception 'urgent_course_only' using errcode = 'P0001';
    end if;
    perform public.check_limit(new.author_id, 'urgent_per_day',
      (select count(*) from public.announcements
        where author_id = new.author_id and priority = 'urgent' and created_at >= public.today_start()));
  end if;
  return new;
end $$;

drop trigger if exists announcements_bi on public.announcements;
create trigger announcements_bi before insert on public.announcements
  for each row execute function public.announcements_before_insert();

create or replace function public.announcements_before_update()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is not null and not public.is_internal() then
    if new.author_id is distinct from old.author_id
       or new.audience is distinct from old.audience
       or new.programme_id is distinct from old.programme_id
       or new.course_code is distinct from old.course_code
       or new.created_at is distinct from old.created_at
       or new.retracted_at is distinct from old.retracted_at
       or new.retracted_by is distinct from old.retracted_by
       or new.reminded_at is distinct from old.reminded_at then
      raise exception 'not_allowed: use retract_announcement / remind_unread' using errcode = '42501';
    end if;
    if new.approval is distinct from old.approval and not public.is_admin() then
      raise exception 'not_allowed: only the registry changes approval' using errcode = '42501';
    end if;
    -- Editing may not be used to slip past the Urgent rules.
    if new.priority = 'urgent' and old.priority <> 'urgent' and not public.is_admin() then
      raise exception 'urgent_requires_registry' using errcode = 'P0001';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists announcements_bu on public.announcements;
create trigger announcements_bu before update on public.announcements
  for each row execute function public.announcements_before_update();

create or replace function public.announcements_after_insert()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  perform public.log_audit('ANNOUNCEMENT_POSTED',
    format('%s -> %s (%s)', new.title, new.audience, new.priority));
  if new.approval = 'approved' then
    perform public.notify_users(
      array(select r from public.audience_recipients(new.audience, new.programme_id, new.course_code) r
            where r <> new.author_id),
      case when new.priority = 'urgent' then 'Urgent: ' || new.title else new.title end,
      'A new notice was published for you.', 'announcement', new.priority = 'urgent');
  end if;
  return new;
end $$;

drop trigger if exists announcements_ai on public.announcements;
create trigger announcements_ai after insert on public.announcements
  for each row execute function public.announcements_after_insert();

create or replace function public.announcements_after_change()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'DELETE' then
    perform public.log_audit('ANNOUNCEMENT_DELETED', old.title);
    return old;
  end if;
  if new.title is distinct from old.title or new.body is distinct from old.body then
    perform public.log_audit('ANNOUNCEMENT_EDITED', new.title);
  end if;
  return new;
end $$;

drop trigger if exists announcements_au on public.announcements;
create trigger announcements_au after update on public.announcements
  for each row execute function public.announcements_after_change();
drop trigger if exists announcements_ad on public.announcements;
create trigger announcements_ad after delete on public.announcements
  for each row execute function public.announcements_after_change();

-- ---------- 10. Timetable -----------------------------------------------------------

create or replace function public.sessions_before_write()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_clash record;
begin
  if tg_op = 'UPDATE' and auth.uid() is not null and not public.is_internal() and not public.is_admin() then
    if new.course_code is distinct from old.course_code or new.lecturer_id is distinct from old.lecturer_id
       or new.approval is distinct from old.approval then
      raise exception 'not_allowed' using errcode = '42501';
    end if;
  end if;

  if tg_op = 'UPDATE' then
    -- A room change or cancellation must say why. The reason is shown to students.
    if ((new.venue is distinct from old.venue and new.venue is distinct from old.original_venue)
        or (new.status = 'cancelled' and old.status <> 'cancelled'))
       and length(trim(coalesce(new.change_reason, ''))) = 0 then
      raise exception 'reason_required' using errcode = 'P0001';
    end if;
    if new.venue is distinct from old.venue and old.original_venue is null then
      new.original_venue := old.venue;
    end if;
    if new.venue is distinct from old.venue and new.status = 'scheduled' then
      new.status := 'changed';
    end if;
    new.updated_at := now();
  end if;

  -- Warn before saving: no two live sessions in one room at overlapping times.
  if new.status <> 'cancelled' then
    select s.id, s.course_code into v_clash
    from public.class_sessions s
    where s.id is distinct from new.id
      and s.status <> 'cancelled'
      and s.day_of_week = new.day_of_week
      and lower(s.venue) = lower(new.venue)
      and s.start_time < new.end_time and new.start_time < s.end_time
    limit 1;
    if found then
      raise exception 'venue_busy: % is busy at that time (%)', new.venue, v_clash.course_code using errcode = 'P0001';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists sessions_bw on public.class_sessions;
create trigger sessions_bw before insert or update on public.class_sessions
  for each row execute function public.sessions_before_write();

create or replace function public.sessions_after_change()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'DELETE' then
    perform public.log_audit('SESSION_DELETED', format('%s %s', old.course_code, old.venue));
    return old;
  end if;
  if new.venue is distinct from old.venue or new.status is distinct from old.status then
    perform public.log_audit(
      case when new.status = 'cancelled' then 'SESSION_CANCELLED' else 'VENUE_CHANGE' end,
      format('%s: %s -> %s. Reason: %s', new.course_code, old.venue, new.venue, coalesce(new.change_reason, '')));
    perform public.notify_users(
      array(select r from public.audience_recipients('course', null, new.course_code) r),
      case when new.status = 'cancelled' then 'Cancelled: ' || new.course_code else 'Room changed: ' || new.course_code end,
      format('%s, %s. Reason: %s', new.venue, to_char(new.start_time, 'HH24:MI'), coalesce(new.change_reason, '')),
      'timetable_change', false);
  end if;
  return new;
end $$;

drop trigger if exists sessions_au on public.class_sessions;
create trigger sessions_au after update on public.class_sessions
  for each row execute function public.sessions_after_change();
drop trigger if exists sessions_ad on public.class_sessions;
create trigger sessions_ad after delete on public.class_sessions
  for each row execute function public.sessions_after_change();

-- ---------- 11. Materials, deadlines, submissions: limits and delivery ---------------

create or replace function public.materials_before_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_actual bigint;
begin
  perform public.check_limit(new.uploader_id, 'materials_per_day',
    (select count(*) from public.study_materials where uploader_id = new.uploader_id and created_at >= public.today_start()));

  -- Trust the size storage recorded, not the number the app claims.
  select (metadata ->> 'size')::bigint into v_actual
  from storage.objects where bucket_id = 'materials' and name = new.storage_path;
  if v_actual is null then
    raise exception 'file_not_uploaded' using errcode = 'P0001';
  end if;
  new.size_bytes := v_actual;
  perform public.check_size(new.uploader_id, 'material_max_bytes', v_actual);
  return new;
end $$;

drop trigger if exists materials_bi on public.study_materials;
create trigger materials_bi before insert on public.study_materials
  for each row execute function public.materials_before_insert();

create or replace function public.materials_before_update()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is not null and not public.is_internal() then
    if new.uploader_id is distinct from old.uploader_id or new.course_code is distinct from old.course_code
       or new.storage_path is distinct from old.storage_path or new.size_bytes is distinct from old.size_bytes
       or new.mime_type is distinct from old.mime_type then
      raise exception 'not_allowed' using errcode = '42501';
    end if;
    if new.approval is distinct from old.approval and not public.is_admin() then
      raise exception 'not_allowed: only the registry changes approval' using errcode = '42501';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists materials_bu on public.study_materials;
create trigger materials_bu before update on public.study_materials
  for each row execute function public.materials_before_update();

create or replace function public.materials_after_change()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'DELETE' then
    perform public.log_audit('MATERIAL_DELETED', format('%s (%s)', old.title, old.file_name));
    return old;
  end if;
  perform public.log_audit('MATERIAL_UPLOAD', format('%s for %s (%s)', new.title, new.course_code, new.file_name));
  perform public.notify_users(
    array(select r from public.audience_recipients('course', null, new.course_code) r where r <> new.uploader_id),
    'New material: ' || new.course_code, new.title, 'announcement', false);
  return new;
end $$;

drop trigger if exists materials_ai on public.study_materials;
create trigger materials_ai after insert on public.study_materials
  for each row execute function public.materials_after_change();
drop trigger if exists materials_ad on public.study_materials;
create trigger materials_ad after delete on public.study_materials
  for each row execute function public.materials_after_change();

create or replace function public.deadlines_before_write()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    perform public.check_limit(new.lecturer_id, 'deadlines_per_day',
      (select count(*) from public.deadlines where lecturer_id = new.lecturer_id and created_at >= public.today_start()));
  elsif auth.uid() is not null and not public.is_internal() then
    if new.lecturer_id is distinct from old.lecturer_id or new.course_code is distinct from old.course_code
       or new.created_at is distinct from old.created_at then
      raise exception 'not_allowed' using errcode = '42501';
    end if;
    if new.approval is distinct from old.approval and not public.is_admin() then
      raise exception 'not_allowed: only the registry changes approval' using errcode = '42501';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists deadlines_bw on public.deadlines;
create trigger deadlines_bw before insert or update on public.deadlines
  for each row execute function public.deadlines_before_write();

create or replace function public.deadlines_after_change()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_users uuid[];
begin
  if tg_op = 'DELETE' then
    perform public.log_audit('DEADLINE_DELETED', old.title);
    return old;
  end if;
  if new.course_code is null then
    v_users := array(select id from public.profiles where status = 'approved' and role = 'student');
  else
    v_users := array(select r from public.audience_recipients('course', null, new.course_code) r);
  end if;
  if tg_op = 'INSERT' then
    perform public.log_audit('DEADLINE_CREATED', format('%s due %s', new.title, new.due_at));
    perform public.notify_users(v_users, 'New deadline: ' || coalesce(new.course_code, 'Academic calendar'),
      format('%s, due %s', new.title, to_char(new.due_at at time zone 'Africa/Dar_es_Salaam', 'DD Mon HH24:MI')),
      'deadline_alert', false);
  elsif new.due_at is distinct from old.due_at then
    perform public.log_audit('DEADLINE_EDITED', format('%s moved to %s', new.title, new.due_at));
    perform public.notify_users(v_users, 'Deadline moved: ' || coalesce(new.course_code, 'Academic calendar'),
      format('%s is now due %s', new.title, to_char(new.due_at at time zone 'Africa/Dar_es_Salaam', 'DD Mon HH24:MI')),
      'deadline_alert', false);
  end if;
  return new;
end $$;

drop trigger if exists deadlines_ai on public.deadlines;
create trigger deadlines_ai after insert or update on public.deadlines
  for each row execute function public.deadlines_after_change();
drop trigger if exists deadlines_ad on public.deadlines;
create trigger deadlines_ad after delete on public.deadlines
  for each row execute function public.deadlines_after_change();

create or replace function public.submissions_before_write()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_actual bigint;
begin
  if tg_op = 'INSERT' then
    perform public.check_limit(new.student_id, 'submissions_per_day',
      (select count(*) from public.submissions where student_id = new.student_id and submitted_at >= public.today_start()));
    select (metadata ->> 'size')::bigint into v_actual
    from storage.objects where bucket_id = 'submissions' and name = new.storage_path;
    if v_actual is null then
      raise exception 'file_not_uploaded' using errcode = 'P0001';
    end if;
    new.size_bytes := v_actual;
    perform public.check_size(new.student_id, 'submission_max_bytes', v_actual);
    new.status := 'pending_review';
    new.grade := null; new.feedback := null; new.graded_by := null; new.graded_at := null;
  elsif auth.uid() is not null and not public.is_internal() then
    -- Markers may only set grade, feedback and status.
    if new.student_id is distinct from old.student_id or new.deadline_id is distinct from old.deadline_id
       or new.course_code is distinct from old.course_code or new.storage_path is distinct from old.storage_path
       or new.size_bytes is distinct from old.size_bytes or new.notes is distinct from old.notes then
      raise exception 'not_allowed' using errcode = '42501';
    end if;
    if new.grade is distinct from old.grade then
      new.graded_by := auth.uid();
      new.graded_at := now();
      new.status := 'approved';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists submissions_bw on public.submissions;
create trigger submissions_bw before insert or update on public.submissions
  for each row execute function public.submissions_before_write();

create or replace function public.submissions_after_change()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    perform public.log_audit('WORK_SUBMITTED', format('%s for %s', new.file_name, new.course_code));
    perform public.notify_users(
      array(select lecturer_id from public.teaching_assignments where course_code = new.course_code),
      'Work submitted: ' || new.course_code, new.file_name, 'submission_received', false);
  elsif new.grade is distinct from old.grade then
    perform public.log_audit('WORK_GRADED', format('submission %s graded %s', new.id, new.grade));
    perform public.notify_users(array[new.student_id], 'Assignment graded: ' || new.course_code,
      'Your grade is ' || new.grade, 'grade_posted', false);
  end if;
  return new;
end $$;

drop trigger if exists submissions_ai on public.submissions;
create trigger submissions_ai after insert or update on public.submissions
  for each row execute function public.submissions_after_change();

-- ---------- 12. Functions the app calls ---------------------------------------------

-- Registry: approve a waiting account and choose its role. Administrator accounts are never
-- created here. Students are enrolled in their programme's courses; lecturers get courses.
create or replace function public.approve_account(
  p_user uuid, p_role public.app_role, p_programme uuid default null, p_courses text[] default '{}')
returns void language plpgsql security definer set search_path = public as $$
declare v_user public.profiles%rowtype;
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  if p_role not in ('student', 'lecturer') then raise exception 'cannot_grant_admin_here' using errcode = 'P0001'; end if;

  select * into v_user from public.profiles where id = p_user for update;
  if not found or v_user.status <> 'pending' then raise exception 'not_pending' using errcode = 'P0001'; end if;
  if not v_user.email_verified then raise exception 'email_not_verified' using errcode = 'P0001'; end if;

  perform set_config('app.internal', '1', true);
  update public.profiles set
    role = p_role, status = 'approved', approved_by = auth.uid(), approved_at = now(),
    programme_id = case when p_role = 'student' then coalesce(p_programme, programme_id) else programme_id end,
    cohort = coalesce(cohort, case when p_role = 'student' then 'Year 1 - Semester 1' else 'Lecturer' end)
  where id = p_user;

  if p_role = 'student' then
    insert into public.enrollments (student_id, course_code)
    select p_user, pc.course_code from public.programme_courses pc
    where pc.programme_id = coalesce(p_programme, v_user.programme_id)
    on conflict do nothing;
  else
    insert into public.teaching_assignments (lecturer_id, course_code)
    select p_user, c from unnest(p_courses) c on conflict do nothing;
  end if;
  perform set_config('app.internal', '', true);

  perform public.log_audit('ACCOUNT_APPROVED', format('%s as %s', v_user.full_name, p_role));
  perform public.notify_users(array[p_user],
    'Karibu, ' || split_part(v_user.full_name, ' ', 1) || '. You are in.',
    'The Academic Registry approved your account.', 'approval', false);
end $$;

create or replace function public.reject_account(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  perform set_config('app.internal', '1', true);
  update public.profiles set status = 'rejected' where id = p_user and status = 'pending';
  perform set_config('app.internal', '', true);
end $$;

-- Switch someone off at once. Every policy checks status on every request, so an existing
-- session loses access immediately, not at the next sign-in.
create or replace function public.suspend_account(p_user uuid, p_reason text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  if p_user = auth.uid() then raise exception 'cannot_suspend_self' using errcode = 'P0001'; end if;
  perform set_config('app.internal', '1', true);
  update public.profiles set status = 'suspended' where id = p_user and status in ('approved', 'pending');
  delete from public.devices where user_id = p_user;
  perform set_config('app.internal', '', true);
  perform public.log_audit('ACCOUNT_SUSPEND_REASON', coalesce(p_reason, ''));
end $$;

create or replace function public.set_teaching_assignments(p_lecturer uuid, p_courses text[])
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  if not exists (select 1 from public.profiles where id = p_lecturer and role = 'lecturer') then
    raise exception 'not_a_lecturer' using errcode = 'P0001';
  end if;
  delete from public.teaching_assignments where lecturer_id = p_lecturer;
  insert into public.teaching_assignments (lecturer_id, course_code) select p_lecturer, c from unnest(p_courses) c;
  perform public.log_audit('COURSES_ASSIGNED', format('%s: %s', p_lecturer, array_to_string(p_courses, ', ')));
end $$;

-- Does the number on the sign-up match the university's list? (green tick / yellow warning)
create or replace function public.registry_match(p_user uuid)
returns boolean language plpgsql stable security definer set search_path = public as $$
declare p public.profiles%rowtype;
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  select * into p from public.profiles where id = p_user;
  if not found then return false; end if;
  if p.staff_number is not null then
    return exists (select 1 from public.university_staff where staff_number = p.staff_number);
  end if;
  return exists (select 1 from public.university_students where student_number = p.student_number);
end $$;

-- "Delivered to 58 - Opened by 41 - 17 not yet opened": only the sender and the registry.
create or replace function public.announcement_stats(p_id uuid)
returns table (delivered int, opened int)
language plpgsql stable security definer set search_path = public as $$
declare a public.announcements%rowtype;
begin
  select * into a from public.announcements where id = p_id;
  if not found or not (public.is_admin() or a.author_id = auth.uid()) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;
  return query
  select
    (select count(*)::int from public.audience_recipients(a.audience, a.programme_id, a.course_code) r
       join public.profiles p on p.id = r and p.role = 'student'),
    (select count(*)::int from public.announcement_reads ar where ar.announcement_id = p_id);
end $$;

-- One gentle nudge to people who have not opened it.
create or replace function public.remind_unread(p_id uuid)
returns int language plpgsql security definer set search_path = public as $$
declare a public.announcements%rowtype; v_users uuid[];
begin
  select * into a from public.announcements where id = p_id for update;
  if not found or not (public.is_admin() or a.author_id = auth.uid()) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;
  if a.retracted_at is not null or a.reminded_at is not null then return 0; end if;

  v_users := array(
    select r from public.audience_recipients(a.audience, a.programme_id, a.course_code) r
    where r <> a.author_id
      and not exists (select 1 from public.announcement_reads ar where ar.announcement_id = p_id and ar.user_id = r));

  perform set_config('app.internal', '1', true);
  update public.announcements set reminded_at = now() where id = p_id;
  perform set_config('app.internal', '', true);

  perform public.notify_users(v_users, 'Reminder: ' || a.title, 'You have not opened this notice yet.', 'announcement', false);
  perform public.log_audit('ANNOUNCEMENT_REMINDER', format('%s people reminded about %s', coalesce(array_length(v_users, 1), 0), a.title));
  return coalesce(array_length(v_users, 1), 0);
end $$;

-- Posted to the wrong class? It vanishes for everyone and a short Correction goes out.
create or replace function public.retract_announcement(p_id uuid)
returns boolean language plpgsql security definer set search_path = public as $$
declare a public.announcements%rowtype;
begin
  select * into a from public.announcements where id = p_id for update;
  if not found or not (public.is_admin() or a.author_id = auth.uid()) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;
  if a.retracted_at is not null then return false; end if;

  perform set_config('app.internal', '1', true);
  update public.announcements set retracted_at = now(), retracted_by = auth.uid() where id = p_id;
  perform set_config('app.internal', '', true);

  perform public.notify_users(
    array(select r from public.audience_recipients(a.audience, a.programme_id, a.course_code) r where r <> a.author_id),
    'Correction: notice withdrawn', format('"%s" was withdrawn. Please disregard it.', a.title), 'announcement', false);
  perform public.log_audit('ANNOUNCEMENT_RETRACTED', a.title);
  return true;
end $$;

-- Counts only, never who. Individual ticks stay private to each student.
create or replace function public.deadline_done_count(p_id uuid)
returns int language plpgsql stable security definer set search_path = public as $$
declare d public.deadlines%rowtype;
begin
  select * into d from public.deadlines where id = p_id;
  if not found or not (public.is_admin() or d.lecturer_id = auth.uid()) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;
  return (select count(*)::int from public.deadline_done where deadline_id = p_id);
end $$;

-- A phone signs in. Refuses when the account has used up its phones.
create or replace function public.register_device(p_key text, p_label text)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid; v_count bigint;
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = '42501'; end if;
  select id into v_id from public.devices where user_id = auth.uid() and device_key = p_key;
  if found then
    update public.devices set last_seen_at = now() where id = v_id;
    return v_id;
  end if;

  select count(*) into v_count from public.devices where user_id = auth.uid();
  perform public.check_limit(auth.uid(), 'devices_max', v_count);

  insert into public.devices (user_id, device_key, label) values (auth.uid(), p_key, left(p_label, 80))
  returning id into v_id;

  if v_count > 0 then
    perform public.notify_users(array[auth.uid()], 'Signed in on a new device',
      'Not you? Open Profile > Devices and sign it out.', 'approval', false);
  end if;
  return v_id;
end $$;

-- What the person may still do today. Lets the app say "2 Urgent notices left".
create or replace function public.my_limits()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  start_of_day timestamptz := public.today_start();
  lim_urgent bigint := public.limit_for(uid, 'urgent_per_day');
  lim_ann bigint := public.limit_for(uid, 'announcements_per_day');
  used_urgent bigint; used_ann bigint;
begin
  select count(*) into used_urgent from public.announcements
    where author_id = uid and priority = 'urgent' and created_at >= start_of_day;
  select count(*) into used_ann from public.announcements
    where author_id = uid and created_at >= start_of_day;
  return jsonb_build_object(
    'urgent_left',        case when lim_urgent is null then null else greatest(lim_urgent - used_urgent, 0) end,
    'announcements_left', case when lim_ann is null then null else greatest(lim_ann - used_ann, 0) end,
    'material_max_bytes',   public.limit_for(uid, 'material_max_bytes'),
    'submission_max_bytes', public.limit_for(uid, 'submission_max_bytes'),
    'unlimited', public.is_admin());
end $$;

-- Everything stored about the signed-in person, as one document.
create or replace function public.export_my_data()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'not_signed_in' using errcode = '42501'; end if;
  return jsonb_build_object(
    'exported_at', now(),
    'profile', (select to_jsonb(p) - 'approved_by' from public.profiles p where p.id = uid),
    'enrollments', coalesce((select jsonb_agg(course_code) from public.enrollments where student_id = uid), '[]'),
    'teaching', coalesce((select jsonb_agg(course_code) from public.teaching_assignments where lecturer_id = uid), '[]'),
    'notifications', coalesce((select jsonb_agg(to_jsonb(n)) from public.notifications n where n.user_id = uid), '[]'),
    'deadline_ticks', coalesce((select jsonb_agg(deadline_id) from public.deadline_done where student_id = uid), '[]'),
    'announcements_opened', (select count(*) from public.announcement_reads where user_id = uid),
    'submissions', coalesce((select jsonb_agg(to_jsonb(s) - 'storage_path') from public.submissions s where s.student_id = uid), '[]'),
    'devices', coalesce((select jsonb_agg(jsonb_build_object('label', label, 'last_seen_at', last_seen_at)) from public.devices where user_id = uid), '[]'));
end $$;

create or replace function public.request_data_deletion()
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = '42501'; end if;
  insert into public.data_deletion_requests (user_id) values (auth.uid()) on conflict do nothing;
  perform public.log_audit('DATA_DELETION_REQUESTED', 'Person asked for their data to be deleted');
  perform public.notify_users(
    array(select id from public.profiles where role = 'admin' and status = 'approved'),
    'Data deletion request', 'A person asked for their personal data to be deleted.', 'approval', false);
end $$;

-- The very first administrator. Works only while there is none, and only from the SQL editor
-- or the service role. Run it once, then enrol that person in two-step sign-in.
create or replace function public.bootstrap_first_admin(p_email text, p_staff_number text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from public.profiles where role = 'admin') then
    raise exception 'an administrator already exists' using errcode = 'P0001';
  end if;
  perform set_config('app.internal', '1', true);
  update public.profiles set role = 'admin', status = 'approved', staff_number = upper(p_staff_number),
    student_number = null, approved_at = now()
  where lower(email) = lower(p_email) and email_verified;
  if not found then raise exception 'no verified account with that email' using errcode = 'P0001'; end if;
  perform set_config('app.internal', '', true);
end $$;

-- ---------- 13. Safe views -----------------------------------------------------------

-- Everyone active may see the names of lecturers and administrators, and nothing more.
create or replace view public.staff_directory as
  select id, full_name, role from public.profiles
  where status = 'approved' and role in ('lecturer', 'admin') and public.is_active();

-- A lecturer's class list: names and registration numbers for their own courses only.
create or replace view public.class_list as
  select e.course_code, p.id as student_id, p.full_name, p.student_number
  from public.enrollments e
  join public.profiles p on p.id = e.student_id and p.status = 'approved'
  where public.is_admin() or public.teaches(e.course_code);

-- ---------- 14. Privileges -----------------------------------------------------------
-- Start from nothing, then give each table only what it needs. RLS then narrows the rows.

revoke all on all tables    in schema public from anon, authenticated;
revoke all on all functions in schema public from public, anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
alter default privileges in schema public revoke all on tables    from anon, authenticated;
alter default privileges in schema public revoke all on functions from public, anon, authenticated;

-- Policy helpers must be callable by anyone who can be asked a policy question.
grant execute on function
  public.setting_text(text, text), public.is_active(), public.is_student(), public.is_lecturer(),
  public.is_admin(), public.teaches(text), public.enrolled_in(text), public.in_programme(uuid),
  public.teaches_programme(uuid), public.is_internal(), public.today_start()
  to anon, authenticated;

-- Functions the signed-in app calls directly. Trigger and internal helpers are NOT listed.
grant execute on function
  public.approve_account(uuid, public.app_role, uuid, text[]),
  public.reject_account(uuid),
  public.suspend_account(uuid, text),
  public.set_teaching_assignments(uuid, text[]),
  public.registry_match(uuid),
  public.announcement_stats(uuid),
  public.remind_unread(uuid),
  public.retract_announcement(uuid),
  public.deadline_done_count(uuid),
  public.register_device(text, text),
  public.my_limits(),
  public.export_my_data(),
  public.request_data_deletion()
  to authenticated;

grant select on public.programmes, public.courses, public.programme_courses to anon, authenticated;
grant select on public.role_limits to authenticated;
grant select on public.staff_directory, public.class_list to authenticated;

grant select on public.profiles to authenticated;
grant update (full_name, department) on public.profiles to authenticated;

grant select on public.enrollments, public.teaching_assignments to authenticated;

grant select, insert, delete on public.class_sessions to authenticated;
grant update (venue, status, change_reason, start_time, end_time, day_of_week, kind, approval)
  on public.class_sessions to authenticated;

grant select, insert, delete on public.announcements to authenticated;
grant update (title, body, priority, attachment_path, attachment_size, approval)
  on public.announcements to authenticated;

grant select, insert on public.announcement_reads to authenticated;

grant select, insert, delete on public.study_materials to authenticated;
grant update (title, description, approval) on public.study_materials to authenticated;

grant select, insert, delete on public.deadlines to authenticated;
grant update (title, instructions, due_at, accepted_formats, approval) on public.deadlines to authenticated;

grant select, insert, delete on public.deadline_done to authenticated;

grant select, insert, delete on public.submissions to authenticated;
grant update (grade, feedback, status) on public.submissions to authenticated;

grant select, delete on public.notifications to authenticated;
grant update (is_read) on public.notifications to authenticated;

grant select on public.audit_log to authenticated;
grant select, delete on public.devices to authenticated;
grant select on public.data_deletion_requests to authenticated;

-- ---------- 15. Row-level security ---------------------------------------------------

alter table public.programmes              enable row level security;
alter table public.courses                 enable row level security;
alter table public.programme_courses       enable row level security;
alter table public.university_students    enable row level security;
alter table public.university_staff        enable row level security;
alter table public.app_settings            enable row level security;
alter table public.role_limits             enable row level security;
alter table public.profiles                enable row level security;
alter table public.enrollments             enable row level security;
alter table public.teaching_assignments    enable row level security;
alter table public.class_sessions          enable row level security;
alter table public.announcements           enable row level security;
alter table public.announcement_reads      enable row level security;
alter table public.study_materials         enable row level security;
alter table public.deadlines               enable row level security;
alter table public.deadline_done           enable row level security;
alter table public.submissions             enable row level security;
alter table public.notifications           enable row level security;
alter table public.audit_log               enable row level security;
alter table public.devices                 enable row level security;
alter table public.data_deletion_requests  enable row level security;
-- university_students, university_staff and app_settings get no policy: closed to all clients.

do $$
declare r record;
begin
  -- Rebuild policies cleanly so this file can be re-run.
  for r in select schemaname, tablename, policyname from pg_policies
           where schemaname = 'public' and policyname like 'cc\_%' escape '\'
  loop
    execute format('drop policy %I on %I.%I', r.policyname, r.schemaname, r.tablename);
  end loop;
end $$;

-- Reference data: readable by anyone (the sign-up screen needs the programme list).
create policy cc_programmes_read on public.programmes         for select to anon, authenticated using (true);
create policy cc_courses_read    on public.courses            for select to anon, authenticated using (true);
create policy cc_pc_read         on public.programme_courses  for select to anon, authenticated using (true);
create policy cc_limits_read     on public.role_limits        for select to authenticated using (true);

-- Profiles: your own, or all of them for the registry. Never another student's.
create policy cc_profiles_read   on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_admin());
create policy cc_profiles_update on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

create policy cc_enroll_read on public.enrollments for select to authenticated
  using (student_id = auth.uid() or public.is_admin());
create policy cc_teach_read  on public.teaching_assignments for select to authenticated
  using (lecturer_id = auth.uid() or public.is_admin());

-- Timetable: enrolled students, the lecturer who teaches it, the registry.
create policy cc_sessions_read on public.class_sessions for select to authenticated
  using (public.is_admin() or lecturer_id = auth.uid()
         or (public.is_active() and approval = 'approved'
             and (public.enrolled_in(course_code) or public.teaches(course_code))));
create policy cc_sessions_insert on public.class_sessions for insert to authenticated
  with check (public.is_admin() or (lecturer_id = auth.uid() and public.teaches(course_code)));
create policy cc_sessions_update on public.class_sessions for update to authenticated
  using (public.is_admin() or (lecturer_id = auth.uid() and public.teaches(course_code)))
  with check (public.is_admin() or (lecturer_id = auth.uid() and public.teaches(course_code)));
create policy cc_sessions_delete on public.class_sessions for delete to authenticated
  using (public.is_admin() or (lecturer_id = auth.uid() and public.teaches(course_code)));

-- Announcements: published ones for your audiences; authors keep their own, drafts included.
create policy cc_ann_read on public.announcements for select to authenticated
  using (
    public.is_admin()
    or author_id = auth.uid()
    or (public.is_active() and approval = 'approved' and retracted_at is null and (
          audience = 'campus'
       or (audience = 'programme' and (public.in_programme(programme_id) or public.teaches_programme(programme_id)))
       or (audience = 'course'    and (public.enrolled_in(course_code) or public.teaches(course_code)))))
  );
-- Only the registry may speak to the whole campus. A lecturer: own courses and their programmes.
create policy cc_ann_insert on public.announcements for insert to authenticated
  with check (
    author_id = auth.uid()
    and approval in ('draft', 'approved')
    and (public.is_admin()
         or (public.is_lecturer() and (
               (audience = 'course'    and public.teaches(course_code))
            or (audience = 'programme' and public.teaches_programme(programme_id)))))
  );
create policy cc_ann_update on public.announcements for update to authenticated
  using (public.is_admin() or author_id = auth.uid())
  with check (public.is_admin() or author_id = auth.uid());
create policy cc_ann_delete on public.announcements for delete to authenticated
  using (public.is_admin() or author_id = auth.uid());

-- Read receipts: you can see and write only your own, and only for notices you can see.
create policy cc_reads_read on public.announcement_reads for select to authenticated
  using (user_id = auth.uid());
create policy cc_reads_insert on public.announcement_reads for insert to authenticated
  with check (user_id = auth.uid() and public.is_student()
              and exists (select 1 from public.announcements a where a.id = announcement_reads.announcement_id));

-- Study materials.
create policy cc_mat_read on public.study_materials for select to authenticated
  using (public.is_admin() or uploader_id = auth.uid()
         or (public.is_active() and approval = 'approved'
             and (public.enrolled_in(course_code) or public.teaches(course_code))));
create policy cc_mat_insert on public.study_materials for insert to authenticated
  with check (uploader_id = auth.uid() and (public.is_admin() or (public.is_lecturer() and public.teaches(course_code))));
create policy cc_mat_update on public.study_materials for update to authenticated
  using (public.is_admin() or uploader_id = auth.uid())
  with check (public.is_admin() or uploader_id = auth.uid());
create policy cc_mat_delete on public.study_materials for delete to authenticated
  using (public.is_admin() or uploader_id = auth.uid());

-- Deadlines. course_code NULL = registry calendar entry, visible to every active person.
create policy cc_dl_read on public.deadlines for select to authenticated
  using (public.is_admin() or lecturer_id = auth.uid()
         or (public.is_active() and approval = 'approved'
             and (course_code is null or public.enrolled_in(course_code) or public.teaches(course_code))));
create policy cc_dl_insert on public.deadlines for insert to authenticated
  with check (lecturer_id = auth.uid()
              and (public.is_admin() or (public.is_lecturer() and course_code is not null and public.teaches(course_code))));
create policy cc_dl_update on public.deadlines for update to authenticated
  using (public.is_admin() or lecturer_id = auth.uid())
  with check (public.is_admin() or lecturer_id = auth.uid());
create policy cc_dl_delete on public.deadlines for delete to authenticated
  using (public.is_admin() or lecturer_id = auth.uid());

-- Done ticks: strictly the student's own. There is deliberately no lecturer or admin policy.
create policy cc_done_read on public.deadline_done for select to authenticated
  using (student_id = auth.uid() and public.is_student());
create policy cc_done_insert on public.deadline_done for insert to authenticated
  with check (student_id = auth.uid() and public.is_student()
              and exists (select 1 from public.deadlines d where d.id = deadline_done.deadline_id));
create policy cc_done_delete on public.deadline_done for delete to authenticated
  using (student_id = auth.uid() and public.is_student());

-- Submissions: a student sees only their own work; a lecturer, work for courses they teach.
create policy cc_sub_read on public.submissions for select to authenticated
  using (student_id = auth.uid() or public.is_admin()
         or (public.is_lecturer() and public.teaches(course_code)));
create policy cc_sub_insert on public.submissions for insert to authenticated
  with check (student_id = auth.uid() and public.enrolled_in(course_code)
              and exists (select 1 from public.deadlines d
                          where d.id = submissions.deadline_id and d.course_code = submissions.course_code));
create policy cc_sub_update on public.submissions for update to authenticated
  using (public.is_admin() or (public.is_lecturer() and public.teaches(course_code)))
  with check (public.is_admin() or (public.is_lecturer() and public.teaches(course_code)));
create policy cc_sub_delete on public.submissions for delete to authenticated
  using (public.is_admin() or (student_id = auth.uid() and status = 'pending_review'));

-- Notifications: only your own, for everyone, administrators included.
create policy cc_notif_read   on public.notifications for select to authenticated using (user_id = auth.uid());
create policy cc_notif_update on public.notifications for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy cc_notif_delete on public.notifications for delete to authenticated using (user_id = auth.uid());

-- Audit log: administrators read it. Nobody writes it directly, and nobody can change it.
create policy cc_audit_read on public.audit_log for select to authenticated using (public.is_admin());

create policy cc_dev_read   on public.devices for select to authenticated using (user_id = auth.uid());
create policy cc_dev_delete on public.devices for delete to authenticated using (user_id = auth.uid());

create policy cc_del_read on public.data_deletion_requests for select to authenticated
  using (user_id = auth.uid() or public.is_admin());

-- ---------- 16. Storage ---------------------------------------------------------------
-- Folder conventions (the folder decides who may write):
--   materials/<COURSE>/<lecturer uid>/<file>
--   submissions/<student uid>/<deadline id>/<file>
--   announcements/<author uid>/<file>
-- Reading is allowed only when the matching database row is visible to the reader.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types) values
  ('materials', 'materials', false, 52428800, array[
     'application/pdf', 'image/jpeg', 'image/png',
     'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
     'application/vnd.openxmlformats-officedocument.presentationml.presentation']),
  ('submissions', 'submissions', false, 15728640, array[
     'application/pdf', 'image/jpeg', 'image/png',
     'application/vnd.openxmlformats-officedocument.wordprocessingml.document']),
  ('announcements', 'announcements', false, 10485760, array['application/pdf', 'image/jpeg', 'image/png'])
on conflict (id) do update set
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists cc_mat_obj_read   on storage.objects;
drop policy if exists cc_mat_obj_insert on storage.objects;
drop policy if exists cc_mat_obj_delete on storage.objects;
drop policy if exists cc_sub_obj_read   on storage.objects;
drop policy if exists cc_sub_obj_insert on storage.objects;
drop policy if exists cc_sub_obj_delete on storage.objects;
drop policy if exists cc_ann_obj_read   on storage.objects;
drop policy if exists cc_ann_obj_insert on storage.objects;
drop policy if exists cc_ann_obj_delete on storage.objects;

create policy cc_mat_obj_read on storage.objects for select to authenticated
  using (bucket_id = 'materials' and (
           (storage.foldername(name))[2] = auth.uid()::text
        or exists (select 1 from public.study_materials m where m.storage_path = objects.name)));
create policy cc_mat_obj_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'materials'
              and (storage.foldername(name))[2] = auth.uid()::text
              and (public.is_admin() or (public.is_lecturer() and public.teaches((storage.foldername(name))[1]))));
create policy cc_mat_obj_delete on storage.objects for delete to authenticated
  using (bucket_id = 'materials'
         and (public.is_admin() or (storage.foldername(name))[2] = auth.uid()::text));

create policy cc_sub_obj_read on storage.objects for select to authenticated
  using (bucket_id = 'submissions' and (
           (storage.foldername(name))[1] = auth.uid()::text
        or exists (select 1 from public.submissions s where s.storage_path = objects.name)));
create policy cc_sub_obj_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'submissions' and public.is_student()
              and (storage.foldername(name))[1] = auth.uid()::text);
create policy cc_sub_obj_delete on storage.objects for delete to authenticated
  using (bucket_id = 'submissions'
         and (public.is_admin() or (storage.foldername(name))[1] = auth.uid()::text));

create policy cc_ann_obj_read on storage.objects for select to authenticated
  using (bucket_id = 'announcements' and (
           (storage.foldername(name))[1] = auth.uid()::text
        or exists (select 1 from public.announcements a where a.attachment_path = objects.name)));
create policy cc_ann_obj_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'announcements'
              and (public.is_admin() or public.is_lecturer())
              and (storage.foldername(name))[1] = auth.uid()::text);
create policy cc_ann_obj_delete on storage.objects for delete to authenticated
  using (bucket_id = 'announcements'
         and (public.is_admin() or (storage.foldername(name))[1] = auth.uid()::text));
