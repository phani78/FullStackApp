import { useState } from "react";
import { useGetBatchJobsQuery, useLaunchBatchJobMutation } from "../app/originationApi";

const jobs = [
  ["cardEmbossingJob", "Card embossing file"],
  ["bureauReportingJob", "Bureau reporting"],
  ["staleApplicationExpiryJob", "Expire stale applications"],
  ["adverseActionNoticeJob", "Generate and simulate adverse-action notices"],
] as const;

export function BatchWorkbench() {
  const today = new Date().toISOString().slice(0, 10);
  const [businessDate, setBusinessDate] = useState(today);
  const [notice, setNotice] = useState("");
  const { data: executions = [], isLoading, isError } = useGetBatchJobsQuery(undefined, { pollingInterval: 5000 });
  const [launch, launchState] = useLaunchBatchJobMutation();

  async function start(jobName: string) {
    try {
      const execution = await launch({ jobName, businessDate }).unwrap();
      setNotice(`${execution.jobName} started with execution ${execution.executionId}.`);
    } catch {
      setNotice("The job could not be started. Confirm the business date and check the batch service.");
    }
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
      <section className="rounded-3xl bg-white p-6 shadow-card md:p-8">
        <h2 className="text-lg font-semibold">Run a nightly job</h2>
        <p className="mt-1 text-sm text-muted">Jobs are restartable and run locally using the configured business date.</p>
        <label className="mt-5 block text-sm font-medium">Business date
          <input type="date" value={businessDate} onChange={(event) => setBusinessDate(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3" />
        </label>
        <div className="mt-5 space-y-3">
          {jobs.map(([name, label]) => (
            <button key={name} onClick={() => void start(name)} disabled={launchState.isLoading} className="flex w-full items-center justify-between rounded-xl border border-slate-200 px-4 py-3 text-left text-sm font-semibold hover:border-bank hover:bg-bank-soft disabled:opacity-50">
              {label}<span aria-hidden="true" className="text-bank">→</span>
            </button>
          ))}
        </div>
        {notice && <p role="status" className="mt-4 rounded-xl bg-bank-soft p-3 text-sm text-bank">{notice}</p>}
      </section>
      <section className="rounded-3xl bg-white p-6 shadow-card md:p-8">
        <h2 className="text-lg font-semibold">Recent executions</h2>
        {isLoading && <p className="mt-5 text-sm text-muted">Loading job history…</p>}
        {isError && <p role="alert" className="mt-5 rounded-xl bg-amber-50 p-4 text-sm text-amber-900">Batch history is unavailable.</p>}
        {!isLoading && !isError && executions.length === 0 && <p className="mt-5 rounded-xl bg-slate-50 p-4 text-sm text-muted">No local batch executions yet.</p>}
        <div className="mt-4 space-y-3">
          {executions.map((execution) => (
            <div key={execution.executionId} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-100 p-4">
              <div><p className="text-sm font-semibold">{execution.jobName}</p><p className="mt-1 text-xs text-muted">Execution {execution.executionId} · {execution.startedAt ? new Date(execution.startedAt).toLocaleString() : "Queued"}</p></div>
              <span className={`rounded-full px-3 py-1 text-xs font-semibold ${execution.status === "COMPLETED" ? "bg-bank-soft text-bank" : "bg-amber-50 text-amber-800"}`}>{execution.status}</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
