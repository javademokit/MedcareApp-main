# Pharmacy Desk: React + Spring Boot + MongoDB

## Run it
1. **MongoDB (replica set, needed for transactions)**
   `docker compose up -d`
2. **Backend** (Java 17+, Maven)
   `cd backend && mvn spring-boot:run`  → http://localhost:8080 (Swagger: /swagger-ui.html)
3. **Frontend** (Node 18+)
   `cd frontend && npm install && npm run dev` → http://localhost:5173

## Demo logins (created on first start, change before real use)
| User | Password | Role |
|---|---|---|
| admin | Admin@12345 | ADMIN |
| manager | Manager@12345 | MANAGER |
| pharmacist | Pharma@12345 | PHARMACIST |
| cashier | Cashier@12345 | CASHIER |

Try: sign in as **cashier**, bill "Dolo 650" (OTC). Then try "Azithral 500" (Schedule H) and
see it refused, and complete it as **pharmacist**. "Alprax 0.25" (H1) fills the H1 register.

## What is built
- JWT login with role-based access on every endpoint
- Medicine master (schedule, GST, HSN, units per pack, reorder level)
- Batch-wise stock, FEFO selling, expiry blocking, quarantine, audited stock adjustments
- Supplier + purchase entry (GRN) with duplicate supplier-invoice check
- POS billing: MRP cap, role-based discount cap, GST-inclusive MRP split into CGST/SGST or IGST,
  financial-year invoice numbers, atomic stock deduction inside a Mongo transaction
- Schedule H/H1/X rules: pharmacist-only, patient and doctor mandatory, H1 and X/NDPS register lines auto-created
- Invoice print, cancel with stock restored (nothing is deleted), audit log, dashboard

## Not built yet (see the requirements MD)
Refresh tokens, 2FA, sales returns/credit notes against specific batches, purchase returns, customer ledger,
GSTR reports, report exports, file upload for prescriptions, optional AI module, offline mode.

## Before going live
- Set `JWT_SECRET`, remove the seeded demo users and data (`DataSeeder`), enable TLS
- Edit your store name, GSTIN and drug licence numbers in `frontend/src/config.js`
- Have your licensed pharmacist confirm register formats and retention periods for your state
