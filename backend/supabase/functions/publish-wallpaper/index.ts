import { authorizeDevice, errorResponse, getContext, HttpError, isUuid, jsonResponse, readJson, requirePost } from "../_shared/nurva.ts";
import { inspectImage } from "../_shared/imageInspection.ts";

const MAX_BYTES=50*1024*1024, MAX_PIXELS=60_000_000, MAX_DIMENSION=30_000;
Deno.serve(async (req) => {
  if(req.method==="OPTIONS") return new Response("ok",{headers:{"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, apikey, content-type, x-client-info","Access-Control-Allow-Methods":"POST, OPTIONS"}});
  try {
    requirePost(req); const body=await readJson(req); const {user,admin}=await getContext(req);
    const {groupId,deviceId,deviceSecret,assetId,mimeType,title,mosaicTiles}=body;
    if(!isUuid(groupId)||!isUuid(deviceId)||!isUuid(assetId)||typeof mimeType!=="string"||!["image/jpeg","image/png","image/webp"].includes(mimeType)) throw new HttpError("Invalid publish request",400);
    if(title!==undefined && title!==null && (typeof title!=="string"||title.length>120)) throw new HttpError("Title must be at most 120 characters",400);
    if(mosaicTiles!==undefined) {
      if(!Array.isArray(mosaicTiles)||mosaicTiles.length<2||mosaicTiles.length>100) throw new HttpError("A panorama needs 2 to 100 device tiles",400);
      const seenDevices=new Set<string>();
      const count=mosaicTiles.length;
      for(let index=0;index<count;index++) {
        const tile=mosaicTiles[index];
        if(!tile||!isUuid(tile.deviceId)||seenDevices.has(tile.deviceId)) throw new HttpError("Mosaic contains an invalid or duplicate device",400);
        seenDevices.add(tile.deviceId);
        const expectedLeft=index/count, expectedRight=(index+1)/count;
        if(tile.tileIndex!==index||tile.rowIndex!==0||tile.columnIndex!==index||
          typeof tile.cropLeft!=="number"||typeof tile.cropTop!=="number"||
          typeof tile.cropRight!=="number"||typeof tile.cropBottom!=="number"||
          Math.abs(tile.cropLeft-expectedLeft)>0.0001||tile.cropTop!==0||
          Math.abs(tile.cropRight-expectedRight)>0.0001||tile.cropBottom!==1||
          !Number.isInteger(tile.outputWidth)||tile.outputWidth<1||tile.outputWidth>10000||
          !Number.isInteger(tile.outputHeight)||tile.outputHeight<1||tile.outputHeight>10000) {
          throw new HttpError("Mosaic tiles must be contiguous equal-width panorama slices",400);
        }
      }
    }
    await authorizeDevice(admin,user.id,groupId,deviceId,typeof deviceSecret==="string"?deviceSecret:"",["owner","admin","editor"]);
    const {data:pending,error:pendingError}=await admin.from("nurva_pending_uploads").select("storage_path").eq("asset_id",assetId).eq("group_id",groupId).eq("user_id",user.id).eq("device_id",deviceId).is("consumed_at",null).gt("expires_at",new Date().toISOString()).maybeSingle();
    if(pendingError||!pending) throw new HttpError("Upload authorization missing, expired or consumed",409);
    const {data:blob,error:downloadError}=await admin.storage.from("nurva-wallpapers").download(pending.storage_path);
    if(downloadError||!blob) throw new HttpError("Uploaded object could not be read",422);
    if(blob.size<1||blob.size>MAX_BYTES) throw new HttpError("Image must be 1 byte to 50 MiB",413);
    const bytes=new Uint8Array(await blob.arrayBuffer());
    if(bytes.byteLength!==blob.size) throw new HttpError("Uploaded size check failed",422);
    const image=inspectImage(bytes,mimeType);
    if(!image) throw new HttpError("File signature does not match a supported JPEG, PNG or WebP image",415);
    if(image.width<1||image.height<1||image.width>MAX_DIMENSION||image.height>MAX_DIMENSION||image.width*image.height>MAX_PIXELS) throw new HttpError("Image dimensions exceed Nurva limits",413);
    const digest=await crypto.subtle.digest("SHA-256",bytes);
    const sha256=Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,"0")).join("");
    // Publication RPC is executable only by service_role. It locks and consumes the
    // pending-upload record while incrementing the authoritative group revision.
    const rpcName = mosaicTiles===undefined
      ? "nurva_publish_wallpaper_revision"
      : "nurva_publish_wallpaper_revision_mosaic";
    const rpcArgs: Record<string, unknown> = {
      p_group:groupId,p_device:deviceId,p_user:user.id,p_asset:assetId,p_hash:sha256,
      p_mime:image.mimeType,p_size:bytes.byteLength,p_width:image.width,p_height:image.height,p_title:title??null,
    };
    if(mosaicTiles!==undefined) rpcArgs.p_tiles=mosaicTiles;
    const {data:published,error:publishError}=await admin.rpc(rpcName,rpcArgs);
    if(publishError||!published) throw new HttpError("Image verification succeeded, but publication failed",409);
    return jsonResponse(published);
  } catch(e) { return errorResponse(e); }
});
