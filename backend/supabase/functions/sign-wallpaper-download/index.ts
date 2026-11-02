import { authorizeDevice, errorResponse, getContext, HttpError, isUuid, jsonResponse, readJson, requirePost } from "../_shared/nurva.ts";
Deno.serve(async (req) => {
  if(req.method==="OPTIONS") return new Response("ok",{headers:{"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, apikey, content-type, x-client-info","Access-Control-Allow-Methods":"POST, OPTIONS"}});
  try {
    requirePost(req); const body=await readJson(req); const {user,admin}=await getContext(req);
    const {groupId,deviceId,deviceSecret,revision}=body;
    if(!isUuid(groupId)||!isUuid(deviceId)||typeof revision!=="number"||!Number.isSafeInteger(revision)||revision<1) throw new HttpError("Invalid download request",400);
    await authorizeDevice(admin,user.id,groupId,deviceId,typeof deviceSecret==="string"?deviceSecret:"");
    const {data:wallpaper,error}=await admin.from("nurva_wallpaper_revisions").select("storage_path,revision,sha256,mime_type").eq("group_id",groupId).eq("revision",revision).maybeSingle();
    if(error||!wallpaper) throw new HttpError("Wallpaper revision not found",404);
    const {data:signed,error:signedError}=await admin.storage.from("nurva-wallpapers").createSignedUrl(wallpaper.storage_path,60,{download:true});
    if(signedError||!signed) throw new HttpError("Could not authorize download",502);
    return jsonResponse({url:signed.signedUrl,expiresInSeconds:60,revision:wallpaper.revision,sha256:wallpaper.sha256,mimeType:wallpaper.mime_type});
  } catch(e) { return errorResponse(e); }
});
