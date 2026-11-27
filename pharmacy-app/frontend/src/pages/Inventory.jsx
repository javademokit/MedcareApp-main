import { useEffect, useState } from 'react';
import api, { errMsg } from '../api';
import { useAuth } from '../auth';
import { money } from '../fmt';
import { Empty, ExpiryChip } from '../components/ui';

export default function Inventory() {
  const { hasRole } = useAuth();
  const canAct = hasRole('ADMIN', 'MANAGER', 'PHARMACIST');
  const [tab, setTab] = useState('expiry');
  const [days, setDays] = useState(90);
  const [rows, setRows] = useState([]);
  const [low, setLow] = useState([]);
  const [msg, setMsg] = useState(null);

  const load = () => {
    if (tab === 'expiry') api.get('/inventory/expiring', { params: { days } }).then((r) => setRows(r.data));
    if (tab === 'batches') api.get('/inventory/batches').then((r) => setRows(r.data));
    if (tab === 'low') api.get('/inventory/low-stock').then((r) => setLow(r.data));
  };
  useEffect(load, [tab, days]);

  const run = async (fn, ok) => { try { await fn(); setMsg({ ok: true, t: ok }); load(); } catch (e) { setMsg({ ok: false, t: errMsg(e) }); } };
  const adjust = (b) => {
    const v = window.prompt(`Change in units for ${b.medicineName} batch ${b.batchNo} (use a minus sign to reduce)`);
    if (!v || isNaN(+v)) return;
    const reason = window.prompt('Reason for this stock change (required)');
    if (!reason) return;
    run(() => api.post('/inventory/adjustments', { batchId: b.id, qtyDelta: +v, reason }), 'Stock updated');
  };
  const setStatus = (b, status) => run(() => api.patch(`/inventory/batches/${b.id}/status`, { status }), `Batch marked ${status.toLowerCase()}`);

  return (
    <>
      <div className="head"><h1>Stock and expiry</h1></div>
      <div className="tabs">
        {[['expiry', 'Expiry watch'], ['batches', 'All batches'], ['low', 'Low stock']].map(([k, l]) => (
          <button key={k} className={tab === k ? 'on' : ''} onClick={() => { setMsg(null); setTab(k); }}>{l}</button>
        ))}
      </div>
      {msg && <div className={`alert ${msg.ok ? 'ok' : 'err'}`}>{msg.t}</div>}
      {tab === 'expiry' && (
        <div className="row" style={{ marginBottom: 12 }}>
          Show batches expiring within
          <select style={{ width: 120 }} value={days} onChange={(e) => setDays(+e.target.value)}>{[30, 60, 90, 180].map((d) => <option key={d} value={d}>{d} days</option>)}</select>
        </div>
      )}
      {tab !== 'low' && (
        <div className="scroll">
          <table className="tbl">
            <thead><tr><th>Medicine</th><th>Batch</th><th>Expiry</th><th className="num">MRP/pack</th><th className="num">Units</th><th>Status</th><th /></tr></thead>
            <tbody>
              {rows.map((b) => (
                <tr key={b.id}>
                  <td>{b.medicineName}</td><td>{b.batchNo}</td><td><ExpiryChip value={b.expiryDate} /></td>
                  <td className="num">{money(b.mrp)}</td><td className="num">{b.qtyAvailable}</td><td><span className={`badge ${b.status}`}>{b.status}</span></td>
                  <td className="row">
                    {canAct && <button className="link" onClick={() => adjust(b)}>Adjust</button>}
                    {canAct && b.status === 'ACTIVE' && <button className="link" onClick={() => setStatus(b, 'QUARANTINE')}>Quarantine</button>}
                    {canAct && b.status !== 'ACTIVE' && <button className="link" onClick={() => setStatus(b, 'ACTIVE')}>Release</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!rows.length && <Empty>Nothing to show for this filter.</Empty>}
        </div>
      )}
      {tab === 'low' && (
        <table className="tbl">
          <thead><tr><th>Medicine</th><th className="num">In stock (units)</th><th className="num">Reorder level</th></tr></thead>
          <tbody>{low.map((r) => <tr key={r.medicineId}><td>{r.name}</td><td className="num">{r.stock}</td><td className="num">{r.reorderLevel}</td></tr>)}</tbody>
        </table>
      )}
      {tab === 'low' && !low.length && <Empty>Everything is above its reorder level.</Empty>}
    </>
  );
}
