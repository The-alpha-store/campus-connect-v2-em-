-- pgTAP tests: who may see and do what, enforced inside the database.
-- Run with:  supabase db reset && supabase test db
-- Needs the sample data from supabase/seed.sql.

begin;
select * from no_plan();

create schema if not exists tests;
create or replace function tests.as_user(p uuid, p_aal text default 'aal1') returns void
language plpgsql as $$
begin
  perform set_config('request.jwt.claims',
    json_build_object('sub', p, 'role', 'authenticated', 'aal', p_aal)::text, true);
  execute 'set local role authenticated';
end $$;

-- Sample people (see seed.sql)
-- asha, kelvin = students (BSc Computer Science)   baraka = pending (BSc Information Systems)
-- mushi teaches CS210 + CS215, kweka teaches IS202  emanuel = administrator

-- ======================================================================================
-- Anonymous visitors
-- ======================================================================================
set local role anon;
select throws_ok($$select * from public.profiles$$, '42501', null, 'anonymous cannot read profiles');
select throws_ok($$select * from public.announcements$$, '42501', null, 'anonymous cannot read announcements');
select isnt_empty($$select * from public.programmes$$, 'anonymous can read the programme list (sign-up needs it)');

-- ======================================================================================
-- Sign-up door: only university emails, and never a role from the app
-- ======================================================================================
reset role;
select throws_ok(
  $$insert into auth.users (id, email, raw_user_meta_data)
    values (gen_random_uuid(), 'someone@gmail.com', '{"full_name":"Out Sider","id_number":"T25-03-00001"}')$$,
  'P0001', 'university_required', 'sign-up must name a university');
select throws_ok(
  $$insert into auth.users (id, email, raw_user_meta_data)
    values (gen_random_uuid(), 'someone@gmail.com', '{"full_name":"Out Sider","id_number":"T25-03-00001","university":"NOWHERE"}')$$,
  'P0001', 'university_required', 'an unknown university is refused');

-- Any email provider and any number format are fine.
insert into auth.users (id, email, email_confirmed_at, raw_user_meta_data)
values ('88888888-8888-4888-8888-888888888888', 'amina.said@yahoo.com', now(),
        '{"full_name":"Amina Said","id_number":"ifm/2024/bsc/0042","university":"IFM","programme":"Free text programme"}');
select is((select university_id from public.profiles where id = '88888888-8888-4888-8888-888888888888'),
          (select id from public.universities where code = 'IFM'), 'a gmail-style email and a free-form number are accepted');
select is((select student_number from public.profiles where id = '88888888-8888-4888-8888-888888888888'),
          'ifm/2024/bsc/0042', 'the registration number is kept exactly as typed');
select throws_ok(
  $$insert into auth.users (id, email, raw_user_meta_data)
    values (gen_random_uuid(), 'other@yahoo.com', '{"full_name":"Other Person","id_number":"IFM/2024/BSC/0042","university":"IFM"}')$$,
  '23505', null, 'the same registration number cannot be used twice in one university');
select lives_ok(
  $$insert into auth.users (id, email, raw_user_meta_data)
    values (gen_random_uuid(), 'same.number@yahoo.com', '{"full_name":"Same Number","id_number":"ifm/2024/bsc/0042","university":"UDSM"}')$$,
  'but another university may use the same number');

insert into auth.users (id, email, raw_user_meta_data)
values ('99999999-9999-4999-8999-999999999999', 'eve.hacker@campus.ac.tz',
        '{"full_name":"Eve Hacker","id_number":"T25-03-00002","university":"CUOM","role":"admin","status":"approved","is_admin":true}');
select is((select role::text from public.profiles where id = '99999999-9999-4999-8999-999999999999'),
          'student', 'sign-up metadata cannot choose the role');
select is((select status::text from public.profiles where id = '99999999-9999-4999-8999-999999999999'),
          'pending', 'sign-up metadata cannot choose the status');

