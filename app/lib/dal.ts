import "server-only";
import { cache } from "react";
import { redirect } from "next/navigation";
import { getSessionToken } from "@/app/lib/session";

type SessionClaims = {
  userId: string;
  organizationId: string;
  email: string;
};

// The JWT is issued and verified by the Spring Boot API; every authenticated
// fetch through app/lib/api.ts sends it as a Bearer token and the API is what
// actually rejects an invalid or expired one. Decoding it here (without
// checking the signature) is only for reading claims to render UI - it must
// never be treated as proof of a valid session on its own.
function decodeClaims(token: string): SessionClaims | null {
  try {
    const payload = token.split(".")[1];
    const json = Buffer.from(payload, "base64url").toString("utf-8");
    const claims = JSON.parse(json);
    return { userId: claims.sub, organizationId: claims.orgId, email: claims.email };
  } catch {
    return null;
  }
}

export const verifySession = cache(async () => {
  const token = await getSessionToken();
  const claims = token ? decodeClaims(token) : null;

  if (!token || !claims) {
    redirect("/login");
  }

  return { token, ...claims };
});
