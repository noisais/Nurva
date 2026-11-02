-- Nurva private synchronization schema. Review and test on a disposable Supabase project first.
create extension if not exists pgcrypto with schema extensions;

create table if not exists public.nurva_groups (
  id uuid primary key default extensions.gen_random_uuid(),
  display_name text not null check (char_length(trim(display_name)) between 1 and 60),
  owner_user_id uuid not null references auth.users(id) on delete restrict,
  current_revision bigint not null default 0 check (current_revision >= 0),
  current_theme_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.nurva_group_members (
  group_id uuid not null references public.nurva_groups(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('owner','admin','editor','viewer')),
  joined_at timestamptz not null default now(),
  removed_at timestamptz,
  primary key (group_id,user_id)
);
create index if not exists nurva_members_user_idx on public.nurva_group_members(user_id);

create table if not exists public.nurva_devices (
  id uuid primary key,
  group_id uuid not null references public.nurva_groups(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  display_name text not null check (char_length(trim(display_name)) between 1 and 80),
  secret_hash text not null check (secret_hash ~ '^[0-9a-f]{64}$'),
  wallpaper_target text not null default 'lock' check (wallpaper_target in ('lock','home','both')),
  apply_mode text not null default 'manual' check (apply_mode in ('auto','manual')),
  last_applied_revision bigint not null default 0,
  last_seen_at timestamptz,
  created_at timestamptz not null default now(),
  revoked_at timestamptz
);
create index if not exists nurva_devices_group_idx on public.nurva_devices(group_id);

create table if not exists public.nurva_invites (
  id uuid primary key default extensions.gen_random_uuid(),
  group_id uuid not null references public.nurva_groups(id) on delete cascade,
  token_hash text not null unique check (token_hash ~ '^[0-9a-f]{64}$'),
  role text not null check (role in ('editor','viewer')),
  created_by uuid not null references auth.users(id),
  expires_at timestamptz not null,
  max_uses integer not null default 1 check (max_uses between 1 and 100),
  uses integer not null default 0 check (uses between 0 and max_uses),
  created_at timestamptz not null default now(),
  revoked_at timestamptz
);

create table if not exists public.nurva_pending_uploads (
  asset_id uuid primary key,
  group_id uuid not null references public.nurva_groups(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id uuid not null references public.nurva_devices(id) on delete cascade,
  storage_path text not null unique,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.nurva_wallpaper_revisions (
  id uuid primary key default extensions.gen_random_uuid(),
  group_id uuid not null references public.nurva_groups(id) on delete cascade,
  revision bigint not null check (revision > 0),
  created_by uuid not null references auth.users(id),
  storage_path text not null unique,
  sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  byte_size bigint not null check (byte_size between 1 and 52428800),
  image_width integer not null check (image_width between 1 and 30000),
  image_height integer not null check (image_height between 1 and 30000),
  title text,
  created_at timestamptz not null default now(),
  unique (group_id,revision)
);

create table if not exists public.nurva_mosaic_tiles (
  group_id uuid not null references public.nurva_groups(id) on delete cascade,
  revision bigint not null,
  device_id uuid not null references public.nurva_devices(id) on delete cascade,
  tile_index integer not null check (tile_index >= 0),
  row_index integer not null check (row_index >= 0),
  column_index integer not null check (column_index >= 0),
  crop_left real not null check (crop_left >= 0 and crop_left < 1),
  crop_top real not null check (crop_top >= 0 and crop_top < 1),
  crop_right real not null check (crop_right > 0 and crop_right <= 1),
  crop_bottom real not null check (crop_bottom > 0 and crop_bottom <= 1),
  output_width integer not null check (output_width > 0),
  output_height integer not null check (output_height > 0),
  primary key (group_id,revision,device_id),
  unique (group_id,revision,tile_index),
  unique (group_id,revision,row_index,column_index),
  foreign key (group_id,revision) references public.nurva_wallpaper_revisions(group_id,revision) on delete cascade,
  check (crop_left < crop_right and crop_top < crop_bottom)
);

create table if not exists public.nurva_themes (
  id uuid primary key default extensions.gen_random_uuid(),
  group_id uuid not null references public.nurva_groups(id) on delete cascade,
  name text not null check (char_length(trim(name)) between 1 and 80),
  layout_mode text not null check (layout_mode in ('shared','per_device','mosaic')),
  description text,
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (group_id,id)
);
alter table public.nurva_groups drop constraint if exists nurva_groups_current_theme_fk;
alter table public.nurva_groups add constraint nurva_groups_current_theme_fk
  foreign key (id,current_theme_id) references public.nurva_themes(group_id,id) deferrable initially deferred;

create table if not exists public.nurva_theme_items (
  theme_id uuid not null references public.nurva_themes(id) on delete cascade,
  position integer not null check (position >= 0),
  wallpaper_revision_id uuid not null references public.nurva_wallpaper_revisions(id) on delete restrict,
  device_id uuid references public.nurva_devices(id) on delete cascade,
  primary key (theme_id,position)
);

create table if not exists public.nurva_schedules (
  id uuid primary key default extensions.gen_random_uuid(),
  group_id uuid not null references public.nurva_groups(id) on delete cascade,
  theme_id uuid references public.nurva_themes(id) on delete set null,
  days_of_week_mask integer not null check (days_of_week_mask between 1 and 127),
  local_hour integer not null check (local_hour between 0 and 23),
  local_minute integer not null check (local_minute between 0 and 59),
  time_zone_id text not null,
  enabled boolean not null default true,
  require_approval boolean not null default false,
  next_run_at timestamptz,
  updated_at timestamptz not null default now()
);

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('nurva-wallpapers','nurva-wallpapers',false,52428800,array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set public=false,file_size_limit=excluded.file_size_limit,allowed_mime_types=excluded.allowed_mime_types;

-- Direct table and storage access is intentionally not granted to mobile clients. The app
-- calls narrowly-scoped RPCs and Edge Functions that validate account membership + device secret.
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['nurva_groups','nurva_group_members','nurva_devices','nurva_invites','nurva_pending_uploads','nurva_wallpaper_revisions','nurva_mosaic_tiles','nurva_themes','nurva_theme_items','nurva_schedules'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
    EXECUTE format('REVOKE ALL ON public.%I FROM anon, authenticated',t);
  END LOOP;
END $$;

create or replace function public.nurva_assert_active_device(p_group uuid,p_device uuid,p_secret text)
returns boolean language sql stable security definer set search_path=''
as $$
  select auth.uid() is not null and p_secret is not null and exists (
    select 1 from public.nurva_devices d join public.nurva_group_members m
      on m.group_id=d.group_id and m.user_id=d.user_id
    where d.id=p_device and d.group_id=p_group and d.user_id=auth.uid()
      and d.revoked_at is null and m.removed_at is null
      and d.secret_hash=encode(extensions.digest(convert_to(p_secret,'UTF8'),'sha256'),'hex')
  );
$$;
revoke all on function public.nurva_assert_active_device(uuid,uuid,text) from public,anon,authenticated;

create or replace function public.nurva_create_group(p_name text,p_device uuid,p_secret text,p_device_name text)
returns uuid language plpgsql security definer set search_path=''
as $$
declare u uuid:=auth.uid(); g uuid;
begin
  if u is null then raise exception 'Authentication required' using errcode='28000'; end if;
  if p_name is null or char_length(trim(p_name)) not between 1 and 60 then raise exception 'Invalid group name' using errcode='22023'; end if;
  if p_device is null or p_secret is null or char_length(p_secret)<32 then raise exception 'Invalid device credential' using errcode='22023'; end if;
  if p_device_name is null or char_length(trim(p_device_name)) not between 1 and 80 then raise exception 'Invalid device name' using errcode='22023'; end if;
  insert into public.nurva_groups(display_name,owner_user_id) values(trim(p_name),u) returning id into g;
  insert into public.nurva_group_members(group_id,user_id,role) values(g,u,'owner');
  insert into public.nurva_devices(id,group_id,user_id,display_name,secret_hash)
    values(p_device,g,u,trim(p_device_name),encode(extensions.digest(convert_to(p_secret,'UTF8'),'sha256'),'hex'));
  return g;
end $$;
revoke all on function public.nurva_create_group(text,uuid,text,text) from public,anon;
grant execute on function public.nurva_create_group(text,uuid,text,text) to authenticated;

create or replace function public.nurva_create_invite(p_group uuid,p_device uuid,p_secret text,p_role text,p_expires timestamptz,p_uses integer default 1)
returns table(invite_id uuid,invite_token text,expires_at timestamptz)
language plpgsql security definer set search_path=''
as $$
declare u uuid:=auth.uid(); r text; token text; iid uuid;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then raise exception 'Device not authorized' using errcode='42501'; end if;
  select role into r from public.nurva_group_members where group_id=p_group and user_id=u and removed_at is null;
  if coalesce(r,'') not in ('owner','admin') then raise exception 'Only owners/admins can invite' using errcode='42501'; end if;
  if p_role is null or p_role not in ('editor','viewer') then raise exception 'Invalid invitation role' using errcode='22023'; end if;
  if p_expires is null or p_expires<=now() or p_expires>now()+interval '30 days' then raise exception 'Invalid expiry' using errcode='22023'; end if;
  if p_uses is null or p_uses not between 1 and 100 then raise exception 'Invalid use count' using errcode='22023'; end if;
  token:=encode(extensions.gen_random_bytes(32),'hex');
  insert into public.nurva_invites(group_id,token_hash,role,created_by,expires_at,max_uses)
    values(p_group,encode(extensions.digest(convert_to(token,'UTF8'),'sha256'),'hex'),p_role,u,p_expires,p_uses) returning id into iid;
  return query select iid,token,p_expires;
end $$;
revoke all on function public.nurva_create_invite(uuid,uuid,text,text,timestamptz,integer) from public,anon;
grant execute on function public.nurva_create_invite(uuid,uuid,text,text,timestamptz,integer) to authenticated;

create or replace function public.nurva_join_with_invite(p_token text,p_device uuid,p_secret text,p_name text,p_target text default 'lock',p_mode text default 'manual')
returns table(group_id uuid,member_role text,device_id uuid)
language plpgsql security definer set search_path=''
as $$
declare u uuid:=auth.uid(); i public.nurva_invites%rowtype;
begin
  if u is null then raise exception 'Authentication required' using errcode='28000'; end if;
  if p_token is null or char_length(p_token)<32 or p_secret is null or char_length(p_secret)<32 then raise exception 'Invalid invitation/device credential' using errcode='22023'; end if;
  if p_target is null or p_target not in ('lock','home','both') or p_mode is null or p_mode not in ('auto','manual') then raise exception 'Invalid device preferences' using errcode='22023'; end if;
  if p_name is null or char_length(trim(p_name)) not between 1 and 80 then raise exception 'Invalid device name' using errcode='22023'; end if;
  select * into i from public.nurva_invites where token_hash=encode(extensions.digest(convert_to(p_token,'UTF8'),'sha256'),'hex')
    and revoked_at is null and expires_at>now() and uses<max_uses for update;
  if not found then raise exception 'Invitation is invalid/expired/used' using errcode='22023'; end if;
  update public.nurva_invites set uses=uses+1 where id=i.id;
  insert into public.nurva_group_members as existing_member(group_id,user_id,role)
    values(i.group_id,u,i.role)
    on conflict(group_id,user_id) do update set
      role=case when existing_member.role in ('owner','admin') then existing_member.role
                when existing_member.role='editor' or excluded.role='editor' then 'editor' else 'viewer' end,
      removed_at=null;
  insert into public.nurva_devices(id,group_id,user_id,display_name,secret_hash,wallpaper_target,apply_mode)
    values(p_device,i.group_id,u,trim(p_name),encode(extensions.digest(convert_to(p_secret,'UTF8'),'sha256'),'hex'),p_target,p_mode);
  return query select i.group_id,m.role,p_device from public.nurva_group_members m
    where m.group_id=i.group_id and m.user_id=u and m.removed_at is null;
end $$;
revoke all on function public.nurva_join_with_invite(text,uuid,text,text,text,text) from public,anon;
grant execute on function public.nurva_join_with_invite(text,uuid,text,text,text,text) to authenticated;

create or replace function public.nurva_get_sync_state(p_group uuid,p_device uuid,p_secret text)
returns jsonb language plpgsql stable security definer set search_path=''
as $$
declare state jsonb;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then raise exception 'Device not authorized' using errcode='42501'; end if;
  select jsonb_build_object(
    'group',jsonb_build_object('id',g.id,'displayName',g.display_name,'ownerUserId',g.owner_user_id,'currentRevision',g.current_revision,'currentThemeId',g.current_theme_id,'updatedAt',g.updated_at),
    'members',(select coalesce(jsonb_agg(jsonb_build_object('userId',m.user_id,'role',m.role,'joinedAt',m.joined_at)),'[]'::jsonb) from public.nurva_group_members m where m.group_id=g.id and m.removed_at is null),
    'devices',(select coalesce(jsonb_agg(jsonb_build_object('id',d.id,'displayName',d.display_name,'wallpaperTarget',d.wallpaper_target,'applyMode',d.apply_mode,'lastAppliedRevision',d.last_applied_revision,'lastSeenAt',d.last_seen_at)),'[]'::jsonb) from public.nurva_devices d where d.group_id=g.id and d.revoked_at is null),
    'currentWallpaper',(select jsonb_build_object('revisionId',r.id,'revision',r.revision,'storagePath',r.storage_path,'sha256',r.sha256,'mimeType',r.mime_type,'byteSize',r.byte_size,'imageWidth',r.image_width,'imageHeight',r.image_height,'title',r.title) from public.nurva_wallpaper_revisions r where r.group_id=g.id and r.revision=g.current_revision),
    'mosaicTiles',(select coalesce(jsonb_agg(jsonb_build_object('deviceId',mt.device_id,'tileIndex',mt.tile_index,'rowIndex',mt.row_index,'columnIndex',mt.column_index,'cropLeft',mt.crop_left,'cropTop',mt.crop_top,'cropRight',mt.crop_right,'cropBottom',mt.crop_bottom,'outputWidth',mt.output_width,'outputHeight',mt.output_height) order by mt.tile_index),'[]'::jsonb) from public.nurva_mosaic_tiles mt where mt.group_id=g.id and mt.revision=g.current_revision),
    'themes',(select coalesce(jsonb_agg(jsonb_build_object('id',t.id,'name',t.name,'layoutMode',t.layout_mode,'description',t.description,'items',(select coalesce(jsonb_agg(jsonb_build_object('position',ti.position,'revisionId',r.id,'revision',r.revision,'storagePath',r.storage_path,'sha256',r.sha256,'deviceId',ti.device_id) order by ti.position),'[]'::jsonb) from public.nurva_theme_items ti join public.nurva_wallpaper_revisions r on r.id=ti.wallpaper_revision_id where ti.theme_id=t.id))),'[]'::jsonb) from public.nurva_themes t where t.group_id=g.id),
    'schedules',(select coalesce(jsonb_agg(jsonb_build_object('id',s.id,'themeId',s.theme_id,'daysOfWeekMask',s.days_of_week_mask,'localHour',s.local_hour,'localMinute',s.local_minute,'timeZoneId',s.time_zone_id,'enabled',s.enabled,'requireApproval',s.require_approval)),'[]'::jsonb) from public.nurva_schedules s where s.group_id=g.id)
  ) into state from public.nurva_groups g where g.id=p_group;
  if state is null then raise exception 'Group not found' using errcode='22023'; end if;
  return state;
end $$;
revoke all on function public.nurva_get_sync_state(uuid,uuid,text) from public,anon;
grant execute on function public.nurva_get_sync_state(uuid,uuid,text) to authenticated;

create or replace function public.nurva_update_device_preferences(p_group uuid,p_device uuid,p_secret text,p_target text,p_mode text)
returns boolean language plpgsql security definer set search_path=''
as $$
declare n integer;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then raise exception 'Device not authorized' using errcode='42501'; end if;
  if p_target is null or p_target not in ('lock','home','both') or p_mode is null or p_mode not in ('auto','manual') then raise exception 'Invalid preference values' using errcode='22023'; end if;
  update public.nurva_devices set wallpaper_target=p_target,apply_mode=p_mode,last_seen_at=now()
    where id=p_device and group_id=p_group and revoked_at is null;
  get diagnostics n=row_count; return n=1;
end $$;
revoke all on function public.nurva_update_device_preferences(uuid,uuid,text,text,text) from public,anon;
grant execute on function public.nurva_update_device_preferences(uuid,uuid,text,text,text) to authenticated;

-- Upload RPC is service-role-only. Edge Function verifies image bytes and uses the single-use
-- pending upload capability before consuming it and creating this authoritative revision.
create or replace function public.nurva_publish_wallpaper_revision(p_group uuid,p_device uuid,p_user uuid,p_asset uuid,p_hash text,p_mime text,p_size bigint,p_width integer,p_height integer,p_title text default null)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare role_name text; upload public.nurva_pending_uploads%rowtype; rev bigint; rid uuid;
begin
  select role into role_name from public.nurva_group_members where group_id=p_group and user_id=p_user and removed_at is null;
  if coalesce(role_name,'') not in ('owner','admin','editor') then raise exception 'Cannot publish in this group' using errcode='42501'; end if;
  if not exists(select 1 from public.nurva_devices where id=p_device and group_id=p_group and user_id=p_user and revoked_at is null) then raise exception 'Device is revoked or invalid' using errcode='42501'; end if;
  select * into upload from public.nurva_pending_uploads where asset_id=p_asset and group_id=p_group and user_id=p_user and device_id=p_device and expires_at>now() and consumed_at is null for update;
  if not found then raise exception 'Pending upload invalid, expired or consumed' using errcode='22023'; end if;
  if p_hash is null or p_hash !~ '^[0-9a-f]{64}$' or p_mime not in ('image/jpeg','image/png','image/webp') then raise exception 'Invalid image metadata' using errcode='22023'; end if;
  if p_size not between 1 and 52428800 or p_width not between 1 and 30000 or p_height not between 1 and 30000 or p_width::bigint*p_height::bigint>60000000 then raise exception 'Image exceeds allowed limits' using errcode='22023'; end if;
  if not exists(select 1 from storage.objects where bucket_id='nurva-wallpapers' and name=upload.storage_path) then raise exception 'Object missing' using errcode='22023'; end if;
  update public.nurva_pending_uploads set consumed_at=now() where asset_id=p_asset;
  update public.nurva_groups set current_revision=current_revision+1,updated_at=now() where id=p_group returning current_revision into rev;
  insert into public.nurva_wallpaper_revisions(group_id,revision,created_by,storage_path,sha256,mime_type,byte_size,image_width,image_height,title)
    values(p_group,rev,p_user,upload.storage_path,p_hash,p_mime,p_size,p_width,p_height,nullif(trim(p_title),'')) returning id into rid;
  return jsonb_build_object('revisionId',rid,'revision',rev);
end $$;
revoke all on function public.nurva_publish_wallpaper_revision(uuid,uuid,uuid,uuid,text,text,bigint,integer,integer,text) from public,anon,authenticated;
grant execute on function public.nurva_publish_wallpaper_revision(uuid,uuid,uuid,uuid,text,text,bigint,integer,integer,text) to service_role;

create or replace function public.nurva_revoke_device(p_group uuid,p_target_device uuid,p_requester uuid,p_secret text)
returns boolean language plpgsql security definer set search_path=''
as $$
declare r text; target_role text; n integer;
begin
  if not public.nurva_assert_active_device(p_group,p_requester,p_secret) then raise exception 'Requester device not authorized' using errcode='42501'; end if;
  select role into r from public.nurva_group_members where group_id=p_group and user_id=auth.uid() and removed_at is null;
  select m.role into target_role from public.nurva_devices d join public.nurva_group_members m on m.group_id=d.group_id and m.user_id=d.user_id
    where d.id=p_target_device and d.group_id=p_group and d.revoked_at is null and m.removed_at is null;
  if coalesce(r,'') not in ('owner','admin') then raise exception 'Not permitted' using errcode='42501'; end if;
  if target_role is null then return false; end if;
  if target_role='owner' and r<>'owner' then raise exception 'Only owner can revoke owner device' using errcode='42501'; end if;
  if r='admin' and target_role in ('owner','admin') then raise exception 'Admin cannot revoke owner/admin device' using errcode='42501'; end if;
  update public.nurva_devices set revoked_at=now() where id=p_target_device and group_id=p_group and revoked_at is null;
  get diagnostics n=row_count; return n=1;
end $$;
revoke all on function public.nurva_revoke_device(uuid,uuid,uuid,text) from public,anon;
grant execute on function public.nurva_revoke_device(uuid,uuid,uuid,text) to authenticated;

create or replace function public.nurva_remove_member(p_group uuid,p_user uuid,p_requester uuid,p_secret text)
returns boolean language plpgsql security definer set search_path=''
as $$
declare r text; tr text; n integer;
begin
  if not public.nurva_assert_active_device(p_group,p_requester,p_secret) then raise exception 'Requester device not authorized' using errcode='42501'; end if;
  select role into r from public.nurva_group_members where group_id=p_group and user_id=auth.uid() and removed_at is null;
  select role into tr from public.nurva_group_members where group_id=p_group and user_id=p_user and removed_at is null;
  if coalesce(r,'') not in ('owner','admin') then raise exception 'Not permitted' using errcode='42501'; end if;
  if tr is null then return false; end if;
  if tr='owner' then raise exception 'Transfer ownership before removing owner' using errcode='42501'; end if;
  if r='admin' and tr='admin' then raise exception 'Admin cannot remove another admin' using errcode='42501'; end if;
  update public.nurva_group_members set removed_at=now() where group_id=p_group and user_id=p_user and removed_at is null;
  get diagnostics n=row_count;
  update public.nurva_devices set revoked_at=now() where group_id=p_group and user_id=p_user and revoked_at is null;
  return n=1;
end $$;
revoke all on function public.nurva_remove_member(uuid,uuid,uuid,text) from public,anon;
grant execute on function public.nurva_remove_member(uuid,uuid,uuid,text) to authenticated;

-- Themes contain shared, per-device or mosaic artwork collections.
create or replace function public.nurva_save_theme(p_group uuid,p_device uuid,p_secret text,p_theme uuid,p_name text,p_layout text,p_items jsonb,p_activate boolean default true)
returns uuid language plpgsql security definer set search_path=''
as $$
declare u uuid:=auth.uid(); role_name text; tid uuid:=coalesce(p_theme,extensions.gen_random_uuid()); item jsonb; pos integer:=0; rid uuid; did uuid; n integer;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then raise exception 'Device not authorized' using errcode='42501'; end if;
  select role into role_name from public.nurva_group_members where group_id=p_group and user_id=u and removed_at is null;
  if coalesce(role_name,'') not in ('owner','admin','editor') then raise exception 'Cannot edit themes' using errcode='42501'; end if;
  if p_name is null or char_length(trim(p_name)) not between 1 and 80 then raise exception 'Invalid theme name' using errcode='22023'; end if;
  if p_layout is null or p_layout not in ('shared','per_device','mosaic') then raise exception 'Invalid theme layout' using errcode='22023'; end if;
  if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items) not between 1 and 100 then raise exception 'Theme needs 1..100 items' using errcode='22023'; end if;
  insert into public.nurva_themes(id,group_id,name,layout_mode,created_by)
    values(tid,p_group,trim(p_name),p_layout,u)
    on conflict(id) do update set name=excluded.name,layout_mode=excluded.layout_mode,updated_at=now()
    where public.nurva_themes.group_id=excluded.group_id;
  get diagnostics n=row_count; if n<>1 then raise exception 'Theme ID does not belong to group' using errcode='22023'; end if;
  delete from public.nurva_theme_items where theme_id=tid;
  for item in select value from jsonb_array_elements(p_items) loop
    begin rid:=(item->>'revisionId')::uuid; did:=nullif(item->>'deviceId','')::uuid;
    exception when others then raise exception 'Malformed theme item' using errcode='22023'; end;
    if not exists(select 1 from public.nurva_wallpaper_revisions where id=rid and group_id=p_group) then raise exception 'Theme item references another group' using errcode='42501'; end if;
    if did is not null and not exists(select 1 from public.nurva_devices where id=did and group_id=p_group and revoked_at is null) then raise exception 'Theme item references inactive device' using errcode='42501'; end if;
    insert into public.nurva_theme_items(theme_id,position,wallpaper_revision_id,device_id) values(tid,pos,rid,did);
    pos:=pos+1;
  end loop;
  if coalesce(p_activate,true) then update public.nurva_groups set current_theme_id=tid,updated_at=now() where id=p_group; end if;
  return tid;
end $$;
revoke all on function public.nurva_save_theme(uuid,uuid,text,uuid,text,text,jsonb,boolean) from public,anon;
grant execute on function public.nurva_save_theme(uuid,uuid,text,uuid,text,text,jsonb,boolean) to authenticated;

create or replace function public.nurva_save_schedule(p_group uuid,p_device uuid,p_secret text,p_schedule uuid,p_theme uuid,p_days integer,p_hour integer,p_minute integer,p_zone text,p_enabled boolean,p_approval boolean)
returns uuid language plpgsql security definer set search_path=''
as $$
declare u uuid:=auth.uid(); role_name text; sid uuid:=coalesce(p_schedule,extensions.gen_random_uuid()); n integer;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then raise exception 'Device not authorized' using errcode='42501'; end if;
  select role into role_name from public.nurva_group_members where group_id=p_group and user_id=u and removed_at is null;
  if coalesce(role_name,'') not in ('owner','admin','editor') then raise exception 'Cannot edit schedules' using errcode='42501'; end if;
  if p_days is null or p_days not between 1 and 127 or p_hour is null or p_hour not between 0 and 23 or p_minute is null or p_minute not between 0 and 59 then raise exception 'Invalid schedule time' using errcode='22023'; end if;
  if p_zone is null or not exists(select 1 from pg_catalog.pg_timezone_names where name=trim(p_zone)) then raise exception 'Unknown IANA time zone' using errcode='22023'; end if;
  if p_theme is not null and not exists(select 1 from public.nurva_themes where id=p_theme and group_id=p_group) then raise exception 'Theme belongs to another group' using errcode='42501'; end if;
  insert into public.nurva_schedules(id,group_id,theme_id,days_of_week_mask,local_hour,local_minute,time_zone_id,enabled,require_approval,updated_at)
    values(sid,p_group,p_theme,p_days,p_hour,p_minute,trim(p_zone),coalesce(p_enabled,true),coalesce(p_approval,false),now())
    on conflict(id) do update set theme_id=excluded.theme_id,days_of_week_mask=excluded.days_of_week_mask,local_hour=excluded.local_hour,
      local_minute=excluded.local_minute,time_zone_id=excluded.time_zone_id,enabled=excluded.enabled,require_approval=excluded.require_approval,updated_at=now()
    where public.nurva_schedules.group_id=excluded.group_id;
  get diagnostics n=row_count; if n<>1 then raise exception 'Schedule does not belong to group' using errcode='22023'; end if;
  return sid;
end $$;
revoke all on function public.nurva_save_schedule(uuid,uuid,text,uuid,uuid,integer,integer,integer,text,boolean,boolean) from public,anon;
grant execute on function public.nurva_save_schedule(uuid,uuid,text,uuid,uuid,integer,integer,integer,text,boolean,boolean) to authenticated;

create or replace function public.nurva_save_mosaic_tiles(p_group uuid,p_device uuid,p_secret text,p_revision bigint,p_tiles jsonb)
returns integer language plpgsql security definer set search_path=''
as $$
declare role_name text; item jsonb; did uuid; idx integer; rw integer; col integer; l real; t real; r real; b real; ow integer; oh integer; count_rows integer:=0;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then raise exception 'Device not authorized' using errcode='42501'; end if;
  select role into role_name from public.nurva_group_members where group_id=p_group and user_id=auth.uid() and removed_at is null;
  if coalesce(role_name,'') not in ('owner','admin','editor') then raise exception 'Cannot edit mosaic layout' using errcode='42501'; end if;
  if not exists(select 1 from public.nurva_wallpaper_revisions where group_id=p_group and revision=p_revision) then raise exception 'Revision not found' using errcode='22023'; end if;
  if p_tiles is null or jsonb_typeof(p_tiles)<>'array' or jsonb_array_length(p_tiles) not between 1 and 100 then raise exception 'Invalid tile list' using errcode='22023'; end if;
  delete from public.nurva_mosaic_tiles where group_id=p_group and revision=p_revision;
  for item in select value from jsonb_array_elements(p_tiles) loop
    begin
      did:=(item->>'deviceId')::uuid; idx:=(item->>'tileIndex')::integer; rw:=(item->>'rowIndex')::integer; col:=(item->>'columnIndex')::integer;
      l:=(item->>'cropLeft')::real; t:=(item->>'cropTop')::real; r:=(item->>'cropRight')::real; b:=(item->>'cropBottom')::real;
      ow:=(item->>'outputWidth')::integer; oh:=(item->>'outputHeight')::integer;
    exception when others then raise exception 'Malformed mosaic tile' using errcode='22023'; end;
    if not exists(select 1 from public.nurva_devices where id=did and group_id=p_group and revoked_at is null) then raise exception 'Tile references inactive device' using errcode='42501'; end if;
    if idx<0 or rw<0 or col<0 or l<0 or t<0 or r>1 or b>1 or l>=r or t>=b or ow not between 1 and 10000 or oh not between 1 and 10000 then raise exception 'Invalid tile bounds or size' using errcode='22023'; end if;
    insert into public.nurva_mosaic_tiles(group_id,revision,device_id,tile_index,row_index,column_index,crop_left,crop_top,crop_right,crop_bottom,output_width,output_height)
      values(p_group,p_revision,did,idx,rw,col,l,t,r,b,ow,oh);
    count_rows:=count_rows+1;
  end loop;
  return count_rows;
end $$;
revoke all on function public.nurva_save_mosaic_tiles(uuid,uuid,text,bigint,jsonb) from public,anon;
grant execute on function public.nurva_save_mosaic_tiles(uuid,uuid,text,bigint,jsonb) to authenticated;

create or replace function public.nurva_revoke_invite(p_group uuid,p_invite uuid,p_device uuid,p_secret text)
returns boolean language plpgsql security definer set search_path=''
as $$
declare role_name text; n integer;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then raise exception 'Device not authorized' using errcode='42501'; end if;
  select role into role_name from public.nurva_group_members where group_id=p_group and user_id=auth.uid() and removed_at is null;
  if coalesce(role_name,'') not in ('owner','admin') then raise exception 'Cannot revoke invitations' using errcode='42501'; end if;
  update public.nurva_invites set revoked_at=now() where id=p_invite and group_id=p_group and revoked_at is null;
  get diagnostics n=row_count; return n=1;
end $$;
revoke all on function public.nurva_revoke_invite(uuid,uuid,uuid,text) from public,anon;
grant execute on function public.nurva_revoke_invite(uuid,uuid,uuid,text) to authenticated;


-- Edge Functions use the service-role key only on the server side. Mobile clients never get
-- direct grants; RLS remains enabled on every application table.
grant select,insert,update,delete on
  public.nurva_groups, public.nurva_group_members, public.nurva_devices, public.nurva_invites,
  public.nurva_pending_uploads, public.nurva_wallpaper_revisions, public.nurva_mosaic_tiles,
  public.nurva_themes, public.nurva_theme_items, public.nurva_schedules
to service_role;