-- ======================================================================================
-- A pending account (Baraka): sees only itself
-- ======================================================================================
select tests.as_user('a0000000-0000-4000-8000-000000000003');
select is((select count(*)::int from public.profiles), 1, 'pending person sees only their own profile');
select is_empty($$select * from public.announcements$$, 'pending person sees no announcements');
select is_empty($$select * from public.class_sessions$$, 'pending person sees no timetable');
select is_empty($$select * from public.study_materials$$, 'pending person sees no materials');
select is_empty($$select * from public.deadlines$$, 'pending person sees no deadlines');
select throws_ok($$update public.profiles set role = 'admin' where id = 'a0000000-0000-4000-8000-000000000003'$$,
                 '42501', null, 'a person cannot promote themselves');
select throws_ok($$update public.profiles set status = 'approved' where id = 'a0000000-0000-4000-8000-000000000003'$$,
                 '42501', null, 'a person cannot approve themselves');
reset role;
select is(public.limit_for('a0000000-0000-4000-8000-000000000003', 'submissions_per_day'), 0::bigint,
          'pending accounts have a zero submission limit');

-- ======================================================================================
-- A student (Asha): own courses only, own data only
-- ======================================================================================
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000001');
select is((select count(*)::int from public.class_sessions where course_code = 'CUOM:IS202'), 1,
          'student sees the timetable of a course they are enrolled in');
select is((select count(*)::int from public.profiles where id <> 'a0000000-0000-4000-8000-000000000001'), 0,
          'student sees no other profile');
select is((select count(*)::int from public.announcements where course_code = 'CUOM:CS210'), 1,
          'student sees an announcement for their course');
select is((select count(*)::int from public.announcements where audience = 'campus'), 1,
          'student sees the campus-wide announcement');
select is((select count(*)::int from public.notifications where user_id <> 'a0000000-0000-4000-8000-000000000001'), 0,
          'student sees only their own notifications');
select is_empty($$select * from public.audit_log$$, 'student cannot read the audit log');
select throws_ok($$insert into public.announcements (author_id, title, body, audience)
    values ('a0000000-0000-4000-8000-000000000001', 'Hello all', 'Hi', 'campus')$$,
    'P0001', 'limit_reached: announcements_per_day (allowed 0)', 'student cannot post: their announcement limit is zero');
select throws_ok($$select public.approve_account('a0000000-0000-4000-8000-000000000003', 'student')$$,
                 '42501', null, 'student cannot approve accounts');
select throws_ok($$select * from public.announcement_stats('e0000000-0000-4000-8000-000000000001')$$,
                 '42501', null, 'student cannot read delivery counts');
select throws_ok($$insert into public.study_materials (course_code, title, uploader_id, storage_path, file_name, mime_type, size_bytes)
    values ('CUOM:CS210', 'Notes', 'a0000000-0000-4000-8000-000000000001', 'CUOM:CS210/x/y.pdf', 'y.pdf', 'application/pdf', 10)$$,
    'P0001', 'limit_reached: materials_per_day (allowed 0)', 'student cannot share materials: their upload limit is zero');
select lives_ok($$insert into public.deadline_done (deadline_id) values ('f0000000-0000-4000-8000-000000000001')$$,
                'student can tick a deadline done');
select lives_ok($$insert into public.announcement_reads (announcement_id, user_id)
    values ('e0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001')$$,
    'student opening a notice is recorded');

-- A student cannot mark a notice for a course they are not in as read.
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000003');  -- pending: cannot even see notices
select throws_ok($$insert into public.announcement_reads (announcement_id, user_id)
    values ('e0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000003')$$,
    '42501', null, 'a person who cannot see a notice cannot record opening it');

-- Student devices are limited to 3.
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000001');
select lives_ok($$select public.register_device('phone-a', 'Phone A')$$, 'student registers phone 1');
select lives_ok($$select public.register_device('phone-b', 'Phone B')$$, 'student registers phone 2');
select lives_ok($$select public.register_device('phone-c', 'Phone C')$$, 'student registers phone 3');
select throws_ok($$select public.register_device('phone-d', 'Phone D')$$, 'P0001', null,
                 'a fourth phone is refused');
