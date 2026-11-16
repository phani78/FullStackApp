import { useState } from "react";
import { useGetProductsQuery, useSubmitApplicationMutation } from "../app/originationApi";
import type { ApplicationRequest } from "../types";

const initialForm: ApplicationRequest = {
  firstName: "",
  lastName: "",
  email: "",
  ssn: "",
  dateOfBirth: "",
  addressLine: "",
  city: "",
  state: "",
  postalCode: "",
  annualIncome: 0,
  monthlyHousingPayment: 0,
  monthlyDebtPayment: 0,
  employmentStatus: "EMPLOYED",
  consentToCreditCheck: false,
  productCode: "",
};

const personas = ["PRIME", "THIN_FILE", "FRAUD_ALERT", "LOW_SCORE", "INVALID_ADDRESS", "KYC_MISMATCH", "OFAC_MATCH", "ADAPTER_FAILURE"];

export function ApplicationWizard({ onSubmitted }: { onSubmitted: (id: string) => void }) {
  const [form, setForm] = useState(initialForm);
  const [step, setStep] = useState(0);
  const [persona, setPersona] = useState("PRIME");
  const [message, setMessage] = useState("");
  const { data: products = [], isLoading: productsLoading } = useGetProductsQuery();
  const [submitApplication, submitState] = useSubmitApplicationMutation();

  function update<K extends keyof ApplicationRequest>(key: K, value: ApplicationRequest[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage("");
    try {
      const receipt = await submitApplication({ application: form, persona }).unwrap();
      onSubmitted(receipt.applicationId);
      setMessage(receipt.message);
      setForm(initialForm);
      setStep(0);
      submitState.reset();
    } catch {
      setMessage("Sorry, we ran into an issue while processing your application. Please call customer service at 1-800-XXX-XXXX.");
      setForm((current) => ({ ...current, ssn: "", dateOfBirth: "" }));
      submitState.reset();
    }
  }

  const steps = ["About you", "Address", "Finances"];

  return (
    <section className="grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(280px,0.65fr)]">
      <div className="rounded-3xl bg-white p-6 shadow-card md:p-8">
        <div className="mb-7 flex items-center gap-3">
          {steps.map((label, index) => (
            <div key={label} className="flex flex-1 items-center gap-2">
              <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-full text-sm font-semibold ${index <= step ? "bg-bank text-white" : "bg-slate-100 text-muted"}`}>{index + 1}</span>
              <span className={`hidden text-xs font-medium sm:block ${index === step ? "text-ink" : "text-muted"}`}>{label}</span>
              {index < steps.length - 1 && <div className="h-px flex-1 bg-slate-200" />}
            </div>
          ))}
        </div>
        <form onSubmit={submit} className="space-y-5">
          {step === 0 && (
            <>
              <div><h2 className="text-lg font-semibold">Let’s start with the basics</h2><p className="mt-1 text-sm text-muted">Your identity information is sent securely for verification and is not included in application status messages.</p></div>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Legal first name" value={form.firstName} onChange={(value) => update("firstName", value)} required />
                <Field label="Legal last name" value={form.lastName} onChange={(value) => update("lastName", value)} required />
                <Field label="Email address" type="email" value={form.email} onChange={(value) => update("email", value)} required />
                <Field label="Social Security number" type="password" inputMode="numeric" autoComplete="off" value={form.ssn} onChange={(value) => update("ssn", value.replace(/\D/g, "").slice(0, 9))} required />
                <Field label="Date of birth" type="date" value={form.dateOfBirth} onChange={(value) => update("dateOfBirth", value)} required />
              </div>
            </>
          )}
          {step === 1 && (
            <>
              <div><h2 className="text-lg font-semibold">Where should we reach you?</h2><p className="mt-1 text-sm text-muted">We standardize the address before underwriting.</p></div>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="sm:col-span-2"><Field label="Street address" value={form.addressLine} onChange={(value) => update("addressLine", value)} required /></div>
                <Field label="City" value={form.city} onChange={(value) => update("city", value)} required />
                <Field label="State" value={form.state} onChange={(value) => update("state", value.toUpperCase().slice(0, 2))} required maxLength={2} />
                <Field label="ZIP code" value={form.postalCode} onChange={(value) => update("postalCode", value)} required />
              </div>
            </>
          )}
          {step === 2 && (
            <>
              <div><h2 className="text-lg font-semibold">Income and card choice</h2><p className="mt-1 text-sm text-muted">Tell us about your monthly obligations so we can assess affordability.</p></div>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Annual income ($)" type="number" min="0" value={String(form.annualIncome || "")} onChange={(value) => update("annualIncome", Number(value))} required />
                <Field label="Monthly housing payment ($)" type="number" min="0" value={String(form.monthlyHousingPayment || "")} onChange={(value) => update("monthlyHousingPayment", Number(value))} required />
                <Field label="Monthly debt payments ($)" type="number" min="0" value={String(form.monthlyDebtPayment || "")} onChange={(value) => update("monthlyDebtPayment", Number(value))} required />
                <label className="block text-sm font-medium">Employment status
                  <select value={form.employmentStatus} onChange={(event) => update("employmentStatus", event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3">
                    <option value="EMPLOYED">Employed</option><option value="SELF_EMPLOYED">Self-employed</option><option value="RETIRED">Retired</option><option value="OTHER">Other</option>
                  </select>
                </label>
                <label className="block text-sm font-medium sm:col-span-2">Card product
                  <select required value={form.productCode} onChange={(event) => update("productCode", event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3">
                    <option value="">{productsLoading ? "Loading products…" : "Select a card"}</option>
                    {products.map((product) => <option key={product.productCode} value={product.productCode}>{product.tier} · {product.aprMin.toFixed(2)}%–{product.aprMax.toFixed(2)}% APR</option>)}
                  </select>
                </label>
                <label className="flex items-start gap-3 text-sm leading-5 text-muted sm:col-span-2">
                  <input type="checkbox" checked={form.consentToCreditCheck} onChange={(event) => update("consentToCreditCheck", event.target.checked)} className="mt-1 accent-bank" />
                  I authorize a credit bureau inquiry and agree to the application disclosures.
                </label>
                <label className="block text-xs text-muted sm:col-span-2">Local test persona
                  <select value={persona} onChange={(event) => setPersona(event.target.value)} className="mt-2 block w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm">
                    {personas.map((item) => <option key={item}>{item}</option>)}
                  </select>
                </label>
              </div>
            </>
          )}
          {message && <p role="status" className="rounded-xl bg-bank-soft p-3 text-sm text-bank">{message}</p>}
          <div className="flex justify-between border-t border-slate-100 pt-5">
            <button type="button" disabled={step === 0} onClick={() => setStep((current) => current - 1)} className="rounded-xl px-4 py-2 text-sm font-semibold text-muted disabled:opacity-30">Back</button>
            {step < 2
              ? <button type="button" onClick={() => setStep((current) => current + 1)} className="rounded-xl bg-bank px-5 py-2.5 text-sm font-semibold text-white">Continue</button>
              : <button disabled={submitState.isLoading || !form.consentToCreditCheck} className="rounded-xl bg-bank px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-50">{submitState.isLoading ? "Submitting…" : "Submit application"}</button>}
          </div>
        </form>
      </div>
      <aside className="space-y-4">
        <div className="rounded-3xl bg-ink p-6 text-white shadow-card">
          <p className="text-sm text-emerald-300">A better way forward</p>
          <h2 className="mt-2 text-xl font-semibold">Choose with confidence</h2>
          <p className="mt-3 text-sm leading-6 text-slate-300">Review rates, fees, rewards, and credit limits before you apply. Final terms depend on eligibility and underwriting.</p>
          <div className="mt-6 space-y-3">
            {products.map((product) => (
              <div key={product.productCode} className="rounded-2xl border border-white/10 bg-white/5 p-4">
                <div className="flex justify-between gap-3"><strong className="text-sm">{product.tier}</strong><span className="text-xs text-emerald-200">{product.annualFee === 0 ? "$0 annual fee" : `$${product.annualFee.toFixed(0)} annual fee`}</span></div>
                <p className="mt-2 text-xs leading-5 text-slate-300">{product.rewardsDescription}</p>
                <p className="mt-2 text-xs text-slate-400">{product.creditLimitMin.toLocaleString()}–{product.creditLimitMax.toLocaleString()} assumed line</p>
              </div>
            ))}
          </div>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-5 text-xs leading-5 text-muted">
          <strong className="text-ink">Privacy first</strong><br />
          Sensitive identity details are used for verification only and are never shown in customer-facing status messages.
        </div>
      </aside>
    </section>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  required = false,
  ...props
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  required?: boolean;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "type" | "required">) {
  return (
    <label className="block text-sm font-medium">
      {label}
      <input
        type={type}
        required={required}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3"
        {...props}
      />
    </label>
  );
}
