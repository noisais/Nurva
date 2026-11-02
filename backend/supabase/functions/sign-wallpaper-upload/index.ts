import { authorizeDevice, errorResponse, getContext, HttpError, isUuid, jsonResponse, readJson, requirePost } from "../_shared/nurva.ts";
const allowed = new Set(["image/jpeg","image/png","image/webp"]);
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok",{headers:{"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, apikey, content-type, x-client-info","Access-Control-Allow-Methods":"POST, OPTIONS"}});
  try {
    requirePost(req); const body=await readJson(req);
    const {user,admin}=await getContext(req);
    const {groupId,deviceId,deviceSecret,assetId,mimeType}=body;
    if (!isUuid(groupId)||!isUuid(deviceId)||!isUuid(assetId)||typeof mimeType!=="string"||!allowed.has(mimeType)) throw new HttpError("Invalid upload request",400);
    await authorizeDevice(admin,user.id,groupId,deviceId,typeof deviceSecret==="string"?deviceSecret:"",["owner","admin","editor"]);
    const path=`${groupId}/${assetId}`;
    const {data,error}=await admin.storage.from("nurva-wallpapers").createSignedUploadUrl(path,{upsert:false});
    if(error||!data) throw new HttpError("Could not authorize upload",502);
    const {error:pendingError}=await admin.from("nurva_pending_uploads").insert({asset_id:assetId,group_id:groupId,user_id:user.id,device_id:deviceId,storage_path:path,expires_at:new Date(Date.now()+2*60*60*1000).toISOString()});
    if(pendingError){ await admin.storage.from("nurva-wallpapers").remove([path]); throw new HttpError("Could not register upload",502); }
    return jsonResponse({bucket:"nurva-wallpapers",path,token:data.token,signedUrl:data.signedUrl,mimeType});
  } catch(e) { return errorResponse(e); }
});