select lives_ok($$select public.register_device('phone-a', 'Phone A')$$, 'a known phone may sign in again');
select ok((select public.export_my_data() -> 'profile' ->> 'email') = 'a.mwangi@campus.ac.tz',
          'a person can export their own data');

-- ======================================================================================
-- Kelvin cannot see Asha's private ticks, and no one else can either
-- ======================================================================================
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000002');
select is((select count(*)::int from public.deadline_done), 0, 'a student cannot see another student''s ticks');
reset role;
select tests.as_user('b0000000-0000-4000-8000-000000000001');
select is((select count(*)::int from public.deadline_done), 0, 'a lecturer cannot see anyone''s ticks');
reset role;
select tests.as_user('c0000000-0000-4000-8000-000000000001', 'aal2');
select is((select count(*)::int from public.deadline_done), 0, 'an administrator cannot see anyone''s ticks');
reset role;
select tests.as_user('b0000000-0000-4000-8000-000000000001');
select is(public.deadline_done_count('f0000000-0000-4000-8000-000000000001'), 1,
          'the lecturer may see only the count of ticks');

-- ======================================================================================
-- A lecturer (Dr. Mushi, teaches CS210 and CS215)
-- ======================================================================================
select is((select count(*)::int from public.profiles), 1, 'lecturer cannot read student profiles');
select is((select count(*)::int from public.class_list where course_code = 'CUOM:CS210'), 2,
          'lecturer sees the class list of a course they teach');
select is((select count(*)::int from public.class_list where course_code = 'CUOM:IS202'), 0,
          'lecturer sees no class list for a course they do not teach');
select is((select count(*)::int from public.class_sessions where course_code = 'CUOM:IS202'), 0,
          'lecturer does not see the timetable of courses they do not teach');
select is_empty($$select * from public.audit_log$$, 'lecturer cannot read the audit log');
select throws_ok($$insert into public.announcements (author_id, title, body, audience)
    values ('b0000000-0000-4000-8000-000000000001', 'To everyone', 'Hello', 'campus')$$,
    '42501', null, 'lecturer cannot speak to the whole campus');
select throws_ok($$insert into public.announcements (author_id, title, body, audience, course_code)
    values ('b0000000-0000-4000-8000-000000000001', 'Not mine', 'Hello', 'course', 'CUOM:IS202')$$,
    '42501', null, 'lecturer cannot post to a course they do not teach');
select lives_ok($$insert into public.announcements (id, author_id, title, body, audience, course_code)
    values ('e0000000-0000-4000-8000-0000000000a1', 'b0000000-0000-4000-8000-000000000001',
            'Lab report due', 'Bring it on Friday.', 'course', 'CUOM:CS210')$$,
    'lecturer posts to their own course');
select throws_ok($$insert into public.deadlines (course_code, lecturer_id, title, due_at)
    values ('CUOM:IS202', 'b0000000-0000-4000-8000-000000000001', 'Not mine', now() + interval '3 days')$$,
    '42501', null, 'lecturer cannot set a deadline for a course they do not teach');
select throws_ok($$update public.announcements set priority = 'urgent' where id = 'e0000000-0000-4000-8000-000000000001'$$,
                 'P0001', null, 'editing cannot be used to make a notice Urgent');

-- Urgent is rationed for lecturers (2 a day) and limited to their own course.
select lives_ok($$insert into public.announcements (author_id, title, body, audience, course_code, priority)
    values ('b0000000-0000-4000-8000-000000000001', 'Urgent one', 'x', 'course', 'CUOM:CS210', 'urgent')$$,
    'first Urgent notice is allowed');
select lives_ok($$insert into public.announcements (author_id, title, body, audience, course_code, priority)
    values ('b0000000-0000-4000-8000-000000000001', 'Urgent two', 'x', 'course', 'CUOM:CS210', 'urgent')$$,
    'second Urgent notice is allowed');
