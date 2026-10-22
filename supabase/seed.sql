-- =====================================================================================
-- DEVELOPMENT SEED. Fake people and sample data for local work and CI.
-- Never run this against a real project: it creates accounts with a known password.
-- Password for every sample account: Campus#2026
-- =====================================================================================

create extension if not exists pgcrypto;

insert into public.programmes (id, name) values
  ('11111111-1111-4111-8111-111111111111', 'BSc Computer Science'),
  ('22222222-2222-4222-8222-222222222222', 'BSc Information Systems')
on conflict (name) do nothing;

insert into public.courses (code, title) values
  ('CS210', 'Data Structures & Algorithms'), ('MT101', 'Discrete Mathematics'),
  ('CS212', 'Database Systems'), ('IS202', 'Systems Analysis'),
  ('CS215', 'Computer Networks'), ('GS101', 'Communication Skills')
on conflict (code) do nothing;

insert into public.programme_courses (programme_id, course_code) values
  ('11111111-1111-4111-8111-111111111111', 'CS210'), ('11111111-1111-4111-8111-111111111111', 'MT101'),
  ('11111111-1111-4111-8111-111111111111', 'CS212'), ('11111111-1111-4111-8111-111111111111', 'IS202'),
  ('11111111-1111-4111-8111-111111111111', 'CS215'), ('11111111-1111-4111-8111-111111111111', 'GS101'),
  ('22222222-2222-4222-8222-222222222222', 'IS202'), ('22222222-2222-4222-8222-222222222222', 'MT101'),
  ('22222222-2222-4222-8222-222222222222', 'GS101')
on conflict do nothing;

insert into public.university_students (student_number) values
  ('T22-03-08492'), ('T22-03-09120'), ('T24-03-01140'), ('T23-03-09412')
on conflict do nothing;
insert into public.university_staff (staff_number) values
  ('STF-CS-041'), ('STF-IS-033'), ('STF-ADM-001'), ('STF-EE-078')
on conflict do nothing;

-- People. They go through the real sign-up trigger, so they start as PENDING students.
insert into auth.users
  (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
   raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
   confirmation_token, email_change, email_change_token_new, recovery_token)
select '00000000-0000-0000-0000-000000000000', u.id, 'authenticated', 'authenticated', u.email,
       crypt('Campus#2026', gen_salt('bf')), now(),
       '{"provider":"email","providers":["email"]}'::jsonb, u.meta::jsonb, now(), now(), '', '', '', ''
from (values
  ('a0000000-0000-4000-8000-000000000001'::uuid, 'a.mwangi@campus.ac.tz',
     '{"full_name":"Asha Mwangi","id_number":"T22-03-08492","programme":"BSc Computer Science"}'),
  ('a0000000-0000-4000-8000-000000000002'::uuid, 'k.tarimo@campus.ac.tz',
     '{"full_name":"Kelvin Tarimo","id_number":"T22-03-09120","programme":"BSc Computer Science"}'),
  ('a0000000-0000-4000-8000-000000000003'::uuid, 'b.juma@campus.ac.tz',
     '{"full_name":"Baraka Juma","id_number":"T24-03-01140","programme":"BSc Information Systems"}'),
  ('b0000000-0000-4000-8000-000000000001'::uuid, 'j.mushi@campus.ac.tz',
     '{"full_name":"Dr. Joseph Mushi","id_number":"STF-CS-041","is_staff":true,"department":"Computer Science"}'),
  ('b0000000-0000-4000-8000-000000000002'::uuid, 'd.kweka@campus.ac.tz',
     '{"full_name":"Eng. Daniel Kweka","id_number":"STF-IS-033","is_staff":true,"department":"Information Systems"}'),
  ('c0000000-0000-4000-8000-000000000001'::uuid, 'admin.registry@campus.ac.tz',
     '{"full_name":"Emanuel Mwenda","id_number":"STF-ADM-001","is_staff":true,"department":"Academic Registry"}')
) as u(id, email, meta)
on conflict (id) do nothing;

-- Registry decisions (done here as the database owner, like a first-time setup).
update public.profiles set role = 'student', status = 'approved', cohort = 'Year 2 - Semester 1',
  programme_id = '11111111-1111-4111-8111-111111111111'
where id in ('a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002');
update public.profiles set role = 'lecturer', status = 'approved', cohort = 'Lecturer'
where id in ('b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002');
update public.profiles set role = 'admin', status = 'approved', cohort = 'Chief Administrator'
where id = 'c0000000-0000-4000-8000-000000000001';
-- Baraka stays pending, so the waiting room can be tried.

insert into public.enrollments (student_id, course_code)
select s, c from (values ('a0000000-0000-4000-8000-000000000001'::uuid), ('a0000000-0000-4000-8000-000000000002'::uuid)) a(s),
                 (values ('CS210'), ('MT101'), ('CS212'), ('IS202'), ('CS215'), ('GS101')) b(c)
on conflict do nothing;

insert into public.teaching_assignments (lecturer_id, course_code) values
  ('b0000000-0000-4000-8000-000000000001', 'CS210'), ('b0000000-0000-4000-8000-000000000001', 'CS215'),
  ('b0000000-0000-4000-8000-000000000002', 'IS202')
on conflict do nothing;

insert into public.class_sessions (id, course_code, kind, lecturer_id, day_of_week, start_time, end_time, venue) values
  ('d0000000-0000-4000-8000-000000000001', 'CS210', 'lecture', 'b0000000-0000-4000-8000-000000000001', 1, '08:00', '10:00', 'Lecture Hall A1'),
  ('d0000000-0000-4000-8000-000000000002', 'CS215', 'lecture', 'b0000000-0000-4000-8000-000000000001', 3, '10:00', '12:00', 'Lab 1'),
  ('d0000000-0000-4000-8000-000000000003', 'IS202', 'lecture', 'b0000000-0000-4000-8000-000000000002', 2, '14:00', '16:00', 'Room B4')
on conflict do nothing;

-- Content is inserted with the owner's rights, so the limit triggers still count it.
insert into public.announcements (id, author_id, title, body, audience, course_code, priority) values
  ('e0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001',
   'CS210 test moved', 'The midterm moves to Thursday 10:00 in A1.', 'course', 'CS210', 'academic'),
  ('e0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000002',
   'IS202 case study', 'Bring the printed case study on Tuesday.', 'course', 'IS202', 'normal')
on conflict do nothing;
insert into public.announcements (id, author_id, title, body, audience, priority) values
  ('e0000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000001',
   'Library open until 22:00', 'The library stays open until 22:00 during exams.', 'campus', 'normal')
on conflict do nothing;

insert into public.deadlines (id, course_code, lecturer_id, title, instructions, due_at) values
  ('f0000000-0000-4000-8000-000000000001', 'CS210', 'b0000000-0000-4000-8000-000000000001',
   'Programming Assignment 2', 'Implement BFS and DFS. Submit one PDF.', now() + interval '7 days'),
  ('f0000000-0000-4000-8000-000000000002', 'IS202', 'b0000000-0000-4000-8000-000000000002',
   'Case study report', 'Two pages.', now() + interval '5 days')
on conflict do nothing;
insert into public.deadlines (id, course_code, lecturer_id, title, instructions, due_at) values
  ('f0000000-0000-4000-8000-000000000003', null, 'c0000000-0000-4000-8000-000000000001',
   'Exam registration closes', 'Register at the registry office.', now() + interval '10 days')
on conflict do nothing;
