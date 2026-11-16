import { useGetApplicationStatusQuery } from "../app/originationApi";

export function StatusTracker({
  applicationId,
  onApplicationIdChange,
}: {
  applicationId: string;
  onApplicationIdChange: (id: string) => void;
}) {
  const statusQuery = useGetApplicationStatusQuery(applicationId, {
    skip: !applicationId,
    pollingInterval: 3000,
  });
  const status = statusQuery.data;
  const labels = ["SUBMITTED", "UNDERWRITING", "MANUAL_REVIEW", "APPROVED", "DECLINED", "SENSITIVE_VALIDATION_REVIEW", "EXPIRED"];

  return (
    <section className="max-w-3xl rounded-3xl bg-white p-6 shadow-card md:p-8">
      <h2 className="text-lg font-semibold">Check an application</h2>
      <p className="mt-1 text-sm text-muted">Enter the reference number from your confirmation.</p>
      <div className="mt-5 flex flex-col gap-3 sm:flex-row">
        <input value={applicationId} onChange={(event) => onApplicationIdChange(event.target.value)} placeholder="Application reference" className="min-w-0 flex-1 rounded-xl border border-slate-200 px-4 py-3" />
        <button onClick={() => statusQuery.refetch()} disabled={!applicationId || statusQuery.isFetching} className="rounded-xl bg-bank px-5 py-3 text-sm font-semibold text-white disabled:opacity-50">Refresh status</button>
      </div>
      {statusQuery.isError && <p role="alert" className="mt-5 rounded-xl bg-amber-50 p-4 text-sm text-amber-900">We couldn’t retrieve this application. Check the reference or contact customer service.</p>}
      {status && (
        <div className="mt-7 border-t border-slate-100 pt-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div><p className="text-xs uppercase tracking-wide text-muted">Application reference</p><p className="mt-1 font-mono text-sm">{status.applicationId}</p></div>
            <span className="rounded-full bg-bank-soft px-3 py-1.5 text-xs font-semibold text-bank">{status.status.replaceAll("_", " ")}</span>
          </div>
          <p className="mt-5 rounded-xl bg-slate-50 p-4 text-sm leading-6 text-ink">{status.message}</p>
          <div className="mt-6 grid gap-2 sm:grid-cols-4">
            {labels.slice(0, 4).map((label, index) => {
              const complete = labels.indexOf(status.status) >= index;
              return <div key={label} className={`rounded-xl p-3 text-xs ${complete ? "bg-bank-soft font-semibold text-bank" : "bg-slate-50 text-muted"}`}>{index + 1}. {label.replaceAll("_", " ")}</div>;
            })}
          </div>
        </div>
      )}
    </section>
  );
}