select throws_ok($$insert into public.announcements (author_id, title, body, audience, course_code, priority)
    values ('b0000000-0000-4000-8000-000000000001', 'Urgent three', 'x', 'course', 'CUOM:CS210', 'urgent')$$,
    'P0001', null, 'third Urgent notice today is refused');
select is((public.my_limits() ->> 'urgent_left')::int, 0, 'my_limits reports no Urgent notices left');

-- Delivery counts, reminders, retraction.
select is((select delivered from public.announcement_stats('e0000000-0000-4000-8000-0000000000a1')), 2,
          'sender sees how many students it was delivered to');
select is((select opened from public.announcement_stats('e0000000-0000-4000-8000-0000000000a1')), 0,
          'nobody has opened it yet');
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000001');
select lives_ok($$insert into public.announcement_reads (announcement_id, user_id)
    values ('e0000000-0000-4000-8000-0000000000a1', 'a0000000-0000-4000-8000-000000000001')$$,
    'Asha opens the notice');
reset role;
select tests.as_user('b0000000-0000-4000-8000-000000000001');
select is((select opened from public.announcement_stats('e0000000-0000-4000-8000-0000000000a1')), 1,
          'sender sees it was opened once');
select is(public.remind_unread('e0000000-0000-4000-8000-0000000000a1'), 1, 'one student is reminded');
select is(public.remind_unread('e0000000-0000-4000-8000-0000000000a1'), 0, 'only one reminder is ever sent');
select is(public.retract_announcement('e0000000-0000-4000-8000-0000000000a1'), true, 'sender retracts the notice');
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000002');
select is((select count(*)::int from public.announcements where id = 'e0000000-0000-4000-8000-0000000000a1'), 0,
          'a retracted notice disappears for students');
select ok(exists (select 1 from public.notifications where title like 'Correction%'),
          'students get a short correction');

-- A room change needs a reason, is shown to enrolled students, and warns about clashes.
reset role;
select tests.as_user('b0000000-0000-4000-8000-000000000001');
select throws_ok($$update public.class_sessions set venue = 'Lab 2' where id = 'd0000000-0000-4000-8000-000000000001'$$,
                 'P0001', 'reason_required', 'a room change without a reason is refused');
select lives_ok($$update public.class_sessions set venue = 'Lab 2', change_reason = 'Projector fault in A1'
                  where id = 'd0000000-0000-4000-8000-000000000001'$$, 'a room change with a reason is saved');
select is((select status::text from public.class_sessions where id = 'd0000000-0000-4000-8000-000000000001'),
          'changed', 'the session is marked Changed');
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000002');
select ok(exists (select 1 from public.notifications where title = 'Room changed: CS210'),
          'enrolled students are told about the room change');
reset role;
select tests.as_user('b0000000-0000-4000-8000-000000000001');
select throws_ok($$update public.class_sessions set venue = 'Lab 2', change_reason = 'Clash test', day_of_week = 1,
    start_time = '09:00', end_time = '11:00' where id = 'd0000000-0000-4000-8000-000000000002'$$,
    'P0001', null, 'moving a class into a busy room and time is refused');

-- Files: the size is read from storage, never trusted, and limited by role.
reset role;
insert into storage.objects (bucket_id, name, metadata)
values ('materials', 'CUOM:CS210/b0000000-0000-4000-8000-000000000001/notes.pdf', '{"size": 2000000}'),
       ('materials', 'CUOM:CS210/b0000000-0000-4000-8000-000000000001/huge.pdf',  '{"size": 60000000}'),
       ('submissions', 'a0000000-0000-4000-8000-000000000001/f0000000-0000-4000-8000-000000000001/work.pdf', '{"size": 1000000}'),
       ('submissions', 'a0000000-0000-4000-8000-000000000001/f0000000-0000-4000-8000-000000000001/big.pdf',  '{"size": 20000000}');
