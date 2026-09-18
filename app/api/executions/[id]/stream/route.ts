import { NextRequest } from "next/server";
import { getSessionToken } from "@/app/lib/session";

export const dynamic = "force-dynamic";

const API_URL = process.env.RUNWAY_API_URL ?? "http://localhost:8080";

export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const token = await getSessionToken();

  const upstream = await fetch(`${API_URL}/api/executions/${id}/stream`, {
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
