import { useEffect, useState } from 'react';
import api, { errMsg } from '../api';
import { useAuth } from '../auth';
import { dateTime, money } from '../fmt';
import Invoice from '../components/Invoice';
import { Empty } from '../components/ui';

export default function Sales() {
  const { hasRole } = useAuth();
  const [rows, setRows] = useState([]);
  const [open, setOpen] = useState(null);
  const [msg, setMsg] = useState(null);
  const load = () => api.get('/sales').then((r) => setRows(r.data));
  useEffect(() => { load(); }, []);

  const cancel = async (s) => {
    const reason = window.prompt(`Why are you cancelling ${s.invoiceNo}? Stock will be returned to the same batches.`);
    if (!reason) return;
    try { await api.post(`/sales/${s.id}/cancel`, { reason }); setMsg({ ok: true, t: `${s.invoiceNo} cancelled` }); load(); }
    catch (e) { setMsg({ ok: false, t: errMsg(e) }); }
  };

  return (
    <>
      <div className="head"><h1>Invoices</h1></div>
      {msg && <div className={`alert ${msg.ok ? 'ok' : 'err'}`}>{msg.t}</div>}
      <div className="scroll">
        <table className="tbl">
          <thead><tr><th>Invoice</th><th>Date</th><th>Customer</th><th>Payment</th><th className="num">Total</th><th>Status</th><th /></tr></thead>
          <tbody>
            {rows.map((s) => (
              <tr key={s.id}>
                <td>{s.invoiceNo}</td><td>{dateTime(s.invoiceDate)}</td><td>{s.customerName || 'Walk-in'}</td><td>{s.paymentMode}</td>
                <td className="num">{money(s.grandTotal)}</td><td><span className={`badge ${s.status}`}>{s.status === 'COMPLETED' ? 'Completed' : 'Cancelled'}</span></td>
                <td className="row">
                  <button className="link" onClick={() => setOpen(s)}>View</button>
                  {s.status === 'COMPLETED' && hasRole('ADMIN', 'MANAGER', 'PHARMACIST') && <button className="link" onClick={() => cancel(s)}>Cancel</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!rows.length && <Empty>No invoices yet. Create the first one from New bill.</Empty>}
      </div>
      {open && <Invoice sale={open} onClose={() => setOpen(null)} />}
    </>
  );
}
