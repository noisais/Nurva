DO $test$
DECLARE
  owner_id uuid := '10000000-0000-4000-8000-000000000001';
  member_id uuid := '10000000-0000-4000-8000-000000000002';
  owner_device uuid := '20000000-0000-4000-8000-000000000001';
  member_device uuid := '20000000-0000-4000-8000-000000000002';
  asset_id uuid := '30000000-0000-4000-8000-000000000001';
  mosaic_asset_id uuid := '30000000-0000-4000-8000-000000000002';
  group_id uuid;
  token text;
  published jsonb;
  mosaic_published jsonb;
  revision_id uuid;
  theme_id uuid;
  schedule_id uuid;
  sync_state jsonb;
  denied boolean := false;
BEGIN
  INSERT INTO auth.users(id) VALUES(owner_id),(member_id);
  PERFORM set_config('request.jwt.claim.sub',owner_id::text,false);
  group_id := public.nurva_create_group('SQL smoke test',owner_device,repeat('a',64),'Owner phone');

  INSERT INTO storage.objects(bucket_id,name) VALUES('nurva-wallpapers',group_id::text||'/'||asset_id::text);
  INSERT INTO public.nurva_pending_uploads(asset_id,group_id,user_id,device_id,storage_path,expires_at)
    VALUES(asset_id,group_id,owner_id,owner_device,group_id::text||'/'||asset_id::text,now()+interval '2 hours');
  published := public.nurva_publish_wallpaper_revision(group_id,owner_device,owner_id,asset_id,repeat('b',64),'image/png',24,4,4,'Test image');
  IF (published->>'revision')::bigint<>1 THEN RAISE EXCEPTION 'Revision should start at 1'; END IF;
  revision_id := (published->>'revisionId')::uuid;

  SELECT invite_token INTO token FROM public.nurva_create_invite(group_id,owner_device,repeat('a',64),'editor',now()+interval '1 hour',1);
  IF token IS NULL OR length(token)<32 THEN RAISE EXCEPTION 'Invite token not generated'; END IF;
  IF EXISTS(SELECT 1 FROM public.nurva_invites WHERE token_hash=token) THEN RAISE EXCEPTION 'Raw invite token stored'; END IF;

  PERFORM set_config('request.jwt.claim.sub',member_id::text,false);
  PERFORM * FROM public.nurva_join_with_invite(token,member_device,repeat('c',64),'Member phone','both','manual');
  IF NOT public.nurva_update_device_preferences(group_id,member_device,repeat('c',64),'both','manual') THEN RAISE EXCEPTION 'Device preference update failed'; END IF;
  IF NOT public.nurva_mark_revision_applied(group_id,member_device,repeat('c',64),1) THEN RAISE EXCEPTION 'Applied-revision acknowledgement failed'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.nurva_devices WHERE id=member_device AND last_applied_revision=1) THEN RAISE EXCEPTION 'Device applied revision was not persisted'; END IF;

  PERFORM set_config('request.jwt.claim.sub',owner_id::text,false);
  INSERT INTO storage.objects(bucket_id,name) VALUES('nurva-wallpapers',group_id::text||'/'||mosaic_asset_id::text);
  INSERT INTO public.nurva_pending_uploads(asset_id,group_id,user_id,device_id,storage_path,expires_at)
    VALUES(mosaic_asset_id,group_id,owner_id,owner_device,group_id::text||'/'||mosaic_asset_id::text,now()+interval '2 hours');
  mosaic_published := public.nurva_publish_wallpaper_revision_mosaic(
    group_id,owner_device,owner_id,mosaic_asset_id,repeat('d',64),'image/png',24,8,4,'Panorama test',
    jsonb_build_array(
      jsonb_build_object('deviceId',owner_device::text,'tileIndex',0,'rowIndex',0,'columnIndex',0,
        'cropLeft',0,'cropTop',0,'cropRight',0.5,'cropBottom',1,'outputWidth',1080,'outputHeight',2400),
      jsonb_build_object('deviceId',member_device::text,'tileIndex',1,'rowIndex',0,'columnIndex',1,
        'cropLeft',0.5,'cropTop',0,'cropRight',1,'cropBottom',1,'outputWidth',1440,'outputHeight',3200)
    )
  );
  IF (mosaic_published->>'revision')::bigint<>2 OR mosaic_published->>'mosaic'<>'true' THEN
    RAISE EXCEPTION 'Atomic mosaic revision was not published';
  END IF;
  IF (SELECT count(*) FROM public.nurva_mosaic_tiles mt WHERE mt.group_id IN (SELECT g.id FROM public.nurva_groups g WHERE g.owner_user_id=owner_id AND g.display_name='SQL smoke test') AND mt.revision=2)<>2 THEN
    RAISE EXCEPTION 'Atomic mosaic tile assignments were not stored';
  END IF;

  sync_state := public.nurva_get_sync_state(group_id,owner_device,repeat('a',64));
  IF jsonb_array_length(sync_state->'mosaicTiles')<>2 THEN RAISE EXCEPTION 'Mosaic tiles missing from sync state'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.nurva_devices WHERE id=owner_device AND last_seen_at IS NOT NULL) THEN
    RAISE EXCEPTION 'Sync-state refresh did not update the device last-seen timestamp';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM jsonb_array_elements(sync_state->'devices') AS device(item)
    WHERE item->>'id'=owner_device::text AND item->>'userId'=owner_id::text AND item->>'role'='owner'
  ) THEN RAISE EXCEPTION 'Device roster did not include owner identity and role'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM jsonb_array_elements(sync_state->'devices') AS device(item)
    WHERE item->>'id'=member_device::text AND item->>'userId'=member_id::text AND item->>'role'='editor'
  ) THEN RAISE EXCEPTION 'Device roster did not include invited member role'; END IF;

  theme_id := public.nurva_save_theme(group_id,owner_device,repeat('a',64),null,'Test theme','shared',jsonb_build_array(jsonb_build_object('revisionId',revision_id::text,'deviceId',null)),true);
  schedule_id := public.nurva_save_schedule(group_id,owner_device,repeat('a',64),null,theme_id,127,9,0,'UTC',true,false);
  IF schedule_id IS NULL THEN RAISE EXCEPTION 'Schedule not created'; END IF;
  PERFORM public.nurva_save_mosaic_tiles(group_id,owner_device,repeat('a',64),1,jsonb_build_array(jsonb_build_object(
    'deviceId',owner_device::text,'tileIndex',0,'rowIndex',0,'columnIndex',0,
    'cropLeft',0,'cropTop',0,'cropRight',1,'cropBottom',1,'outputWidth',1080,'outputHeight',2400
  )));
  IF NOT public.nurva_remove_member(group_id,member_id,owner_device,repeat('a',64)) THEN RAISE EXCEPTION 'Member removal failed'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.nurva_devices WHERE id=member_device AND revoked_at IS NOT NULL) THEN RAISE EXCEPTION 'Removed member device not revoked'; END IF;

  PERFORM set_config('request.jwt.claim.sub',member_id::text,false);
  BEGIN
    PERFORM public.nurva_get_sync_state(group_id,member_device,repeat('c',64));
  EXCEPTION WHEN insufficient_privilege THEN denied:=true;
  END;
  IF NOT denied THEN RAISE EXCEPTION 'Removed member read group state'; END IF;

  PERFORM set_config('request.jwt.claim.sub',owner_id::text,false);
  denied:=false;
  BEGIN
    PERFORM public.nurva_publish_wallpaper_revision(group_id,owner_device,owner_id,asset_id,repeat('b',64),'image/png',24,4,4,'duplicate');
  EXCEPTION WHEN invalid_parameter_value THEN denied:=true;
  END;
  IF NOT denied THEN RAISE EXCEPTION 'Single-use upload could be published twice'; END IF;
END $test$;