select tests.as_user('b0000000-0000-4000-8000-000000000001');
select throws_ok($$insert into public.study_materials (course_code, title, uploader_id, storage_path, file_name, mime_type, size_bytes)
    values ('CUOM:CS210', 'Missing', 'b0000000-0000-4000-8000-000000000001', 'CUOM:CS210/b0000000-0000-4000-8000-000000000001/nope.pdf', 'nope.pdf', 'application/pdf', 5)$$,
    'P0001', 'file_not_uploaded', 'a material row needs a real uploaded file');
select throws_ok($$insert into public.study_materials (course_code, title, uploader_id, storage_path, file_name, mime_type, size_bytes)
    values ('CUOM:CS210', 'Huge', 'b0000000-0000-4000-8000-000000000001', 'CUOM:CS210/b0000000-0000-4000-8000-000000000001/huge.pdf', 'huge.pdf', 'application/pdf', 5)$$,
    'P0001', null, 'a lecturer file over 50 MB is refused, whatever size the app claims');
select lives_ok($$insert into public.study_materials (course_code, title, uploader_id, storage_path, file_name, mime_type, size_bytes)
    values ('CUOM:CS210', 'Week 1 notes', 'b0000000-0000-4000-8000-000000000001', 'CUOM:CS210/b0000000-0000-4000-8000-000000000001/notes.pdf', 'notes.pdf', 'application/pdf', 5)$$,
    'a lecturer shares a file for their course');
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000001');
select is((select count(*)::int from public.study_materials), 1, 'enrolled students see the material');
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000003');
select is((select count(*)::int from public.study_materials), 0, 'a pending account does not');

-- Submissions: own work only, size limited, per-course.
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000001');
select throws_ok($$insert into public.submissions (deadline_id, course_code, student_id, storage_path, file_name, mime_type, size_bytes)
    values ('f0000000-0000-4000-8000-000000000001', 'CUOM:CS210', 'a0000000-0000-4000-8000-000000000001',
            'a0000000-0000-4000-8000-000000000001/f0000000-0000-4000-8000-000000000001/big.pdf', 'big.pdf', 'application/pdf', 5)$$,
    'P0001', null, 'a student file over 15 MB is refused');
select lives_ok($$insert into public.submissions (id, deadline_id, course_code, student_id, storage_path, file_name, mime_type, size_bytes)
    values ('a1000000-0000-4000-8000-000000000001', 'f0000000-0000-4000-8000-000000000001', 'CUOM:CS210',
            'a0000000-0000-4000-8000-000000000001',
            'a0000000-0000-4000-8000-000000000001/f0000000-0000-4000-8000-000000000001/work.pdf', 'work.pdf', 'application/pdf', 5)$$,
    'a student submits work');
select throws_ok($$insert into public.submissions (deadline_id, course_code, student_id, storage_path, file_name, mime_type, size_bytes)
    values ('f0000000-0000-4000-8000-000000000001', 'CUOM:CS210', 'a0000000-0000-4000-8000-000000000002',
            'a0000000-0000-4000-8000-000000000001/f0000000-0000-4000-8000-000000000001/work.pdf', 'work.pdf', 'application/pdf', 5)$$,
    '42501', null, 'a student cannot submit as someone else');
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000002');
select is((select count(*)::int from public.submissions), 0, 'another student cannot see that submission');
reset role;
select tests.as_user('b0000000-0000-4000-8000-000000000002');
select is((select count(*)::int from public.submissions), 0, 'a lecturer of another course cannot see it either');
reset role;
select tests.as_user('b0000000-0000-4000-8000-000000000001');
select is((select count(*)::int from public.submissions), 1, 'the course lecturer sees it');
select lives_ok($$update public.submissions set grade = '92/100', feedback = 'Clean work' where id = 'a1000000-0000-4000-8000-000000000001'$$,
                'the lecturer grades it');
select throws_ok($$update public.submissions set student_id = 'a0000000-0000-4000-8000-000000000002' where id = 'a1000000-0000-4000-8000-000000000001'$$,
                 '42501', null, 'a marker may change only grade, feedback and status');
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000001');
select ok(exists (select 1 from public.notifications where title like 'Assignment graded%'), 'the student is told about the grade');

