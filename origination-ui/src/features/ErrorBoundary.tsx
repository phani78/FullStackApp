import { Component, type ReactNode } from "react";

interface ErrorBoundaryState {
  hasError: boolean;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="grid min-h-screen place-items-center px-6">
          <section role="alert" className="max-w-md rounded-2xl bg-white p-8 text-center shadow-card">
            <h1 className="text-xl font-semibold">We couldn’t load this page</h1>
            <p className="mt-3 text-sm leading-6 text-muted">Please refresh the page or contact customer service if the issue continues.</p>
            <button onClick={() => window.location.reload()} className="mt-5 rounded-xl bg-bank px-4 py-2 text-sm font-semibold text-white">Refresh</button>
          </section>
        </main>
      );
    }
    return this.props.children;
  }
}
