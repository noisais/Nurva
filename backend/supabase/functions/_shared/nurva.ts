import { createClient, type SupabaseClient, type User } from "npm:@supabase/supabase-js@2";

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

export class HttpError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}
export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}
export function errorResponse(error: unknown): Response {
  if (error instanceof HttpError) return jsonResponse({ error: error.message }, error.status);
  console.error("Nurva function failed");
  return jsonResponse({ error: "Request could not be completed" }, 500);
}
function parseKeySet(raw: string | undefined): string | undefined {
  if (!raw) return undefined;
  try { const values = JSON.parse(raw) as Record<string,string>; return values.default ?? Object.values(values)[0]; }
  catch { return undefined; }
}
export type Context = { user: User; admin: SupabaseClient };
export async function getContext(req: Request): Promise<Context> {
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) throw new HttpError("Sign in first", 401);
  const url = Deno.env.get("SUPABASE_URL");
  const anon = Deno.env.get("SUPABASE_ANON_KEY") ?? parseKeySet(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS"));
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? parseKeySet(Deno.env.get("SUPABASE_SECRET_KEYS"));
  if (!url || !anon || !service) throw new HttpError("Server configuration is incomplete", 500);
  const userClient = createClient(url, anon, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } });
  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } });
  const { data, error } = await userClient.auth.getUser();
  if (error || !data.user) throw new HttpError("Invalid or expired session", 401);
  return { user: data.user, admin };
}
export function isUuid(value: unknown): value is string {
  return typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}
export async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2,"0")).join("");
}
export async function authorizeDevice(admin: SupabaseClient,userId: string,groupId: string,deviceId: string,secret: string,roles?: string[]): Promise<void> {
  if (!isUuid(groupId) || !isUuid(deviceId) || typeof secret !== "string" || secret.length < 32 || secret.length > 256) throw new HttpError("Device authorization failed",403);
  const hash = await sha256Hex(secret);
  const { data: device,error: de } = await admin.from("nurva_devices").select("id").eq("id",deviceId).eq("group_id",groupId).eq("user_id",userId).eq("secret_hash",hash).is("revoked_at",null).maybeSingle();
  if (de || !device) throw new HttpError("Device is not authorized",403);
  const { data: member,error: me } = await admin.from("nurva_group_members").select("role").eq("group_id",groupId).eq("user_id",userId).is("removed_at",null).maybeSingle();
  if (me || !member) throw new HttpError("Group membership is inactive",403);
  if (roles && !roles.includes(member.role)) throw new HttpError("Group role cannot perform this action",403);
}
export function requirePost(req: Request): void { if (req.method !== "POST") throw new HttpError("Use POST",405); }
export async function readJson(req: Request): Promise<Record<string,unknown>> {
  try { const v = await req.json() as unknown; if (!v || typeof v !== "object" || Array.isArray(v)) throw new Error(); return v as Record<string,unknown>; }
  catch { throw new HttpError("Body must be a JSON object",400); }
}
