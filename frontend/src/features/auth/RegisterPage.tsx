import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { AuthLayout } from "./AuthLayout";
import { extractErrorMessage } from "../../shared/api/client";
import { ErrorBanner } from "../../shared/ui/Feedback";

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ organizationName: "", firstName: "", lastName: "", email: "", password: "" });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const passwordOk = /^(?=.*[A-Za-z])(?=.*\d).{8,100}$/.test(form.password);

  if (user) return <Navigate to="/dashboard" replace />;

  function update(field: keyof typeof form) {
    return (e: React.ChangeEvent<HTMLInputElement>) => setForm((f) => ({ ...f, [field]: e.target.value }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(form);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <form className="auth-form glass" onSubmit={handleSubmit}>
        <h2>Launch your workspace</h2>
        <p className="muted">You'll be the admin of a brand-new organization.</p>
        {error && <ErrorBanner message={error} />}
        <label className="field">
          <span>Organization name</span>
          <input value={form.organizationName} onChange={update("organizationName")} required autoFocus placeholder="Acme Inc." />
        </label>
        <div className="form-grid">
          <label className="field">
            <span>First name</span>
            <input value={form.firstName} onChange={update("firstName")} required autoComplete="given-name" />
          </label>
          <label className="field">
            <span>Last name</span>
            <input value={form.lastName} onChange={update("lastName")} required autoComplete="family-name" />
          </label>
        </div>
        <label className="field">
          <span>Email</span>
          <input type="email" value={form.email} onChange={update("email")} required autoComplete="email" />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" value={form.password} onChange={update("password")} required autoComplete="new-password" />
          <small className={form.password && !passwordOk ? "text-danger" : "muted"}>
            At least 8 characters, with both letters and numbers.
          </small>
        </label>
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting || !passwordOk}>
          {submitting ? "Creating…" : "Create organization"}
        </button>
        <p className="muted center">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
