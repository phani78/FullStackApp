import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import type { AppDispatch, RootState } from "../app/store";
import { clearAccessToken, clearSession, setAccessToken, setSession } from "../app/authSlice";
import { batchApi, originationApi } from "../app/originationApi";
import type { Role } from "../types";
import { ApplicationWizard } from "./ApplicationWizard";
import { BatchWorkbench } from "./BatchWorkbench";
import { StatusTracker } from "./StatusTracker";
import { UnderwriterWorkbench } from "./UnderwriterWorkbench";

const roles: Role[] = [
  "CUSTOMER",
  "UNDERWRITER",
  "UNDERWRITER_SUPERVISOR",
  "OPERATIONS",
  "AUDITOR",
];

export function App() {
  const dispatch = useDispatch<AppDispatch>();
  const { subject, role } = useSelector((state: RootState) => state.auth);
  const [loginEmail, setLoginEmail] = useState("customer@example.test");
  const [loginRole, setLoginRole] = useState<Role>("CUSTOMER");
  const [connecting, setConnecting] = useState(false);
  const [signInError, setSignInError] = useState(false);
  const [activeView, setActiveView] = useState("applications");
  const [applicationId, setApplicationId] = useState("");

  async function connect(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setConnecting(true);
    setSignInError(false);
    try {
      const response = await fetch("/api/v1/local-tokens", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Correlation-ID": crypto.randomUUID(),
          "X-Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({ subject: loginEmail, role: loginRole }),
      });
      if (!response.ok) throw new Error("Local sign-in failed.");
      const result = await response.json() as { accessToken: string };
      setAccessToken(result.accessToken);
      dispatch(originationApi.util.resetApiState());
      dispatch(batchApi.util.resetApiState());
      dispatch(setSession({ subject: loginEmail, role: loginRole }));
    } catch {
      clearAccessToken();
      setSignInError(true);
    } finally {
      setConnecting(false);
    }
  }

  if (!role) {
    return (
      <main className="grid min-h-screen place-items-center px-5 py-12">
        <section className="w-full max-w-md rounded-3xl bg-white p-8 shadow-card">
          <div className="mb-8 flex items-center gap-3">
            <div className="grid h-12 w-12 place-items-center rounded-2xl bg-bank text-xl font-bold text-white">N</div>
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.18em] text-bank">Northstar Bank</p>
              <h1 className="text-xl font-semibold">Card origination</h1>
            </div>
          </div>
          <p className="mb-6 text-sm leading-6 text-muted">
            Local demonstration access. Choose a test role to continue; these credentials are not production authentication.
          </p>
          <form onSubmit={connect} className="space-y-4">
            <label className="block text-sm font-medium">
              Work email
              <input
                required
                type="email"
                value={loginEmail}
                onChange={(event) => setLoginEmail(event.target.value)}
                className="mt-2 w-full rounded-xl border border-slate-200 px-4 py-3"
              />
            </label>
            <label className="block text-sm font-medium">
              Demonstration role
              <select
                value={loginRole}
                onChange={(event) => setLoginRole(event.target.value as Role)}
                className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-4 py-3"
              >
                {roles.map((item) => <option key={item}>{item}</option>)}
              </select>
            </label>
            <button disabled={connecting} className="w-full rounded-xl bg-bank px-4 py-3 font-semibold text-white disabled:opacity-60">
            {connecting ? "Connecting…" : "Continue"}
            </button>
            {signInError && <p role="alert" className="text-sm text-red-700">Local sign-in is unavailable. Check that the API is running.</p>}
          </form>
        </section>
      </main>
    );
  }

  const nav = role === "CUSTOMER"
    ? [{ id: "applications", label: "Apply for a card" }, { id: "status", label: "Application status" }]
    : role === "UNDERWRITER" || role === "UNDERWRITER_SUPERVISOR"
      ? [{ id: "review", label: "Review queue" }]
      : role === "OPERATIONS"
        ? [{ id: "batch", label: "Batch operations" }]
        : [{ id: "status", label: "Audit overview" }];

  return (
    <div className="min-h-screen md:flex">
      <aside className="flex w-full flex-col bg-ink px-5 py-6 text-white md:min-h-screen md:w-72 md:px-7">
        <div className="mb-10 flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-500 text-lg font-bold">N</div>
          <div><p className="font-semibold">Northstar</p><p className="text-xs text-slate-300">Card services</p></div>
        </div>
        <p className="mb-3 text-xs font-semibold uppercase tracking-[0.15em] text-slate-400">Workspace</p>
        <nav className="flex gap-2 md:flex-col">
          {nav.map((item) => (
            <button
              key={item.id}
              onClick={() => setActiveView(item.id)}
              className={`rounded-xl px-4 py-3 text-left text-sm ${activeView === item.id ? "bg-white/15 font-semibold" : "text-slate-300 hover:bg-white/10"}`}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <div className="mt-auto hidden border-t border-white/15 pt-5 md:block">
          <p className="truncate text-sm font-medium">{subject}</p>
          <p className="mt-1 text-xs text-slate-400">{role.replaceAll("_", " ")}</p>
          <button onClick={() => { clearAccessToken(); dispatch(originationApi.util.resetApiState()); dispatch(batchApi.util.resetApiState()); dispatch(clearSession()); }} className="mt-4 text-sm text-slate-300 hover:text-white">Sign out</button>
        </div>
      </aside>
      <main className="min-w-0 flex-1 px-5 py-7 md:px-10 md:py-10">
        <header className="mb-8 flex items-start justify-between gap-5">
          <div>
            <p className="text-sm font-medium text-bank">CARD ORIGINATION</p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight md:text-3xl">
              {activeView === "applications" ? "Find a card that fits" : activeView === "review" ? "Underwriter workbench" : activeView === "batch" ? "Batch operations" : "Application tracker"}
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
              {activeView === "applications" ? "A guided application with clear terms and secure handling of your information." : activeView === "review" ? "Review assigned applications and record auditable decisions." : activeView === "batch" ? "Start and monitor local nightly processing jobs." : "Follow the latest customer-safe application status."}
            </p>
          </div>
          <div className="hidden rounded-full bg-white px-4 py-2 text-xs font-semibold text-muted shadow-sm lg:block">{role.replaceAll("_", " ")}</div>
        </header>
        {activeView === "applications" && role === "CUSTOMER" && <ApplicationWizard onSubmitted={(id) => { setApplicationId(id); setActiveView("status"); }} />}
        {activeView === "status" && <StatusTracker applicationId={applicationId} onApplicationIdChange={setApplicationId} />}
        {activeView === "review" && <UnderwriterWorkbench supervisor={role === "UNDERWRITER_SUPERVISOR"} />}
        {activeView === "batch" && <BatchWorkbench />}
        {role === "AUDITOR" && <section className="rounded-2xl bg-white p-6 shadow-card"><h2 className="font-semibold">Read-only audit access</h2><p className="mt-2 text-sm text-muted">Audit data is available through authorized operational reporting.</p></section>}
      </main>
    </div>
  );
}