-- ======================================================================================
-- The registry (administrator): two-step sign-in, approvals, audit log
-- ======================================================================================
reset role;
select tests.as_user('c0000000-0000-4000-8000-000000000001', 'aal1');
select is(public.is_admin(), false, 'an administrator without the second step has no admin powers');
reset role;
select tests.as_user('c0000000-0000-4000-8000-000000000001', 'aal2');
select is(public.is_admin(), true, 'with the second step the administrator has admin powers');
select is(public.registry_match('a0000000-0000-4000-8000-000000000003'), true, 'registry sees the number is on the student list');
select throws_ok($$select public.registry_match('a2000000-0000-4000-8000-0000000000f1')$$, '42501', null, 'a Cuom administrator cannot check an IFM person against any list');
select throws_ok($$select public.approve_account('a0000000-0000-4000-8000-000000000003', 'admin')$$,
                 'P0001', 'cannot_grant_admin_here', 'accounts cannot be made administrators from the queue');
select lives_ok($$select public.approve_account('a0000000-0000-4000-8000-000000000003', 'student', '22222222-2222-4222-8222-222222222222')$$,
                'the registry approves Baraka as a student');
select isnt_empty($$select * from public.audit_log where action = 'ACCOUNT_APPROVED'$$, 'the approval is in the audit log');
select throws_ok($$update public.audit_log set details = 'edited'$$, '42501', null, 'an administrator cannot edit an audit entry');
select throws_ok($$delete from public.audit_log$$, '42501', null, 'an administrator cannot erase an audit entry');
select lives_ok($$insert into public.announcements (author_id, title, body, audience)
    values ('c0000000-0000-4000-8000-000000000001', 'Campus notice', 'Open late', 'campus')$$,
    'the registry speaks to the whole campus');

reset role;
select throws_ok($$update public.audit_log set details = 'edited'$$, 'P0001', 'audit_log_is_append_only',
                 'even the database owner cannot edit an audit entry');
select throws_ok($$delete from public.audit_log$$, 'P0001', 'audit_log_is_append_only',
                 'even the database owner cannot erase an audit entry');
select throws_ok($$truncate public.audit_log$$, 'P0001', 'audit_log_is_append_only',
                 'even the database owner cannot truncate the audit log');
select tests.as_user('a0000000-0000-4000-8000-000000000003');
select is((select count(*)::int from public.class_sessions where course_code = 'CUOM:IS202'), 1,
          'Baraka now sees the courses of his programme');
select is((select count(*)::int from public.class_sessions where course_code = 'CUOM:CS210'), 0,
          'and not the courses of another programme');
select ok(exists (select 1 from public.notifications where title like 'Karibu%'), 'Baraka is welcomed');
reset role;
select is(public.limit_for('a0000000-0000-4000-8000-000000000003', 'submissions_per_day'), 20::bigint,
          'an approved student gets the student limits');

-- Switching someone off takes effect immediately.
reset role;
select tests.as_user('c0000000-0000-4000-8000-000000000001', 'aal2');
select lives_ok($$select public.suspend_account('a0000000-0000-4000-8000-000000000002', 'Test')$$, 'the registry suspends Kelvin');
select throws_ok($$select public.suspend_account('c0000000-0000-4000-8000-000000000001', 'oops')$$, 'P0001', 'cannot_suspend_self',
                 'an administrator cannot suspend themselves');
reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000002');
select is_empty($$select * from public.announcements$$, 'a suspended person sees nothing, right away');
reset role;
select is(public.limit_for('a0000000-0000-4000-8000-000000000002', 'submissions_per_day'), 0::bigint, 'and has zero limits');

