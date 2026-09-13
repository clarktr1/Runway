import { redirect } from "next/navigation";
import { getSessionToken } from "@/app/lib/session";

export default async function Home() {
  const token = await getSessionToken();
  redirect(token ? "/dashboard" : "/login");
}
