-- Let an authenticated, active device acknowledge only revisions in its own group.
-- The device secret is validated by the same helper used by all group-sync RPCs.
create or replace function public.nurva_mark_revision_applied(
  p_group uuid,
  p_device uuid,
  p_secret text,
  p_revision bigint
)
returns boolean
language plpgsql
security definer
set search_path=''
as $$
declare
  affected integer;
begin
  if not public.nurva_assert_active_device(p_group,p_device,p_secret) then
    raise exception 'Device not authorized' using errcode='42501';
  end if;
  if p_revision is null or p_revision < 1 then
    raise exception 'Invalid wallpaper revision' using errcode='22023';
  end if;
  if not exists (
    select 1 from public.nurva_wallpaper_revisions
    where group_id=p_group and revision=p_revision
  ) then
    raise exception 'Wallpaper revision not found in this group' using errcode='22023';
  end if;

  update public.nurva_devices
    set last_applied_revision=greatest(last_applied_revision,p_revision),
        last_seen_at=now()
    where id=p_device and group_id=p_group and user_id=auth.uid() and revoked_at is null;
  get diagnostics affected=row_count;
  return affected=1;
end;
$$;

revoke all on function public.nurva_mark_revision_applied(uuid,uuid,text,bigint) from public,anon;
grant execute on function public.nurva_mark_revision_applied(uuid,uuid,text,bigint) to authenticated;
