import "server-only";
import { cookies } from "next/headers";

const SESSION_COOKIE = "runway_session";

export async function createSession(token: string) {
  const cookieStore = await cookies();
  cookieStore.set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    // The JWT itself carries its own expiration; the cookie just needs to
    // outlive it so the API (not the cookie) is what actually rejects stale
    // sessions.
    maxAge: 60 * 60 * 24 * 30,
  });
}

export async function getSessionToken() {
  const cookieStore = await cookies();
  return cookieStore.get(SESSION_COOKIE)?.value;
}

export async function deleteSession() {
  const cookieStore = await cookies();
  cookieStore.delete(SESSION_COOKIE);
}
