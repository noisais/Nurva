import { inspectImage } from "./imageInspection.ts";
function putAscii(b:Uint8Array,o:number,s:string){[...s].forEach((c,i)=>b[o+i]=c.charCodeAt(0));}
function png(w:number,h:number){const b=new Uint8Array(24);b.set([137,80,78,71,13,10,26,10]);b.set([0,0,0,13],8);putAscii(b,12,"IHDR");new DataView(b.buffer).setUint32(16,w);new DataView(b.buffer).setUint32(20,h);return b;}
function jpeg(w:number,h:number){return new Uint8Array([0xff,0xd8,0xff,0xc0,0,11,8,(h>>8)&255,h&255,(w>>8)&255,w&255,1,1,0x11,0]);}
Deno.test("reads PNG IHDR dimensions",()=>{const v=inspectImage(png(1920,1080),"image/png");if(v?.width!==1920||v.height!==1080)throw new Error("PNG dimensions incorrect");});
Deno.test("reads JPEG SOF dimensions",()=>{const v=inspectImage(jpeg(2400,1080),"image/jpeg");if(v?.width!==2400||v.height!==1080)throw new Error("JPEG dimensions incorrect");});
Deno.test("rejects mismatched MIME and unknown bytes",()=>{if(inspectImage(png(10,10),"image/jpeg")!==null)throw new Error("MIME mismatch accepted");if(inspectImage(new Uint8Array([1,2,3]),"image/png")!==null)throw new Error("unknown bytes accepted");});
