import { useEffect, useState } from 'react';
import api, { errMsg } from '../api';
import { useAuth } from '../auth';
import { date, money } from '../fmt';
import { Empty, Field, Modal } from '../components/ui';

const blankItem = { medicineId: '', batchNo: '', mfgDate: '', expiryDate: '', packs: 1, freePacks: 0, rate: '', mrp: '' };

export default function Purchases() {
  const { hasRole } = useAuth();
  const canEdit = hasRole('ADMIN', 'MANAGER', 'STOREKEEPER');
  const [list, setList] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [meds, setMeds] = useState([]);
  const [open, setOpen] = useState(false);
  const [sup, setSup] = useState(null);
  const [h, setH] = useState({ supplierId: '', supplierInvoiceNo: '', invoiceDate: new Date().toISOString().slice(0, 10) });
  const [items, setItems] = useState([{ ...blankItem }]);
  const [err, setErr] = useState('');

  const load = () => {
    api.get('/purchases').then((r) => setList(r.data)).catch(() => {});
    api.get('/suppliers').then((r) => setSuppliers(r.data));
    api.get('/medicines', { params: { limit: 500 } }).then((r) => setMeds(r.data));
  };
  useEffect(load, []);
  const setItem = (i, p) => setItems((a) => a.map((x, j) => (j === i ? { ...x, ...p } : x)));

  const saveSupplier = async (e) => {
    e.preventDefault();
    try { const { data } = await api.post('/suppliers', sup); setSup(null); load(); setH((x) => ({ ...x, supplierId: data.id })); }
    catch (x) { setErr(errMsg(x)); }
  };

  const submit = async (e) => {
    e.preventDefault(); setErr('');
    try {
      await api.post('/purchases/grn', {
        ...h,
        items: items.map((i) => ({ ...i, packs: +i.packs, freePacks: +i.freePacks, rate: +i.rate, mrp: +i.mrp, mfgDate: i.mfgDate || null })),
      });
      setOpen(false); setItems([{ ...blankItem }]); load();
    } catch (x) { setErr(errMsg(x)); }
  };

  return (
    <>
      <div className="head">
        <h1>Purchases</h1>
        <div className="row">
          {canEdit && <button className="btn ghost" onClick={() => { setErr(''); setSup({ name: '', gstin: '', drugLicenceNo: '', phone: '', address: '' }); }}>Add supplier</button>}
          {canEdit && <button className="btn" onClick={() => { setErr(''); setOpen(true); }}>Receive stock</button>}
        </div>
      </div>
      <div className="scroll">
        <table className="tbl">
          <thead><tr><th>Received</th><th>Supplier</th><th>Supplier invoice</th><th className="num">Items</th><th className="num">Amount</th><th>Entered by</th></tr></thead>
          <tbody>
            {list.map((g) => (
              <tr key={g.id}><td>{date(g.createdAt)}</td><td>{g.supplierName}</td><td>{g.supplierInvoiceNo}</td><td className="num">{g.items.length}</td><td className="num">{money(g.total)}</td><td>{g.createdBy}</td></tr>
            ))}
          </tbody>
        </table>
        {!list.length && <Empty>No stock received yet. Use Receive stock to enter a supplier invoice.</Empty>}
      </div>

      {sup && (
        <Modal title="Add supplier" onClose={() => setSup(null)}>
          {err && <div className="alert err">{err}</div>}
          <form className="form" onSubmit={saveSupplier}>
            <Field label="Name" full><input required value={sup.name} onChange={(e) => setSup({ ...sup, name: e.target.value })} /></Field>
            <Field label="GSTIN"><input value={sup.gstin} onChange={(e) => setSup({ ...sup, gstin: e.target.value })} /></Field>
            <Field label="Drug licence no."><input value={sup.drugLicenceNo} onChange={(e) => setSup({ ...sup, drugLicenceNo: e.target.value })} /></Field>
            <Field label="Phone"><input value={sup.phone} onChange={(e) => setSup({ ...sup, phone: e.target.value })} /></Field>
            <Field label="Address"><input value={sup.address} onChange={(e) => setSup({ ...sup, address: e.target.value })} /></Field>
            <div className="full"><button className="btn">Save supplier</button></div>
          </form>
        </Modal>
      )}

      {open && (
        <Modal title="Receive stock against a supplier invoice" wide onClose={() => setOpen(false)}>
          {err && <div className="alert err">{err}</div>}
          <form onSubmit={submit} className="stack">
            <div className="form" style={{ gridTemplateColumns: '2fr 1fr 1fr' }}>
              <Field label="Supplier"><select required value={h.supplierId} onChange={(e) => setH({ ...h, supplierId: e.target.value })}><option value="">Select supplier</option>{suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
              <Field label="Supplier invoice no."><input required value={h.supplierInvoiceNo} onChange={(e) => setH({ ...h, supplierInvoiceNo: e.target.value })} /></Field>
              <Field label="Invoice date"><input type="date" required value={h.invoiceDate} onChange={(e) => setH({ ...h, invoiceDate: e.target.value })} /></Field>
            </div>
            <div className="scroll">
              <table className="tbl">
                <thead><tr><th>Medicine</th><th>Batch</th><th>Mfg</th><th>Expiry</th><th>Packs</th><th>Free</th><th>Rate</th><th>MRP</th><th /></tr></thead>
                <tbody>
                  {items.map((it, i) => (
                    <tr key={i}>
                      <td style={{ minWidth: 170 }}><select required value={it.medicineId} onChange={(e) => setItem(i, { medicineId: e.target.value })}><option value="">Select</option>{meds.map((m) => <option key={m.id} value={m.id}>{m.brandName}</option>)}</select></td>
                      <td><input required style={{ width: 90 }} value={it.batchNo} onChange={(e) => setItem(i, { batchNo: e.target.value })} /></td>
                      <td><input type="date" value={it.mfgDate} onChange={(e) => setItem(i, { mfgDate: e.target.value })} /></td>
                      <td><input type="date" required value={it.expiryDate} onChange={(e) => setItem(i, { expiryDate: e.target.value })} /></td>
                      <td><input type="number" min="1" required style={{ width: 70 }} value={it.packs} onChange={(e) => setItem(i, { packs: e.target.value })} /></td>
                      <td><input type="number" min="0" style={{ width: 60 }} value={it.freePacks} onChange={(e) => setItem(i, { freePacks: e.target.value })} /></td>
                      <td><input type="number" step="0.01" min="0" required style={{ width: 80 }} value={it.rate} onChange={(e) => setItem(i, { rate: e.target.value })} /></td>
                      <td><input type="number" step="0.01" min="0" required style={{ width: 80 }} value={it.mrp} onChange={(e) => setItem(i, { mrp: e.target.value })} /></td>
                      <td>{items.length > 1 && <button type="button" className="link" onClick={() => setItems((a) => a.filter((_, j) => j !== i))}>Remove</button>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="row">
              <button type="button" className="btn ghost" onClick={() => setItems((a) => [...a, { ...blankItem }])}>Add another item</button>
              <span className="grow" />
              <button className="btn">Save and add to stock</button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}
