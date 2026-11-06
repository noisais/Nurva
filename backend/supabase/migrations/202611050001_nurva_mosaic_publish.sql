-- Atomically publish a wallpaper revision together with its panorama tile assignments.
-- The mobile client only supplies the tile plan; this RPC remains service-role-only and
-- the Edge Function verifies the uploaded bytes and editor permissions before calling it.
create or replace function public.nurva_publish_wallpaper_revision_mosaic(
  p_group uuid,
  p_device uuid,
  p_user uuid,
  p_asset uuid,
  p_hash text,
  p_mime text,
  p_size bigint,
  p_width integer,
  p_height integer,
  p_title text,
  p_tiles jsonb
)
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
  role_name text;
  upload public.nurva_pending_uploads%rowtype;
  rev bigint;
  rid uuid;
  item jsonb;
  device_id uuid;
  tile_index integer;
  row_index integer;
  column_index integer;
  crop_left real;
  crop_top real;
  crop_right real;
  crop_bottom real;
  output_width integer;
  output_height integer;
  tile_count integer;
  active_device_count integer;
  expected_left real;
  expected_right real;
begin
  select role into role_name
    from public.nurva_group_members
    where group_id=p_group and user_id=p_user and removed_at is null;
  if coalesce(role_name,'') not in ('owner','admin','editor') then
    raise exception 'Cannot publish in this group' using errcode='42501';
  end if;
  if not exists (
    select 1 from public.nurva_devices
    where id=p_device and group_id=p_group and user_id=p_user and revoked_at is null
  ) then
    raise exception 'Device is revoked or invalid' using errcode='42501';
  end if;
  select * into upload
    from public.nurva_pending_uploads
    where asset_id=p_asset and group_id=p_group and user_id=p_user and device_id=p_device
      and expires_at>now() and consumed_at is null
    for update;
  if not found then raise exception 'Pending upload invalid, expired or consumed' using errcode='22023'; end if;
  if p_hash is null or p_hash !~ '^[0-9a-f]{64}$' or p_mime is null
      or p_mime not in ('image/jpeg','image/png','image/webp') then
    raise exception 'Invalid image metadata' using errcode='22023';
  end if;
  if p_size not between 1 and 52428800 or p_width not between 1 and 30000
      or p_height not between 1 and 30000 or p_width::bigint*p_height::bigint>60000000 then
    raise exception 'Image exceeds allowed limits' using errcode='22023';
  end if;
  if not exists(select 1 from storage.objects where bucket_id='nurva-wallpapers' and name=upload.storage_path) then
    raise exception 'Object missing' using errcode='22023';
  end if;
  if p_tiles is null or jsonb_typeof(p_tiles)<>'array' then
    raise exception 'Mosaic tile plan must be a JSON array' using errcode='22023';
  end if;
  tile_count:=jsonb_array_length(p_tiles);
  if tile_count not between 2 and 100 then
    raise exception 'A panorama requires 2 to 100 tiles' using errcode='22023';
  end if;
  select count(*) into active_device_count
    from public.nurva_devices d
    join public.nurva_group_members m on m.group_id=d.group_id and m.user_id=d.user_id
    where d.group_id=p_group and d.revoked_at is null and m.removed_at is null;
  if tile_count<>active_device_count then
    raise exception 'Mosaic tile count must match every active group device' using errcode='22023';
  end if;

  -- Consume the upload and create the revision before inserting its FK-dependent tile rows.
  update public.nurva_pending_uploads set consumed_at=now() where asset_id=p_asset;
  update public.nurva_groups set current_revision=current_revision+1,updated_at=now()
    where id=p_group returning current_revision into rev;
  insert into public.nurva_wallpaper_revisions(
    group_id,revision,created_by,storage_path,sha256,mime_type,byte_size,image_width,image_height,title
  ) values (
    p_group,rev,p_user,upload.storage_path,p_hash,p_mime,p_size,p_width,p_height,nullif(trim(p_title),'')
  ) returning id into rid;

  for item in select value from jsonb_array_elements(p_tiles) loop
    begin
      device_id:=(item->>'deviceId')::uuid;
      tile_index:=(item->>'tileIndex')::integer;
      row_index:=(item->>'rowIndex')::integer;
      column_index:=(item->>'columnIndex')::integer;
      crop_left:=(item->>'cropLeft')::real;
      crop_top:=(item->>'cropTop')::real;
      crop_right:=(item->>'cropRight')::real;
      crop_bottom:=(item->>'cropBottom')::real;
      output_width:=(item->>'outputWidth')::integer;
      output_height:=(item->>'outputHeight')::integer;
    exception when others then
      raise exception 'Malformed mosaic tile' using errcode='22023';
    end;
    if tile_index<0 or tile_index>=tile_count or row_index<>0 or column_index<>tile_index
        or output_width not between 1 and 10000 or output_height not between 1 and 10000 then
      raise exception 'Invalid panorama tile index or output size' using errcode='22023';
    end if;
    expected_left:=tile_index::real/tile_count;
    expected_right:=(tile_index+1)::real/tile_count;
    if crop_left is null or crop_top is null or crop_right is null or crop_bottom is null
        or abs(crop_left-expected_left)>0.0001 or crop_top<>0
        or abs(crop_right-expected_right)>0.0001 or crop_bottom<>1 then
      raise exception 'Panorama tiles must be contiguous equal-width slices' using errcode='22023';
    end if;
    if not exists (
      select 1 from public.nurva_devices d
      join public.nurva_group_members m on m.group_id=d.group_id and m.user_id=d.user_id
      where d.id=device_id and d.group_id=p_group and d.revoked_at is null and m.removed_at is null
    ) then
      raise exception 'Tile references an inactive device' using errcode='42501';
    end if;
    insert into public.nurva_mosaic_tiles(
      group_id,revision,device_id,tile_index,row_index,column_index,
      crop_left,crop_top,crop_right,crop_bottom,output_width,output_height
    ) values (
      p_group,rev,device_id,tile_index,row_index,column_index,
      crop_left,crop_top,crop_right,crop_bottom,output_width,output_height
    );
  end loop;

  return jsonb_build_object('revisionId',rid,'revision',rev,'mosaic',true,'tileCount',tile_count);
end;
$$;

revoke all on function public.nurva_publish_wallpaper_revision_mosaic(
  uuid,uuid,uuid,uuid,text,text,bigint,integer,integer,text,jsonb
) from public,anon,authenticated;
grant execute on function public.nurva_publish_wallpaper_revision_mosaic(
  uuid,uuid,uuid,uuid,text,text,bigint,integer,integer,text,jsonb
) to service_role;
