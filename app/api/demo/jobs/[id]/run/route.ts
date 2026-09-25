import { NextRequest } from "next/server";
import { resolveApiUrl } from "@/app/lib/api";

export const dynamic = "force-dynamic";

const API_URL = resolveApiUrl();

// Public on purpose: the API only runs its own fixed demo jobs here, so no
// session or token is involved.
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  const upstream = await fetch(`${API_URL}/api/demo/jobs/${encodeURIComponent(id)}/run`, {
    method: "POST",
    cache: "no-store",
    signal: request.signal,
  });

  return new Response(upstream.body, {
    status: upstream.status,
    headers: {
      "Content-Type": upstream.headers.get("content-type") ?? "application/json",
      "Cache-Control": "no-cache, no-transform",
    },
  });
}