-- ======================================================================================
-- Universities are kept apart
-- ======================================================================================
reset role;
select tests.as_user('c2000000-0000-4000-8000-000000000001', 'aal2');   -- IFM registry
select is((select count(*)::int from public.profiles where university_id <> (select id from public.universities where code = 'IFM')), 0,
          'an IFM administrator sees no Cuom profile');
select is_empty($$select * from public.class_sessions$$, 'an IFM administrator sees no Cuom timetable');
select is_empty($$select * from public.announcements$$, 'an IFM administrator sees no Cuom announcements');
select is_empty($$select * from public.audit_log where university_id <> (select id from public.universities where code = 'IFM')$$,
                'an IFM administrator sees no Cuom audit entry');
select throws_ok($$select public.approve_account('a0000000-0000-4000-8000-000000000003', 'student')$$, '42501', null,
                 'an IFM administrator cannot approve a Cuom account');
select throws_ok($$select public.suspend_account('a0000000-0000-4000-8000-000000000001', 'x')$$, '42501', null,
                 'an IFM administrator cannot suspend a Cuom student');
select isnt_empty($$select * from public.profiles where id = '88888888-8888-4888-8888-888888888888'$$,
                  'an IFM administrator sees the people waiting to join IFM');
select is(public.registry_match('88888888-8888-4888-8888-888888888888'), null,
          'IFM has not uploaded a list, so there is nothing to check against');
select lives_ok($$select public.approve_account('88888888-8888-4888-8888-888888888888', 'student')$$,
                'and approves them');

reset role;
select tests.as_user('c0000000-0000-4000-8000-000000000001', 'aal2');   -- Cuom registry
select is((select count(*)::int from public.profiles where university_id <> (select id from public.universities where code = 'CUOM')), 0,
          'a Cuom administrator sees no IFM profile');
select throws_ok($$select public.approve_account('a2000000-0000-4000-8000-0000000000f1', 'student')$$, '42501', null,
                 'a Cuom administrator cannot touch an IFM account');
select lives_ok($$insert into public.announcements (author_id, title, body, audience)
    values ('c0000000-0000-4000-8000-000000000001', 'Cuom campus only', 'Hello Cuom', 'campus')$$, 'Cuom posts campus-wide');

reset role;
select tests.as_user('a2000000-0000-4000-8000-0000000000f1');           -- IFM student
select is_empty($$select * from public.announcements$$, 'a Cuom campus notice does not reach an IFM student');
select is((select count(*)::int from public.notifications where title = 'Cuom campus only'), 0,
          'and sends them no notification');

reset role;
select tests.as_user('a0000000-0000-4000-8000-000000000001');           -- Cuom student
select is((select count(*)::int from public.announcements where title = 'Cuom campus only'), 1, 'but it does reach Cuom students');

-- ======================================================================================
-- Guards that keep future changes honest
-- ======================================================================================
reset role;
select is_empty(
  $$select tablename from pg_tables where schemaname = 'public' and not rowsecurity$$,
  'every table in the public schema has row-level security switched on');
select is((select count(*)::int from public.role_limits), 12,
          'there is a limits row for every role and membership status');
select is_empty(
  $$select role, status from public.role_limits
    where status <> 'approved' and (coalesce(announcements_per_day, 1) <> 0 or coalesce(materials_per_day, 1) <> 0
          or coalesce(submissions_per_day, 1) <> 0 or coalesce(deadlines_per_day, 1) <> 0)$$,
  'accounts that are not approved have zero limits for everything');
select is((select urgent_per_day from public.role_limits where role = 'lecturer' and status = 'approved'), 2,
          'lecturers may send two Urgent notices a day');
select ok((select urgent_per_day is null from public.role_limits where role = 'admin' and status = 'approved'),
          'the registry has no Urgent limit');
select is_empty(
  $$select p.proname from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname in ('bootstrap_first_admin', 'log_audit', 'notify_users', 'limit_for', 'check_limit')
      and (has_function_privilege('anon', p.oid, 'execute') or has_function_privilege('authenticated', p.oid, 'execute'))$$,
  'internal functions cannot be called by app users');

select * from finish();
rollback;
