export type ImageMime = "image/jpeg" | "image/png" | "image/webp";
export type ImageMetadata = { mimeType: ImageMime; width: number; height: number };
const ascii = (b: Uint8Array, o: number, s: string) => o >= 0 && o + s.length <= b.length && [...s].every((c,i) => b[o+i] === c.charCodeAt(0));
const be16 = (b: Uint8Array,o: number) => ((b[o]??0)<<8)|(b[o+1]??0);
const be32 = (b: Uint8Array,o: number) => ((b[o]??0)*0x1000000)|((b[o+1]??0)<<16)|((b[o+2]??0)<<8)|(b[o+3]??0);
const le24 = (b: Uint8Array,o: number) => (b[o]??0)|((b[o+1]??0)<<8)|((b[o+2]??0)<<16);

function jpeg(b: Uint8Array) {
  if (b.length<4 || b[0]!==0xff || b[1]!==0xd8) return null;
  const sof = new Set([0xc0,0xc1,0xc2,0xc3,0xc5,0xc6,0xc7,0xc9,0xca,0xcb,0xcd,0xce,0xcf]);
  let p=2;
  while (p+4<=b.length) {
    if (b[p]!==0xff) { p++; continue; }
    while (p<b.length && b[p]===0xff) p++;
    if (p>=b.length) return null;
    const marker=b[p++];
    if (marker===0xd9 || marker===0xda) return null;
    if (marker===0xd8 || marker===0x01 || (marker>=0xd0 && marker<=0xd7)) continue;
    if (p+2>b.length) return null;
    const len=be16(b,p);
    if (len<2 || p+len>b.length) return null;
    if (sof.has(marker)) return len>=7 ? {height:be16(b,p+3),width:be16(b,p+5)} : null;
    p+=len;
  }
  return null;
}
function png(b: Uint8Array) {
  const signature=[137,80,78,71,13,10,26,10];
  if (b.length<24 || !signature.every((v,i)=>b[i]===v) || !ascii(b,12,"IHDR")) return null;
  return {width:be32(b,16),height:be32(b,20)};
}
function webp(b: Uint8Array) {
  if (b.length<30 || !ascii(b,0,"RIFF") || !ascii(b,8,"WEBP")) return null;
  const kind=String.fromCharCode(...b.slice(12,16));
  const len=(b[16]??0)|((b[17]??0)<<8)|((b[18]??0)<<16)|((b[19]??0)<<24);
  if (len<1 || 20+len>b.length) return null;
  if (kind==="VP8X" && len>=10) return {width:le24(b,24)+1,height:le24(b,27)+1};
  if (kind==="VP8L" && len>=5 && b[20]===0x2f) {
    const a=b[21]??0,c=b[22]??0,d=b[23]??0,e=b[24]??0;
    return {width:1+a+((c&0x3f)<<8),height:1+(c>>6)+(d<<2)+((e&0x0f)<<10)};
  }
  if (kind==="VP8 " && len>=10 && b[23]===0x9d && b[24]===0x01 && b[25]===0x2a) {
    return {width:(((b[27]??0)&0x3f)<<8)|(b[26]??0),height:(((b[29]??0)&0x3f)<<8)|(b[28]??0)};
  }
  return null;
}
export function inspectImage(bytes: Uint8Array, requestedMime: string): ImageMetadata | null {
  let mime: ImageMime | null=null;
  let dimensions: {width:number;height:number} | null=null;
  if (bytes[0]===0xff && bytes[1]===0xd8) { mime="image/jpeg"; dimensions=jpeg(bytes); }
  else if (bytes[0]===137 && ascii(bytes,1,"PNG\r\n\u001a\n")) { mime="image/png"; dimensions=png(bytes); }
  else if (ascii(bytes,0,"RIFF") && ascii(bytes,8,"WEBP")) { mime="image/webp"; dimensions=webp(bytes); }
  if (!mime || mime!==requestedMime || !dimensions) return null;
  return {mimeType:mime,width:dimensions.width,height:dimensions.height};
}
