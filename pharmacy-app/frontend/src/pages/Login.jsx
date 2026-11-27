import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { errMsg } from '../api';

export default function Login() {
  const { user, login } = useAuth();
  const nav = useNavigate();
  const [f, setF] = useState({ username: '', password: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErr('');
    try {
      await login(f.username, f.password);
      nav('/');
    } catch (x) {
      setErr(errMsg(x));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <form onSubmit={submit}>
        <div>
          <h1>Pharmacy Desk</h1>
          <div style={{ color: 'var(--muted)' }}>Sign in to start billing and managing stock.</div>
        </div>
        {err && <div className="alert err">{err}</div>}
        <label className="f">Username<input autoFocus value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} /></label>
        <label className="f">Password<input type="password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} /></label>
        <button className="btn" disabled={busy}>{busy ? 'Signing in...' : 'Sign in'}</button>
      </form>
    </div>
  );
}
