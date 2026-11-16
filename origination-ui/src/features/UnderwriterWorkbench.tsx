import { useState } from "react";
import {
  useAddReviewNoteMutation,
  useApproveDecisionMutation,
  useClaimApplicationMutation,
  useGetReviewQueueQuery,
  useGetSensitiveReviewQueueQuery,
  useProposeDecisionMutation,
} from "../app/originationApi";

export function UnderwriterWorkbench({ supervisor }: { supervisor: boolean }) {
  const { data: queue = [], isLoading, isError } = useGetReviewQueueQuery(undefined, { pollingInterval: 10000 });
  const [claim] = useClaimApplicationMutation();
  const [addNote] = useAddReviewNoteMutation();
  const [propose] = useProposeDecisionMutation();
  const [approve] = useApproveDecisionMutation();
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState("");

  async function act(action: () => Promise<unknown>) {
    try {
      await action();
      setNotice("Action recorded for review.");
    } catch {
      setNotice("The action could not be completed. Refresh the queue and try again.");
    }
  }

  return (
    <section className="rounded-3xl bg-white p-6 shadow-card md:p-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div><h2 className="text-lg font-semibold">Manual review queue</h2><p className="mt-1 text-sm text-muted">Customer identity and sensitive validation evidence are not shown here.</p></div>
        <span className="rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-muted">{queue.length} cases</span>
      </div>
      {notice && <p role="status" className="mt-4 rounded-xl bg-bank-soft p-3 text-sm text-bank">{notice}</p>}
      {isLoading && <p className="mt-6 text-sm text-muted">Loading review queue…</p>}
      {isError && <p role="alert" className="mt-6 rounded-xl bg-amber-50 p-4 text-sm text-amber-900">The review queue is unavailable.</p>}
      {!isLoading && !isError && queue.length === 0 && <p className="mt-6 rounded-2xl bg-slate-50 p-6 text-sm text-muted">No applications currently need manual review.</p>}
      <div className="mt-5 space-y-4">
        {queue.map((item) => (
          <article key={item.applicationId} className="rounded-2xl border border-slate-200 p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div><h3 className="font-semibold">{item.applicantName}</h3><p className="mt-1 text-xs text-muted">{item.productCode} · submitted {new Date(item.submittedAt).toLocaleString()}</p></div>
              <span className="rounded-full bg-amber-50 px-3 py-1.5 text-xs font-semibold text-amber-800">{item.status.replaceAll("_", " ")}</span>
            </div>
            <p className="mt-3 text-xs text-muted">Application {item.applicationId} · SSN ending {item.ssnLast4}</p>
            <div className="mt-4 grid gap-2 rounded-xl bg-slate-50 p-3 text-xs text-muted sm:grid-cols-2 lg:grid-cols-4">
              <span>Income: ${item.annualIncome.toLocaleString()} / year</span>
              <span>Housing: ${item.monthlyHousingPayment.toLocaleString()} / month</span>
              <span>Other debt: ${item.monthlyDebtPayment.toLocaleString()} / month</span>
              <span>Bureau file: {item.bureauScore ?? "Thin file"}</span>
              <span>Address check: {item.addressValidationStatus}</span>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <button onClick={() => void act(() => claim(item.applicationId).unwrap())} className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold">Claim case</button>
              <button onClick={() => void act(() => propose({ id: item.applicationId, outcome: "APPROVED" }).unwrap())} className="rounded-lg bg-bank px-3 py-2 text-xs font-semibold text-white">Propose approval</button>
              <button onClick={() => void act(() => propose({ id: item.applicationId, outcome: "DECLINED" }).unwrap())} className="rounded-lg border border-red-200 px-3 py-2 text-xs font-semibold text-red-800">Propose decline</button>
              {supervisor && <><button onClick={() => void act(() => approve({ id: item.applicationId, approve: true }).unwrap())} className="rounded-lg bg-ink px-3 py-2 text-xs font-semibold text-white">Approve proposal</button><button onClick={() => void act(() => approve({ id: item.applicationId, approve: false }).unwrap())} className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold">Reject proposal</button></>}
            </div>
            <div className="mt-4 flex flex-col gap-2 sm:flex-row">
              <input value={notes[item.applicationId] ?? ""} onChange={(event) => setNotes((current) => ({ ...current, [item.applicationId]: event.target.value }))} placeholder="Add a restricted review note" className="min-w-0 flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm" />
              <button onClick={() => void act(() => addNote({ id: item.applicationId, note: notes[item.applicationId] ?? "" }).unwrap())} className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold">Save note</button>
            </div>
          </article>
        ))}
      </div>
      {supervisor && <RestrictedValidationQueue />}
    </section>
  );
}

function RestrictedValidationQueue() {
  const { data: cases = [], isError } = useGetSensitiveReviewQueueQuery(undefined, { pollingInterval: 10000 });

  return (
    <section className="mt-8 border-t border-slate-200 pt-6">
      <h2 className="text-base font-semibold">Restricted validation queue</h2>
      <p className="mt-1 text-sm text-muted">Supervisor-only category and provider references. Each access is audited.</p>
      {isError && <p role="alert" className="mt-3 text-sm text-amber-800">Restricted validation cases are unavailable.</p>}
      <div className="mt-4 space-y-2">
        {cases.map((item) => (
          <div key={`${item.applicationId}-${item.providerReference}`} className="flex flex-wrap justify-between gap-2 rounded-xl bg-amber-50 p-3 text-xs">
            <span className="font-semibold text-amber-950">{item.validationCategory.replaceAll("_", " ")}</span>
            <span className="font-mono text-amber-900">{item.applicationId}</span>
            <span className="text-amber-800">{new Date(item.createdAt).toLocaleString()}</span>
          </div>
        ))}
        {cases.length === 0 && <p className="rounded-xl bg-slate-50 p-4 text-sm text-muted">No restricted validation cases.</p>}
      </div>
    </section>
  );
}
