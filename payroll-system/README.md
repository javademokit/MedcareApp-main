# Payroll Desk Backend

Attendance-driven payroll with salary structure templates and a monthly auto-run.

```
payroll-system/
  backend/    Spring Boot 3.3, Java 17, MongoDB, Lombok
```

The React UI has been migrated into the main app at
`../GitHub/appp/src/payrollDesk`. Keep the payroll backend here; start the main
React app from `../GitHub/appp`.

## How it works
- **Salary structures**: a template defines basic as % of gross, extra earnings (% of basic, % of gross, fixed, or balance) and deductions (% of basic with cap, % of gross, fixed).
- **Employees**: each has a department, role, monthly gross and assigned salary structure. The employee form offers hospital and support-role suggestions while allowing custom job titles.
- **Attendance**: the monthly register can be searched by employee name, ID, department or role. Only exceptions are stored. Weekdays default to present, Saturdays and Sundays are weekly offs.
- **Pay** = gross split by the structure, then prorated by paid days / days in month. Present, paid leave and weekly offs are paid, a half day counts 0.5, absent is unpaid.
- **Payroll run**: processing a month saves a payslip snapshot per employee and locks that month's attendance.
- **Auto-run**: `PayrollScheduler` runs at 6:00 pm on the last day of every month (cron `0 0 18 L * *`, zone from `payroll.timezone`). It can be switched off in the UI.

## Run it
Backend (needs JDK 17+, Maven, and MongoDB running locally):
```
cd /Users/rishabhaadi/Documents/payroll-system/backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run          # http://localhost:8080
```
Main React app (needs Node 18+; payroll API defaults to `http://localhost:8080/api`):
```
cd /Users/rishabhaadi/Documents/GitHub/appp
npm start                    # http://localhost:3000
```
The app's `.env.example` documents `REACT_APP_PAYROLL_API_URL` if the payroll
backend is hosted somewhere other than `http://localhost:8080/api`.
Demo structures, 10 employees and sample absences load on first backend start.

## API
| Method | Path | Purpose |
|---|---|---|
| GET | /api/employees | list employees |
| GET | /api/employees/roles | role suggestions for employee setup |
| POST/PUT | /api/employees, /api/employees/{id} | add or edit an employee |
| GET/POST | /api/structures, PUT/DELETE /api/structures/{id} | manage templates |
| POST | /api/structures/preview | full-month breakdown for a draft template |
| GET | /api/attendance?month=2026-11 | attendance register with employee ID, department, role and day statuses |
| PUT | /api/attendance | `{employeeId, date, status}` |
| GET | /api/payroll/{month} | live figures, or saved figures if processed |
| POST | /api/payroll/{month}/run | process the month now |
| GET | /api/payroll/runs | run history |
| GET/PUT | /api/settings/auto-run | `{enabled}` |

## Before going live
- Add authentication (Spring Security) and roles. There is none yet.
- Secure the API before exposing it beyond local development.
- Back up the MongoDB payroll database regularly.
- TDS is a flat % of gross here. Real income tax slabs, PF/ESI rules and professional tax by state need to be added to the calculator.
- If the server is down at 6:00 pm on the last day, the auto-run is missed. Add a startup catch-up check or run it manually.
- Build and serve the main React app from Nginx or another static host when deploying.
