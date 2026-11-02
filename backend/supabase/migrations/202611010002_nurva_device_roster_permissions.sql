-- Include the owning user and effective group role in each active device row so the
-- Android client can render a useful, permission-aware device roster.
create or replace function public.nurva_get_sync_state(p_group uuid,p_device uuid,p_secret text)
returns jsonb language plpgsql security definer set search_path=''
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
    'group',jsonb_build_object('id',g.id,'displayName',g.display_name,'ownerUserId',g.owner_user_id,'currentRevision',g.current_revision,'currentThemeId',g.current_theme_id,'updatedAt',g.updated_at),
    'members',(select coalesce(jsonb_agg(jsonb_build_object('userId',m.user_id,'role',m.role,'joinedAt',m.joined_at)),'[]'::jsonb)
      from public.nurva_group_members m where m.group_id=g.id and m.removed_at is null),
    'devices',(select coalesce(jsonb_agg(jsonb_build_object(
      'id',d.id,'userId',d.user_id,'role',m.role,'displayName',d.display_name,
      'wallpaperTarget',d.wallpaper_target,'applyMode',d.apply_mode,
      'lastAppliedRevision',d.last_applied_revision,'lastSeenAt',d.last_seen_at
    ) order by d.created_at),'[]'::jsonb)
      from public.nurva_devices d
      join public.nurva_group_members m on m.group_id=d.group_id and m.user_id=d.user_id and m.removed_at is null
      where d.group_id=g.id and d.revoked_at is null),
    'currentWallpaper',(select jsonb_build_object('revisionId',r.id,'revision',r.revision,'storagePath',r.storage_path,'sha256',r.sha256,'mimeType',r.mime_type,'byteSize',r.byte_size,'imageWidth',r.image_width,'imageHeight',r.image_height,'title',r.title)
      from public.nurva_wallpaper_revisions r where r.group_id=g.id and r.revision=g.current_revision),
    'mosaicTiles',(select coalesce(jsonb_agg(jsonb_build_object('deviceId',mt.device_id,'tileIndex',mt.tile_index,'rowIndex',mt.row_index,'columnIndex',mt.column_index,'cropLeft',mt.crop_left,'cropTop',mt.crop_top,'cropRight',mt.crop_right,'cropBottom',mt.crop_bottom,'outputWidth',mt.output_width,'outputHeight',mt.output_height) order by mt.tile_index),'[]'::jsonb)
      from public.nurva_mosaic_tiles mt where mt.group_id=g.id and mt.revision=g.current_revision),
    'themes',(select coalesce(jsonb_agg(jsonb_build_object('id',t.id,'name',t.name,'layoutMode',t.layout_mode,'description',t.description,'items',(
      select coalesce(jsonb_agg(jsonb_build_object('position',ti.position,'revisionId',r.id,'revision',r.revision,'storagePath',r.storage_path,'sha256',r.sha256,'deviceId',ti.device_id) order by ti.position),'[]'::jsonb)
      from public.nurva_theme_items ti join public.nurva_wallpaper_revisions r on r.id=ti.wallpaper_revision_id where ti.theme_id=t.id
    ))),'[]'::jsonb) from public.nurva_themes t where t.group_id=g.id),
    'schedules',(select coalesce(jsonb_agg(jsonb_build_object('id',s.id,'themeId',s.theme_id,'daysOfWeekMask',s.days_of_week_mask,'localHour',s.local_hour,'localMinute',s.local_minute,'timeZoneId',s.time_zone_id,'enabled',s.enabled,'requireApproval',s.require_approval)),'[]'::jsonb)
      from public.nurva_schedules s where s.group_id=g.id)
  ) into state
  from public.nurva_groups g
  where g.id=p_group;

  if state is null then raise exception 'Group not found' using errcode='22023'; end if;
  return state;
end $$;

revoke all on function public.nurva_get_sync_state(uuid,uuid,text) from public,anon;
grant execute on function public.nurva_get_sync_state(uuid,uuid,text) to authenticated;
