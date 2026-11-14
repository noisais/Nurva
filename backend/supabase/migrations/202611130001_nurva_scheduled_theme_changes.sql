-- Weekly shared-theme activation. The server owns next_run_at and advances due
-- schedules transactionally so multiple devices cannot execute the same occurrence twice.

create or replace function public.nurva_next_schedule_at(
  p_days integer,
  p_hour integer,
  p_minute integer,
  p_zone text,
  p_after timestamptz
)
returns timestamptz
language plpgsql
stable
security definer
set search_path=''
as $$
declare
  local_after timestamp;
  candidate_date date;
  candidate_local timestamp;
  candidate_at timestamptz;
  day_offset integer;
  weekday_bit integer;
begin
  if p_days is null or p_days not between 1 and 127
      or p_hour is null or p_hour not between 0 and 23
      or p_minute is null or p_minute not between 0 and 59
      or p_zone is null
      or not exists(select 1 from pg_catalog.pg_timezone_names where name=trim(p_zone))
      or p_after is null then
    raise exception 'Invalid schedule calculation input' using errcode='22023';
  end if;

  local_after := p_after at time zone p_zone;
  for day_offset in 0..7 loop
    candidate_date := local_after::date + day_offset;
    weekday_bit := 1 << (extract(isodow from candidate_date)::integer - 1);
    if (p_days & weekday_bit) <> 0 then
      candidate_local := candidate_date::timestamp + make_interval(hours => p_hour, mins => p_minute);
      candidate_at := candidate_local at time zone p_zone;
      if candidate_at > p_after then
        return candidate_at;
      end if;
    end if;
  end loop;
  return null;
end;
$$;
revoke all on function public.nurva_next_schedule_at(integer,integer,integer,text,timestamptz) from public,anon,authenticated;

create or replace function public.nurva_save_schedule(
  p_group uuid,p_device uuid,p_secret text,p_schedule uuid,p_theme uuid,
  p_days integer,p_hour integer,p_minute integer,p_zone text,p_enabled boolean,p_approval boolean
)
returns uuid
language plpgsql
security definer
set search_path=''
as $$
declare
  u uuid:=auth.uid();
  role_name text;
  sid uuid:=coalesce(p_schedule,extensions.gen_random_uuid());
  next_at timestamptz;
  affected integer;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then
    raise exception 'Device not authorized' using errcode='42501';
  end if;
  select role into role_name from public.nurva_group_members
    where group_id=p_group and user_id=u and removed_at is null;
  if coalesce(role_name,'') not in ('owner','admin','editor') then
    raise exception 'Cannot edit schedules' using errcode='42501';
  end if;
  if p_days is null or p_days not between 1 and 127
      or p_hour is null or p_hour not between 0 and 23
      or p_minute is null or p_minute not between 0 and 59 then
    raise exception 'Invalid schedule time' using errcode='22023';
  end if;
  if p_zone is null or not exists(select 1 from pg_catalog.pg_timezone_names where name=trim(p_zone)) then
    raise exception 'Unknown IANA time zone' using errcode='22023';
  end if;
  if p_theme is not null and not exists(
    select 1 from public.nurva_themes where id=p_theme and group_id=p_group and layout_mode='shared'
  ) then
    raise exception 'Theme belongs to another group or is not a shared theme' using errcode='42501';
  end if;
  if coalesce(p_enabled,true) and exists(
    select 1 from public.nurva_schedules s
    where s.group_id=p_group and s.id<>sid and s.enabled=true
      and s.time_zone_id=trim(p_zone)
      and s.local_hour=p_hour and s.local_minute=p_minute
      and (s.days_of_week_mask & p_days)<>0
  ) then
    raise exception 'Another enabled schedule overlaps this local time' using errcode='22023';
  end if;

  next_at := case when coalesce(p_enabled,true)
    then public.nurva_next_schedule_at(p_days,p_hour,p_minute,trim(p_zone),now())
    else null end;

  insert into public.nurva_schedules(
    id,group_id,theme_id,days_of_week_mask,local_hour,local_minute,
    time_zone_id,enabled,require_approval,next_run_at,updated_at
  ) values (
    sid,p_group,p_theme,p_days,p_hour,p_minute,trim(p_zone),
    coalesce(p_enabled,true),coalesce(p_approval,false),next_at,now()
  )
  on conflict(id) do update set
    theme_id=excluded.theme_id,
    days_of_week_mask=excluded.days_of_week_mask,
    local_hour=excluded.local_hour,
    local_minute=excluded.local_minute,
    time_zone_id=excluded.time_zone_id,
    enabled=excluded.enabled,
    require_approval=excluded.require_approval,
    next_run_at=excluded.next_run_at,
    updated_at=now()
  where public.nurva_schedules.group_id=excluded.group_id;
  get diagnostics affected=row_count;
  if affected<>1 then raise exception 'Schedule does not belong to group' using errcode='22023'; end if;
  return sid;
end;
$$;
revoke all on function public.nurva_save_schedule(uuid,uuid,text,uuid,uuid,integer,integer,integer,text,boolean,boolean) from public,anon;
grant execute on function public.nurva_save_schedule(uuid,uuid,text,uuid,uuid,integer,integer,integer,text,boolean,boolean) to authenticated;

