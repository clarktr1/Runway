import { NextRequest } from "next/server";
import { resolveApiUrl } from "@/app/lib/api";
import { getSessionToken } from "@/app/lib/session";

export const dynamic = "force-dynamic";

const API_URL = resolveApiUrl();

export async function GET(request: NextRequest) {
  const token = await getSessionToken();

  const upstream = await fetch(`${API_URL}/api/workers/stream`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    cache: "no-store",
    signal: request.signal,
  });

  return new Response(upstream.body, {
    status: upstream.status,
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
    },
  });
}