create or replace function public.nurva_process_due_theme_schedules(p_group uuid,p_device uuid,p_secret text)
returns integer
language plpgsql
security definer
set search_path=''
as $$
declare
  due_schedule public.nurva_schedules%rowtype;
  next_at timestamptz;
  processed integer:=0;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then
    raise exception 'Device not authorized' using errcode='42501';
  end if;

  for due_schedule in
    select s.* from public.nurva_schedules s
    where s.group_id=p_group
      and s.enabled=true
      and s.require_approval=false
      and s.theme_id is not null
      and s.next_run_at is not null
      and s.next_run_at<=now()
    order by s.next_run_at,s.updated_at,s.id
    for update skip locked
  loop
    if exists(select 1 from public.nurva_themes t where t.id=due_schedule.theme_id and t.group_id=p_group and t.layout_mode='shared') then
      update public.nurva_groups
        set current_theme_id=due_schedule.theme_id,updated_at=now()
        where id=p_group;
      processed:=processed+1;
    end if;

    next_at := public.nurva_next_schedule_at(
      due_schedule.days_of_week_mask,
      due_schedule.local_hour,
      due_schedule.local_minute,
      due_schedule.time_zone_id,
      now()
    );
    update public.nurva_schedules
      set next_run_at=next_at,updated_at=now()
      where id=due_schedule.id and group_id=p_group;
  end loop;
  return processed;
end;
$$;
revoke all on function public.nurva_process_due_theme_schedules(uuid,uuid,text) from public,anon;
grant execute on function public.nurva_process_due_theme_schedules(uuid,uuid,text) to authenticated;

-- Extend snapshots with full theme-item asset metadata and schedule execution timestamps.
create or replace function public.nurva_get_sync_state(p_group uuid,p_device uuid,p_secret text)
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare state jsonb;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then
    raise exception 'Device not authorized' using errcode='42501';
  end if;
  update public.nurva_devices
    set last_seen_at=now()
    where id=p_device and group_id=p_group and revoked_at is null;

  select jsonb_build_object(
    'group',jsonb_build_object(
      'id',g.id,'displayName',g.display_name,'ownerUserId',g.owner_user_id,
      'currentRevision',g.current_revision,'currentThemeId',g.current_theme_id,'updatedAt',g.updated_at
    ),
    'members',(select coalesce(jsonb_agg(jsonb_build_object(
      'userId',m.user_id,'role',m.role,'joinedAt',m.joined_at
    )),'[]'::jsonb) from public.nurva_group_members m
      where m.group_id=g.id and m.removed_at is null),
    'devices',(select coalesce(jsonb_agg(jsonb_build_object(
      'id',d.id,'userId',d.user_id,'role',m.role,'displayName',d.display_name,
      'wallpaperTarget',d.wallpaper_target,'applyMode',d.apply_mode,
      'lastAppliedRevision',d.last_applied_revision,'lastSeenAt',d.last_seen_at
    ) order by d.created_at),'[]'::jsonb)
      from public.nurva_devices d
      join public.nurva_group_members m on m.group_id=d.group_id and m.user_id=d.user_id and m.removed_at is null
      where d.group_id=g.id and d.revoked_at is null),
    'currentWallpaper',(select jsonb_build_object(
      'revisionId',r.id,'revision',r.revision,'storagePath',r.storage_path,'sha256',r.sha256,
      'mimeType',r.mime_type,'byteSize',r.byte_size,'imageWidth',r.image_width,'imageHeight',r.image_height,'title',r.title
    ) from public.nurva_wallpaper_revisions r where r.group_id=g.id and r.revision=g.current_revision),
    'mosaicTiles',(select coalesce(jsonb_agg(jsonb_build_object(
      'deviceId',mt.device_id,'tileIndex',mt.tile_index,'rowIndex',mt.row_index,'columnIndex',mt.column_index,
      'cropLeft',mt.crop_left,'cropTop',mt.crop_top,'cropRight',mt.crop_right,'cropBottom',mt.crop_bottom,
      'outputWidth',mt.output_width,'outputHeight',mt.output_height
    ) order by mt.tile_index),'[]'::jsonb)
      from public.nurva_mosaic_tiles mt where mt.group_id=g.id and mt.revision=g.current_revision),
    'themes',(select coalesce(jsonb_agg(jsonb_build_object(
      'id',t.id,'name',t.name,'layoutMode',t.layout_mode,'description',t.description,
      'items',(select coalesce(jsonb_agg(jsonb_build_object(
        'position',ti.position,'revisionId',r.id,'revision',r.revision,
        'storagePath',r.storage_path,'sha256',r.sha256,'mimeType',r.mime_type,
        'byteSize',r.byte_size,'imageWidth',r.image_width,'imageHeight',r.image_height,
        'createdAt',r.created_at,'deviceId',ti.device_id
      ) order by ti.position),'[]'::jsonb)
        from public.nurva_theme_items ti
        join public.nurva_wallpaper_revisions r on r.id=ti.wallpaper_revision_id
        where ti.theme_id=t.id)
    )),'[]'::jsonb) from public.nurva_themes t where t.group_id=g.id),
    'schedules',(select coalesce(jsonb_agg(jsonb_build_object(
      'id',s.id,'themeId',s.theme_id,'daysOfWeekMask',s.days_of_week_mask,
      'localHour',s.local_hour,'localMinute',s.local_minute,'timeZoneId',s.time_zone_id,
      'enabled',s.enabled,'requireApproval',s.require_approval,
      'nextRunAt',s.next_run_at,'updatedAt',s.updated_at
    ) order by s.local_hour,s.local_minute),'[]'::jsonb)
      from public.nurva_schedules s where s.group_id=g.id)
  ) into state
  from public.nurva_groups g
  where g.id=p_group;

  if state is null then raise exception 'Group not found' using errcode='22023'; end if;
  return state;
end;
$$;
revoke all on function public.nurva_get_sync_state(uuid,uuid,text) from public,anon;
grant execute on function public.nurva_get_sync_state(uuid,uuid,text) to authenticated;
