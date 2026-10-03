package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.nursing.NurseProfile;
import com.example.MedcareApp.Entity.payroll.EmployeeSalary;
import com.example.MedcareApp.Entity.payroll.DoctorDispute;
import com.example.MedcareApp.Entity.payroll.DoctorEarning;
import com.example.MedcareApp.Entity.payroll.DoctorPayProfile;
import com.example.MedcareApp.Entity.payroll.DoctorRateCard;
import com.example.MedcareApp.Entity.payroll.NurseAttendanceSummary;
import com.example.MedcareApp.Entity.payroll.NurseWardAllowance;
import com.example.MedcareApp.Entity.payroll.PayrollAdjustment;
import com.example.MedcareApp.Entity.payroll.PayrollAuditLog;
import com.example.MedcareApp.Entity.payroll.PayrollCycle;
import com.example.MedcareApp.Entity.payroll.PayrollLoan;
import com.example.MedcareApp.Entity.payroll.PayrollOvertime;
import com.example.MedcareApp.Entity.payroll.SalaryComponent;
import com.example.MedcareApp.Entity.payroll.SalaryStructure;
import com.example.MedcareApp.Entity.payroll.StatutoryConfig;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.Interafce.nursing.NurseProfileRepository;
import com.example.MedcareApp.Interafce.nursing.NurseShiftRosterRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PayrollService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Set<String> EMPLOYEE_TYPES = Set.of("DOCTOR", "NURSE");
    private static final Set<String> COMPONENT_TYPES = Set.of("EARNING", "DEDUCTION");
    private static final Set<String> CALCULATIONS = Set.of("FIXED", "PERCENT_OF_BASIC", "PER_UNIT", "FORMULA");
    private final MongoTemplate mongo;
    private final DoctorRepository doctorRepository;
    private final NurseProfileRepository nurseRepository;
    private final UserRepository userRepository;
    private final NurseShiftRosterRepository rosterRepository;

    public PayrollService(
            MongoTemplate mongo,
            DoctorRepository doctorRepository,
            NurseProfileRepository nurseRepository,
            UserRepository userRepository,
            NurseShiftRosterRepository rosterRepository) {
        this.mongo = mongo;
        this.doctorRepository = doctorRepository;
        this.nurseRepository = nurseRepository;
        this.userRepository = userRepository;
        this.rosterRepository = rosterRepository;
    }

    public List<SalaryComponent> components(String actor) {
        audit(actor, "READ", "COMPONENTS", "all");
        return mongo.findAll(SalaryComponent.class);
    }

    public SalaryComponent saveComponent(SalaryComponent component, String actor) {
        if (component == null || !StringUtils.hasText(component.getName())
                || !StringUtils.hasText(component.getCode()) || !StringUtils.hasText(component.getType())
                || !StringUtils.hasText(component.getCalculation()) || !StringUtils.hasText(component.getAppliesTo())) {
            throw badRequest("Name, code, type, calculation, and applies-to are required");
        }
        component.setCode(component.getCode().trim().toUpperCase(Locale.ROOT));
        component.setType(normalize(component.getType()));
        component.setCalculation(normalize(component.getCalculation()));
        component.setAppliesTo(normalize(component.getAppliesTo()));
        if (!COMPONENT_TYPES.contains(component.getType()) || !CALCULATIONS.contains(component.getCalculation())
                || !Set.of("DOCTOR", "NURSE", "BOTH").contains(component.getAppliesTo())) {
            throw badRequest("Select a supported component type, calculation, and employee group");
        }
        if ("FORMULA".equals(component.getCalculation())) {
            if (component.getFormula() == null || component.getFormula().length() > 200) {
                throw badRequest("Payroll formula must be 200 characters or fewer");
            }
            try {
                PayrollFormula.evaluate(component.getFormula(), BigDecimal.ONE, BigDecimal.ONE);
            } catch (IllegalArgumentException exception) {
                throw badRequest("Formula must use only numbers, BASIC, UNITS, parentheses, and + - * /");
            }
        }
        if (mongo.findAll(SalaryComponent.class).stream().anyMatch(existing ->
                component.getCode().equalsIgnoreCase(existing.getCode())
                        && !Objects.equals(existing.getId(), component.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Component code already exists");
        }
        component.setUpdatedAt(Instant.now());
        SalaryComponent saved = mongo.save(component);
        audit(actor, "WRITE", "COMPONENT", saved.getId());
        return saved;
    }

    public List<SalaryStructure> structures(String actor) {
        audit(actor, "READ", "STRUCTURES", "all");
        return mongo.findAll(SalaryStructure.class);
    }

    public SalaryStructure createStructure(SalaryStructure structure, String actor) {
        validateStructure(structure);
        structure.setId(null);
        structure.setVersion(1);
        structure.setCreatedAt(Instant.now());
        structure.setUpdatedAt(Instant.now());
        SalaryStructure saved = mongo.save(structure);
        audit(actor, "WRITE", "STRUCTURE", saved.getId());
        return saved;
    }

    public SalaryStructure reviseStructure(String id, SalaryStructure revision, String actor) {
        SalaryStructure previous = require(SalaryStructure.class, id, "Salary structure");
        validateStructure(revision);
        revision.setId(null);
        revision.setVersion(previous.getVersion() + 1);
        revision.setCreatedAt(Instant.now());
        revision.setUpdatedAt(Instant.now());
        SalaryStructure saved = mongo.save(revision);
        audit(actor, "REVISION", "STRUCTURE", saved.getId());
        return saved;
    }

    public List<EmployeeSalary> salaries(String type, String actor) {
        String normalizedType = validateEmployeeType(type);
        audit(actor, "READ", "SALARIES_" + normalizedType, "all");
        return mongo.find(Query.query(Criteria.where("employeeType").is(normalizedType))
                .with(Sort.by(Sort.Direction.DESC, "effectiveFrom")), EmployeeSalary.class);
    }

    public EmployeeSalary assignSalary(EmployeeSalary salary, String actor) {
        if (salary == null || !StringUtils.hasText(salary.getEmployeeType())
                || !StringUtils.hasText(salary.getEmployeeId()) || !StringUtils.hasText(salary.getStructureId())
                || salary.getBasic() == null || salary.getBasic().signum() < 0 || salary.getEffectiveFrom() == null) {
            throw badRequest("Employee, structure, non-negative basic salary, and effective date are required");
        }
        salary.setEmployeeType(validateEmployeeType(salary.getEmployeeType()));
        requireEmployee(salary.getEmployeeType(), salary.getEmployeeId());
        SalaryStructure structure = require(SalaryStructure.class, salary.getStructureId(), "Salary structure");
        if (!structure.isActive() || !applies(structure.getAppliesTo(), salary.getEmployeeType())) {
            throw badRequest("Salary structure is inactive or not applicable to this employee type");
        }
        List<EmployeeSalary> current = mongo.find(Query.query(Criteria.where("employeeType").is(salary.getEmployeeType())
                .and("employeeId").is(salary.getEmployeeId()).and("active").is(true)), EmployeeSalary.class);
        for (EmployeeSalary prior : current) {
            if (prior.getEffectiveFrom() != null && !prior.getEffectiveFrom().isAfter(salary.getEffectiveFrom())) {
                prior.setEffectiveTo(salary.getEffectiveFrom().minusDays(1));
                prior.setActive(false);
                mongo.save(prior);
            } else if (prior.getEffectiveFrom() != null) {
                throw badRequest("Salary revisions must be entered in effective-date order");
            }
        }
        salary.setId(null);
        salary.setCreatedBy(actor);
        salary.setActive(true);
        EmployeeSalary saved = mongo.save(salary);
        audit(actor, "ASSIGN", "EMPLOYEE_SALARY", saved.getId());
        return saved;
    }

    public List<StatutoryConfig> statutoryConfigs(String actor) {
        audit(actor, "READ", "STATUTORY_CONFIG", "all");
        return mongo.findAll(StatutoryConfig.class);
    }

    public StatutoryConfig saveStatutoryConfig(StatutoryConfig config, String actor) {
        if (config == null || !StringUtils.hasText(config.getName()) || !StringUtils.hasText(config.getAppliesTo())
                || config.getRate() == null || config.getRate().signum() < 0
                || config.getEffectiveFrom() == null
                || !Set.of("DOCTOR", "NURSE", "BOTH").contains(normalize(config.getAppliesTo()))) {
            throw badRequest("Statutory name, applicable employee type, non-negative rate, and effective date are required");
        }
        if (config.getCeiling() != null && config.getCeiling().signum() < 0) {
            throw badRequest("Statutory ceiling cannot be negative");
        }
        config.setAppliesTo(normalize(config.getAppliesTo()));
        config.setCreatedAt(Instant.now());
        StatutoryConfig saved = mongo.save(config);
        audit(actor, "WRITE", "STATUTORY_CONFIG", saved.getId());
        return saved;
    }

    public List<DoctorPayProfile> doctorPayProfiles(String actor) {
            audit(actor, "READ", "DOCTOR_PAY_PROFILES", "all");
            return mongo.findAll(DoctorPayProfile.class);
        }

        public DoctorPayProfile saveDoctorPayProfile(DoctorPayProfile profile, String actor) {
            if (profile == null || !StringUtils.hasText(profile.getDoctorId())
                    || !StringUtils.hasText(profile.getDoctorType()) || !StringUtils.hasText(profile.getPayModel())
                    || profile.getEffectiveFrom() == null) {
                throw badRequest("Doctor, employment type, pay model, and effective date are required");
            }
            if (!doctorRepository.existsById(profile.getDoctorId())
                    || !Set.of("PERMANENT", "VISITING", "RESIDENT", "PART_TIME").contains(normalize(profile.getDoctorType()))
                    || !Set.of("FIXED", "CONSULTATION_SHARE", "PROCEDURE_SHARE", "PER_VISIT", "HYBRID")
                    .contains(normalize(profile.getPayModel()))) {
                throw badRequest("Select a valid doctor, employment type, and pay model");
            }
            if (profile.getEffectiveTo() != null && profile.getEffectiveTo().isBefore(profile.getEffectiveFrom())) {
                throw badRequest("Doctor profile end date cannot be before its effective date");
            }
            requireNonNegative(profile.getGuaranteedMinimum(), "Minimum guarantee");
            requireNonNegative(profile.getVariableCap(), "Variable earnings cap");
            requireNonNegative(profile.getOnCallAllowance(), "On-call allowance");
            List<DoctorPayProfile> existing = mongo.find(Query.query(Criteria.where("doctorId")
                    .is(profile.getDoctorId())), DoctorPayProfile.class);
            DoctorPayProfile priorProfile = existing.stream()
                    .filter(prior -> prior.getEffectiveFrom() != null)
                    .max(Comparator.comparing(DoctorPayProfile::getEffectiveFrom)).orElse(null);
            if (priorProfile != null) {
                if (priorProfile.getEffectiveFrom().equals(profile.getEffectiveFrom())) {
                    throw conflict("A doctor pay profile already exists for this effective date");
                }
                if (priorProfile.getEffectiveFrom().isAfter(profile.getEffectiveFrom())) {
                    throw badRequest("Doctor pay profile revisions must be entered in effective-date order");
                }
                priorProfile.setEffectiveTo(profile.getEffectiveFrom().minusDays(1));
                mongo.save(priorProfile);
            }
            profile.setId(null);
            profile.setDoctorType(normalize(profile.getDoctorType()));
            profile.setPayModel(normalize(profile.getPayModel()));
            profile.setCreatedAt(Instant.now());
            profile.setUpdatedAt(Instant.now());
            DoctorPayProfile saved = mongo.save(profile);
            audit(actor, "WRITE", "DOCTOR_PAY_PROFILE", saved.getId());
            return saved;
        }

        public List<DoctorRateCard> doctorRateCards(String actor) {
            audit(actor, "READ", "DOCTOR_RATE_CARDS", "all");
            return mongo.findAll(DoctorRateCard.class);
        }

        public DoctorRateCard saveDoctorRateCard(DoctorRateCard rate, String actor) {
            if (rate == null || !StringUtils.hasText(rate.getRateType())
                    || !StringUtils.hasText(rate.getServiceCode()) || rate.getEffectiveFrom() == null) {
                throw badRequest("Rate type, service/procedure code, and effective date are required");
            }
            rate.setRateType(normalize(rate.getRateType()));
            if (!Set.of("CONSULTATION", "PROCEDURE").contains(rate.getRateType())) {
                throw badRequest("Rate type must be CONSULTATION or PROCEDURE");
            }
            requireNonNegative(rate.getFee(), "Fee");
            requireNonNegative(rate.getFixedAmount(), "Fixed share");
            requireNonNegative(rate.getSharePercent(), "Share percentage");
            if ("CONSULTATION".equals(rate.getRateType())) {
                if (!StringUtils.hasText(rate.getDoctorId()) || !doctorRepository.existsById(rate.getDoctorId())
                        || !Set.of("OPD", "IPD", "EMERGENCY").contains(normalize(rate.getVisitType()))
                        || rate.getFee() == null || rate.getSharePercent() == null
                        || rate.getSharePercent().compareTo(new BigDecimal("100")) > 0) {
                    throw badRequest("Consultation rate requires a doctor, OPD/IPD/EMERGENCY type, fee, and share up to 100%");
                }
                rate.setVisitType(normalize(rate.getVisitType()));
            } else {
                if (!StringUtils.hasText(rate.getDoctorRole())
                        || !Set.of("SURGEON", "ASSISTANT", "ANESTHETIST").contains(normalize(rate.getDoctorRole()))
                        || (rate.getSharePercent() == null && rate.getFixedAmount() == null)) {
                    throw badRequest("Procedure rate requires surgeon, assistant, or anesthetist role and a share or fixed amount");
                }
                rate.setDoctorRole(normalize(rate.getDoctorRole()));
                if (rate.getSharePercent() != null && rate.getSharePercent().compareTo(new BigDecimal("100")) > 0) {
                    throw badRequest("Procedure share cannot exceed 100%");
                }
                if (rate.getSharePercent() != null) {
                    BigDecimal totalShare = mongo.findAll(DoctorRateCard.class).stream()
                            .filter(existing -> "PROCEDURE".equals(existing.getRateType()))
                            .filter(existing -> existing.getServiceCode().equalsIgnoreCase(rate.getServiceCode()))
                            .filter(existing -> Objects.equals(existing.getEffectiveFrom(), rate.getEffectiveFrom()))
                            .filter(existing -> !Objects.equals(existing.getId(), rate.getId()))
                            .map(DoctorRateCard::getSharePercent).filter(Objects::nonNull)
                            .reduce(rate.getSharePercent(), BigDecimal::add);
                    if (totalShare.compareTo(new BigDecimal("100")) > 0) {
                        throw badRequest("Procedure doctor shares cannot total more than 100%");
                    }
                }
                if (rate.getEffectiveTo() != null && rate.getEffectiveTo().isBefore(rate.getEffectiveFrom())) {
                    throw badRequest("Rate-card end date cannot be before its effective date");
                }
            }
            List<DoctorRateCard> sameScope = mongo.findAll(DoctorRateCard.class).stream()
                    .filter(existing -> Objects.equals(existing.getDoctorId(), rate.getDoctorId()))
                    .filter(existing -> Objects.equals(existing.getRateType(), rate.getRateType()))
                    .filter(existing -> existing.getServiceCode().equalsIgnoreCase(rate.getServiceCode()))
                    .filter(existing -> Objects.equals(existing.getVisitType(), rate.getVisitType()))
                    .filter(existing -> Objects.equals(existing.getDepartment(), rate.getDepartment()))
                    .filter(existing -> Objects.equals(existing.getDoctorRole(), rate.getDoctorRole()))
                    .toList();
            DoctorRateCard priorRate = sameScope.stream()
                    .filter(prior -> prior.getEffectiveFrom() != null)
                    .max(Comparator.comparing(DoctorRateCard::getEffectiveFrom)).orElse(null);
            if (priorRate != null) {
                if (priorRate.getEffectiveFrom().equals(rate.getEffectiveFrom())) {
                    throw conflict("A rate card already exists for this scope and effective date");
                }
                if (priorRate.getEffectiveFrom().isAfter(rate.getEffectiveFrom())) {
                    throw badRequest("Doctor rate-card revisions must be entered in effective-date order");
                }
                priorRate.setEffectiveTo(rate.getEffectiveFrom().minusDays(1));
                mongo.save(priorRate);
            }
            rate.setId(null);
            rate.setCreatedAt(Instant.now());
            DoctorRateCard saved = mongo.save(rate);
            audit(actor, "WRITE", "DOCTOR_RATE_CARD", saved.getId());
            return saved;
        }

        public List<DoctorEarning> doctorEarnings(String doctorId, String actor, boolean selfService) {
            if (selfService) {
                user account = userRepository.findAllByEmailIdIgnoreCase(actor).stream().findFirst()
                        .orElseThrow(() -> notFound("Account"));
                doctorId = account.getDoctorId();
                if (!StringUtils.hasText(doctorId) || !account.getRoles().contains("DOCTOR")) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor earnings are available only to the linked doctor");
                }
            }
            String id = doctorId;
            audit(actor, selfService ? "READ_SELF" : "READ", "DOCTOR_EARNINGS", id);
            return mongo.find(Query.query(Criteria.where("doctorId").is(id)), DoctorEarning.class);
        }

        public List<DoctorEarning> allDoctorEarnings(String actor) {
            audit(actor, "READ", "DOCTOR_EARNINGS", "all");
            return mongo.findAll(DoctorEarning.class);
        }

        public DoctorEarning captureDoctorEarning(DoctorEarning earning, String actor) {
            if (earning == null || !StringUtils.hasText(earning.getDoctorId())
                    || !StringUtils.hasText(earning.getSourceType()) || !StringUtils.hasText(earning.getSourceId())
                    || !StringUtils.hasText(earning.getService()) || earning.getServiceDate() == null
                    || !StringUtils.hasText(earning.getBillingStatus()) || earning.getBilledAmount() == null
                    || earning.getBilledAmount().signum() < 0) {
                throw badRequest("Doctor, source reference, service, date, billing status, and billed amount are required");
            }
            if (!doctorRepository.existsById(earning.getDoctorId())) throw notFound("Doctor profile");
            earning.setSourceType(normalize(earning.getSourceType()));
            if (!Set.of("CONSULTATION", "VISIT", "PROCEDURE", "ON_CALL").contains(earning.getSourceType())) {
                throw badRequest("Source type must be CONSULTATION, VISIT, PROCEDURE, or ON_CALL");
            }
            boolean verifiedSource = "ON_CALL".equalsIgnoreCase(earning.getSourceType())
                    ? "COMPLETED".equals(normalize(earning.getBillingStatus()))
                    : Set.of("PAID", "FINALIZED").contains(normalize(earning.getBillingStatus()));
            if (!verifiedSource) {
                throw badRequest("Doctor earnings require a PAID/FINALIZED bill or a COMPLETED on-call duty");
            }
            if (Set.of("CONSULTATION", "VISIT").contains(earning.getSourceType())) {
                if (!Set.of("OPD", "IPD", "EMERGENCY").contains(normalize(earning.getVisitType()))) {
                    throw badRequest("Consultation earnings require an OPD, IPD, or EMERGENCY visit type");
                }
                earning.setVisitType(normalize(earning.getVisitType()));
            }
            boolean duplicateSource = mongo.findAll(DoctorEarning.class).stream()
                    .anyMatch(existing -> earning.getSourceId().equals(existing.getSourceId())
                            && earning.getDoctorId().equals(existing.getDoctorId())
                            && !Objects.equals(existing.getId(), earning.getId()));
            if (duplicateSource) throw conflict("This billed service is already included for this doctor");
            if (isEarningPeriodCalculated(earning.getDoctorId(), earning.getServiceDate())) {
                throw conflict("This service date is already included in a calculated payroll cycle");
            }
            earning.setBillingStatus(normalize(earning.getBillingStatus()));
            if ("PROCEDURE".equals(earning.getSourceType())) {
                if (!StringUtils.hasText(earning.getDoctorRole())
                        || !Set.of("SURGEON", "ASSISTANT", "ANESTHETIST")
                        .contains(normalize(earning.getDoctorRole()))) {
                    throw badRequest("Procedure earnings require the doctor's role for this procedure");
                }
                earning.setDoctorRole(normalize(earning.getDoctorRole()));
            }
            earning.setDoctorShare(calculateDoctorShare(earning));
            earning.setStatus("INCLUDED");
            earning.setId(null);
            earning.setCreatedAt(Instant.now());
            earning.setUpdatedAt(Instant.now());
            DoctorEarning saved = mongo.save(earning);
            audit(actor, "CAPTURE", "DOCTOR_EARNING", saved.getId());
            return saved;
        }

        public DoctorEarning reverseDoctorEarning(String id, String reason, String actor) {
            if (!StringUtils.hasText(reason)) throw badRequest("A refund or cancellation reason is required");
            DoctorEarning earning = require(DoctorEarning.class, id, "Doctor earning");
            if (!"INCLUDED".equals(earning.getStatus())) {
                throw conflict("Resolve any open dispute before reversing this earning");
            }
            if (isEarningPeriodCalculated(earning.getDoctorId(), earning.getServiceDate())) {
                throw conflict("This earning is already included in a payroll cycle; use a future-cycle adjustment");
            }
            earning.setStatus("REVERSED");
            earning.setReversalReason(reason.trim());
            earning.setUpdatedAt(Instant.now());
            DoctorEarning saved = mongo.save(earning);
            audit(actor, "REVERSE", "DOCTOR_EARNING", id);
            return saved;
        }

        private boolean isEarningPeriodCalculated(String doctorId, LocalDate serviceDate) {
            return mongo.findAll(PayrollCycle.class).stream()
                    .filter(cycle -> "DOCTOR".equals(cycle.getEmployeeType()))
                    .filter(cycle -> cycle.getFromDate() != null && cycle.getToDate() != null
                            && !serviceDate.isBefore(cycle.getFromDate()) && !serviceDate.isAfter(cycle.getToDate()))
                    .filter(cycle -> !"DRAFT".equals(cycle.getStatus()))
                    .anyMatch(cycle -> cycle.getEntries().stream()
                            .anyMatch(entry -> doctorId.equals(entry.getEmployeeId())));
        }

        private BigDecimal calculateDoctorShare(DoctorEarning earning) {
            DoctorPayProfile profile = currentDoctorPayProfile(earning.getDoctorId(), earning.getServiceDate());
            if (profile == null) throw badRequest("Configure an effective doctor pay profile before capturing earnings");
            if ("ON_CALL".equals(earning.getSourceType())) {
                BigDecimal allowance = profile.getOnCallAllowance();
                if (allowance == null || allowance.signum() < 0) {
                    throw badRequest("Configure a non-negative on-call allowance for this doctor");
                }
                return money(allowance);
            }
            String model = normalize(profile.getPayModel());
            boolean supportedModel = switch (earning.getSourceType()) {
                case "CONSULTATION", "VISIT" -> Set.of("CONSULTATION_SHARE", "PER_VISIT", "HYBRID").contains(model);
                case "PROCEDURE" -> Set.of("PROCEDURE_SHARE", "HYBRID").contains(model);
                default -> false;
            };
            if (!supportedModel) throw badRequest("The doctor's pay model does not allow this earning type");
            List<DoctorRateCard> rates = mongo.findAll(DoctorRateCard.class).stream()
                    .filter(rate -> rate.getServiceCode().equalsIgnoreCase(earning.getService()))
                    .filter(rate -> rate.getDoctorId() == null || rate.getDoctorId().equals(earning.getDoctorId()))
                    .filter(rate -> effective(rate.getEffectiveFrom(), rate.getEffectiveTo(), earning.getServiceDate()))
                    .filter(rate -> ("CONSULTATION".equals(rate.getRateType())
                            && ("CONSULTATION".equals(earning.getSourceType()) || "VISIT".equals(earning.getSourceType()))
                            && rate.getVisitType().equals(normalize(earning.getVisitType()))
                            && (rate.getDepartment() == null || rate.getDepartment().isBlank()
                                || rate.getDepartment().equalsIgnoreCase(earning.getDepartment())))
                            || ("PROCEDURE".equals(rate.getRateType()) && "PROCEDURE".equals(earning.getSourceType())
                                && earning.getDoctorRole().equals(rate.getDoctorRole())))
                    .toList();
            List<DoctorRateCard> doctorSpecificRates = rates.stream()
                    .filter(rate -> earning.getDoctorId().equals(rate.getDoctorId())).toList();
            if (!doctorSpecificRates.isEmpty()) rates = doctorSpecificRates;
            if (rates.size() != 1) throw badRequest("An unambiguous effective doctor rate card is required");
            DoctorRateCard rate = rates.get(0);
            if (rate.getFixedAmount() != null && "PROCEDURE".equals(rate.getRateType())) return money(rate.getFixedAmount());
            if (rate.getSharePercent() == null) throw badRequest("Rate card requires a share percentage or fixed amount");
            return money(earning.getBilledAmount().multiply(rate.getSharePercent())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        }

        public DoctorDispute disputeDoctorEarning(String earningId, String reason, String actor) {
            if (!StringUtils.hasText(reason)) throw badRequest("Enter a reason for the earnings dispute");
            user account = userRepository.findAllByEmailIdIgnoreCase(actor).stream().findFirst()
                    .orElseThrow(() -> notFound("Account"));
            DoctorEarning earning = require(DoctorEarning.class, earningId, "Doctor earning");
            if (!account.getRoles().contains("DOCTOR") || !earning.getDoctorId().equals(account.getDoctorId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may dispute only your own earnings");
            }
            if (!"INCLUDED".equals(earning.getStatus())) {
                throw conflict("Only an included earning line can be disputed");
            }
            boolean existingOpenDispute = mongo.findAll(DoctorDispute.class).stream()
                    .anyMatch(dispute -> earningId.equals(dispute.getEarningId())
                            && "OPEN".equals(dispute.getStatus()));
            if (existingOpenDispute) throw conflict("This earning already has an open dispute");
            DoctorDispute dispute = new DoctorDispute();
            dispute.setEarningId(earningId);
            dispute.setDoctorId(earning.getDoctorId());
            dispute.setReason(reason.trim());
            earning.setStatus("DISPUTED");
            mongo.save(earning);
            DoctorDispute saved = mongo.save(dispute);
            audit(actor, "DISPUTE", "DOCTOR_EARNING", earningId);
            return saved;
        }

        public List<DoctorDispute> doctorDisputes(String actor) {
            audit(actor, "READ", "DOCTOR_DISPUTES", "all");
            return mongo.findAll(DoctorDispute.class);
        }

        public DoctorDispute resolveDoctorDispute(String id, String resolution, String actor) {
            if (!StringUtils.hasText(resolution)) throw badRequest("A dispute resolution note is required");
            DoctorDispute dispute = require(DoctorDispute.class, id, "Doctor dispute");
            if (!"OPEN".equals(dispute.getStatus())) throw conflict("This doctor dispute has already been resolved");
            dispute.setStatus("RESOLVED");
            dispute.setResolution(resolution.trim());
            dispute.setResolvedBy(actor);
            dispute.setResolvedAt(Instant.now());
            DoctorEarning earning = require(DoctorEarning.class, dispute.getEarningId(), "Doctor earning");
            earning.setStatus("INCLUDED");
            earning.setUpdatedAt(Instant.now());
            mongo.save(earning);
            DoctorDispute saved = mongo.save(dispute);
            audit(actor, "RESOLVE", "DOCTOR_DISPUTE", id);
            return saved;
        }

        public List<NurseWardAllowance> wardAllowances(String actor) {
            audit(actor, "READ", "NURSE_WARD_ALLOWANCES", "all");
            return mongo.findAll(NurseWardAllowance.class);
        }

        public NurseWardAllowance saveWardAllowance(NurseWardAllowance allowance, String actor) {
            if (allowance == null || !StringUtils.hasText(allowance.getWardId())
                    || allowance.getAmount() == null || allowance.getAmount().signum() < 0
                    || allowance.getEffectiveFrom() == null || !Set.of("PER_DAY", "PER_MONTH")
                    .contains(normalize(allowance.getBasis()))) {
                throw badRequest("Ward, non-negative allowance, PER_DAY/PER_MONTH basis, and effective date are required");
            }
            allowance.setBasis(normalize(allowance.getBasis()));
            boolean duplicateRevision = mongo.findAll(NurseWardAllowance.class).stream()
                    .anyMatch(existing -> allowance.getWardId().equals(existing.getWardId())
                            && allowance.getEffectiveFrom().equals(existing.getEffectiveFrom()));
            if (duplicateRevision) throw conflict("A ward allowance already exists for this effective date");
            allowance.setId(null);
            NurseWardAllowance saved = mongo.save(allowance);
            audit(actor, "WRITE", "NURSE_WARD_ALLOWANCE", saved.getId());
            return saved;
        }

        private void requireNonNegative(BigDecimal value, String name) {
            if (value != null && value.signum() < 0) throw badRequest(name + " cannot be negative");
        }

        private boolean effective(LocalDate from, LocalDate to, LocalDate date) {
            return from != null && !from.isAfter(date) && (to == null || !to.isBefore(date));
        }

        private DoctorPayProfile currentDoctorPayProfile(String doctorId, LocalDate date) {
            return mongo.find(Query.query(Criteria.where("doctorId").is(doctorId)), DoctorPayProfile.class).stream()
                    .filter(profile -> effective(profile.getEffectiveFrom(), profile.getEffectiveTo(), date))
                    .max(Comparator.comparing(DoctorPayProfile::getEffectiveFrom)).orElse(null);
        }
    public PayrollCycle createCycle(String type, int month, int year, String actor) {
        String employeeType = validateEmployeeType(type);
        if (month < 1 || month > 12 || year < 2000 || year > 9999) {
            throw badRequest("Enter a valid payroll month and year");
        }
        Query duplicateQuery = Query.query(Criteria.where("employeeType").is(employeeType)
                .and("month").is(month).and("year").is(year));
        if (mongo.exists(duplicateQuery, PayrollCycle.class)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A payroll cycle already exists for this employee group and month");
        }
        YearMonth period = YearMonth.of(year, month);
        PayrollCycle cycle = new PayrollCycle();
        cycle.setEmployeeType(employeeType);
        cycle.setMonth(month);
        cycle.setYear(year);
        cycle.setFromDate(period.atDay(1));
        cycle.setToDate(period.atEndOfMonth());
        cycle.setRunBy(actor);
        PayrollCycle saved = mongo.save(cycle);
        audit(actor, "CREATE", "PAYROLL_CYCLE", saved.getId());
        return saved;
    }

    public PayrollCycle calculateCycle(String cycleId, String actor) {
        PayrollCycle cycle = require(PayrollCycle.class, cycleId, "Payroll cycle");
        if (!"DRAFT".equals(cycle.getStatus())) {
            throw conflict("Only a draft payroll cycle can be calculated");
        }
        boolean hasPendingAdjustment = mongo.exists(Query.query(Criteria.where("cycleId").is(cycleId)
                .and("status").is("PENDING")), PayrollAdjustment.class);
        if (hasPendingAdjustment) {
            throw conflict("Approve or reject all pending payroll adjustments before calculation");
        }
        List<EmployeeSalary> assignments = mongo.find(Query.query(Criteria.where("employeeType")
                .is(cycle.getEmployeeType())
                .and("effectiveFrom").lte(cycle.getToDate())
                .orOperator(Criteria.where("effectiveTo").is(null),
                        Criteria.where("effectiveTo").gte(cycle.getFromDate()))), EmployeeSalary.class);
        if (assignments.isEmpty()) {
            throw badRequest("Assign effective-dated salary structures before calculating payroll");
        }
        List<PayrollCycle.Entry> entries = new ArrayList<>();
        List<String> exceptions = new ArrayList<>();
        BigDecimal grossTotal = ZERO;
        BigDecimal deductionsTotal = ZERO;
        BigDecimal netTotal = ZERO;
        for (EmployeeSalary assignment : assignments) {
            PayrollCycle.Entry entry = calculateEntry(cycle, assignment, exceptions);
            entries.add(entry);
            grossTotal = grossTotal.add(entry.getGross());
            deductionsTotal = deductionsTotal.add(entry.getTotalDeductions());
            netTotal = netTotal.add(entry.getNetPay());
        }
        addStaffMovementExceptions(cycle, entries, exceptions);
        cycle.setEntries(entries);
        cycle.setExceptions(exceptions);
        cycle.setGrossTotal(money(grossTotal));
        cycle.setDeductionTotal(money(deductionsTotal));
        cycle.setNetTotal(money(netTotal));
        cycle.setStatus("CALCULATED");
        cycle.setUpdatedAt(Instant.now());
        PayrollCycle saved = mongo.save(cycle);
        audit(actor, "CALCULATE", "PAYROLL_CYCLE", saved.getId());
        return saved;
    }

    private void addStaffMovementExceptions(
            PayrollCycle cycle, List<PayrollCycle.Entry> entries, List<String> exceptions) {
        PayrollCycle previous = mongo.findAll(PayrollCycle.class).stream()
                .filter(candidate -> candidate.getEmployeeType().equals(cycle.getEmployeeType()))
                .filter(candidate -> candidate.getToDate().isBefore(cycle.getFromDate()))
                .max(Comparator.comparing(PayrollCycle::getToDate)).orElse(null);
        if (previous == null) return;
        Set<String> previousEmployees = previous.getEntries().stream()
                .map(PayrollCycle.Entry::getEmployeeId).collect(java.util.stream.Collectors.toSet());
        Set<String> currentEmployees = entries.stream()
                .map(PayrollCycle.Entry::getEmployeeId).collect(java.util.stream.Collectors.toSet());
        currentEmployees.stream().filter(employee -> !previousEmployees.contains(employee)).forEach(employee ->
                exceptions.add("New joiner: " + entries.stream().filter(entry -> employee.equals(entry.getEmployeeId()))
                        .map(PayrollCycle.Entry::getEmployeeCode).findFirst().orElse(employee)));
        previousEmployees.stream().filter(employee -> !currentEmployees.contains(employee)).forEach(employee ->
                exceptions.add("Exit or inactive salary assignment: " + previous.getEntries().stream()
                        .filter(entry -> employee.equals(entry.getEmployeeId()))
                        .map(PayrollCycle.Entry::getEmployeeCode).findFirst().orElse(employee)));
    }

    private PayrollCycle.Entry calculateEntry(
            PayrollCycle cycle, EmployeeSalary salary, List<String> exceptions) {
        SalaryStructure structure = require(SalaryStructure.class, salary.getStructureId(), "Salary structure");
        PayrollCycle.Entry entry = new PayrollCycle.Entry();
        entry.setEmployeeType(salary.getEmployeeType());
        entry.setEmployeeId(salary.getEmployeeId());
        entry.setStructureId(structure.getId());
        BigDecimal basic = prorate(salary.getBasic(), salary, cycle);
        identifyEmployee(entry, salary.getEmployeeType(), salary.getEmployeeId());
        NurseAttendanceSummary attendance = null;
        BigDecimal overtimeHours = ZERO;
        if ("NURSE".equals(salary.getEmployeeType())) {
            attendance = requireFinalizedAttendance(salary.getEmployeeId(), cycle);
            overtimeHours = approvedOvertimeHours(salary.getEmployeeId(), cycle);
        }
        BigDecimal gross = ZERO;
        BigDecimal deductions = ZERO;
        for (SalaryStructure.ComponentLine line : structure.getComponents()) {
            SalaryComponent component = require(SalaryComponent.class, line.getComponentId(), "Salary component");
            if (!component.isActive() || !applies(component.getAppliesTo(), salary.getEmployeeType())) continue;
            BigDecimal units = configuredUnits(line, component, attendance, overtimeHours);
            BigDecimal amount;
            try {
                amount = PayrollCalculator.componentAmount(component.getCalculation(), line.getAmount(),
                        line.getPercent(), basic, units);
            } catch (IllegalArgumentException exception) {
                if ("FORMULA".equals(component.getCalculation())) {
                    amount = evaluateFormula(component.getFormula(), basic, units, component.getCode());
                } else {
                    throw badRequest("Unsupported calculation for component " + component.getCode());
                }
            }
            if (component.getCalculation().equals("FIXED")) amount = prorate(amount, salary, cycle);
            if ("LOP".equals(component.getCode()) && attendance != null && salary.getLopDivisor() != null
                    && salary.getLopDivisor().signum() > 0) {
                amount = basic.divide(salary.getLopDivisor(), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(attendance.getLopDays()));
            }
            if ("OVERTIME".equals(component.getCode()) && salary.getMonthlyHours() != null
                    && salary.getMonthlyHours().signum() > 0 && salary.getOvertimeMultiplier() != null) {
                amount = basic.divide(salary.getMonthlyHours(), 4, RoundingMode.HALF_UP)
                        .multiply(salary.getOvertimeMultiplier()).multiply(overtimeHours);
            }
            if ("WARD_ALLOWANCE".equals(component.getCode()) && attendance != null) {
                amount = calculateWardAllowance(attendance, cycle);
            }
            appendLine(entry, component.getCode(), component.getName(), component.getType(), amount,
                    component.getCalculation().equals("PER_UNIT") ? units.stripTrailingZeros().toPlainString() + " units" : "");
            if ("EARNING".equals(component.getType())) gross = gross.add(money(amount));
            else deductions = deductions.add(money(amount));
        }
        gross = gross.add(basic);
        appendLine(entry, "BASIC", "Basic salary", "EARNING", basic, "Effective-dated salary");
        if ("DOCTOR".equals(salary.getEmployeeType())) {
            addDoctorEarnings(entry, salary.getEmployeeId(), cycle, basic, exceptions);
            gross = entry.getLines().stream().filter(line -> "EARNING".equals(line.getType()))
                    .map(PayrollCycle.EntryLine::getAmount).reduce(ZERO, BigDecimal::add);
        }
        for (StatutoryConfig config : applicableStatutory(salary.getEmployeeType(), cycle)) {
            BigDecimal base = basic;
            if (config.getCeiling() != null) base = base.min(config.getCeiling());
            BigDecimal amount = base.multiply(config.getRate())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            appendLine(entry, config.getName().toUpperCase(Locale.ROOT), config.getName(),
                    "DEDUCTION", amount, "Configured statutory rate");
            deductions = deductions.add(money(amount));
        }
        for (PayrollAdjustment adjustment : approvedAdjustments(salary, cycle)) {
            boolean earning = "EARNING".equals(adjustment.getType());
            appendLine(entry, "ADJUSTMENT", "Adjustment", adjustment.getType(),
                    adjustment.getAmount(), adjustment.getReason());
            if (earning) gross = gross.add(money(adjustment.getAmount()));
            else deductions = deductions.add(money(adjustment.getAmount()));
        }
        for (PayrollLoan loan : activeLoans(salary)) {
            BigDecimal emi = loan.getEmi().min(loan.getBalance());
            appendLine(entry, "ADVANCE_RECOVERY", "Advance recovery", "DEDUCTION", emi, loan.getId());
            deductions = deductions.add(money(emi));
        }
        BigDecimal net = money(gross.subtract(deductions));
        entry.setGross(money(gross));
        entry.setTotalDeductions(money(deductions));
        entry.setNetPay(net);
        if (net.signum() < 0) {
            entry.setStatus("REVIEW_REQUIRED");
            exceptions.add("Negative net pay for " + entry.getEmployeeCode());
        } else if (net.signum() == 0) {
            exceptions.add("Zero net pay for " + entry.getEmployeeCode());
        }
        return entry;
    }

    private BigDecimal evaluateFormula(String formula, BigDecimal basic, BigDecimal units, String code) {
        try {
            return PayrollFormula.evaluate(formula, basic, units);
        } catch (IllegalArgumentException exception) {
            throw badRequest("Invalid formula for component " + code + ": " + exception.getMessage());
        }
    }

    private BigDecimal calculateWardAllowance(NurseAttendanceSummary attendance, PayrollCycle cycle) {
        BigDecimal total = ZERO;
        Map<String, NurseWardAllowance> latestByWard = new java.util.HashMap<>();
        for (NurseWardAllowance candidate : mongo.findAll(NurseWardAllowance.class)) {
            if (candidate.getEffectiveFrom() == null || candidate.getEffectiveFrom().isAfter(cycle.getToDate())) continue;
            NurseWardAllowance current = latestByWard.get(candidate.getWardId());
            if (current == null || candidate.getEffectiveFrom().isAfter(current.getEffectiveFrom())) {
                latestByWard.put(candidate.getWardId(), candidate);
            }
        }
        for (NurseWardAllowance allowance : latestByWard.values()) {
            int workedDays = attendance.getWardDays() == null ? 0
                    : attendance.getWardDays().getOrDefault(allowance.getWardId(), 0);
            if ("PER_DAY".equals(allowance.getBasis())) {
                total = total.add(allowance.getAmount().multiply(BigDecimal.valueOf(workedDays)));
            } else if ("PER_MONTH".equals(allowance.getBasis()) && workedDays > 0) {
                long periodDays = ChronoUnit.DAYS.between(cycle.getFromDate(), cycle.getToDate()) + 1;
                total = total.add(allowance.getAmount().multiply(BigDecimal.valueOf(Math.min(workedDays, periodDays)))
                        .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP));
            }
        }
        return money(total);
    }

    private void addDoctorEarnings(
            PayrollCycle.Entry entry, String doctorId, PayrollCycle cycle, BigDecimal fixedPay,
            List<String> exceptions) {
        DoctorPayProfile profile = currentDoctorPayProfile(doctorId, cycle.getToDate());
        if (profile == null) return;
        List<DoctorEarning> doctorEarnings = mongo.find(Query.query(Criteria.where("doctorId").is(doctorId)
                .and("serviceDate").gte(cycle.getFromDate()).lte(cycle.getToDate())), DoctorEarning.class);
        String payModel = normalize(profile.getPayModel());
        boolean disputePending = mongo.findAll(DoctorDispute.class).stream()
                .anyMatch(dispute -> doctorEarnings.stream()
                        .anyMatch(earning -> earning.getId().equals(dispute.getEarningId()))
                        && "OPEN".equals(dispute.getStatus()));
        if (disputePending) entry.setStatus("DISPUTE_REVIEW");
        BigDecimal variable = ZERO;
        for (DoctorEarning earning : doctorEarnings) {
            if ("DISPUTED".equals(earning.getStatus())) {
                exceptions.add("Open doctor earnings dispute: " + earning.getId());
                entry.setStatus("DISPUTE_REVIEW");
                continue;
            }
            if (!"INCLUDED".equals(earning.getStatus()) || earning.getDoctorShare() == null) continue;
            boolean allowedByModel = switch (earning.getSourceType()) {
                case "CONSULTATION", "VISIT" -> Set.of("CONSULTATION_SHARE", "PER_VISIT", "HYBRID").contains(payModel);
                case "PROCEDURE" -> Set.of("PROCEDURE_SHARE", "HYBRID").contains(payModel);
                default -> false;
            };
            if (!allowedByModel) continue;
            BigDecimal amount = money(earning.getDoctorShare());
            if (profile.getVariableCap() != null) {
                BigDecimal remainingCap = profile.getVariableCap().subtract(variable).max(ZERO);
                amount = amount.min(remainingCap);
            }
            appendLine(entry, "DOCTOR_VARIABLE", earning.getService(), "EARNING", amount,
                    earning.getServiceDate() + " | " + earning.getSourceType()
                            + " | reference " + earning.getSourceId() + " | patient " + nullToEmpty(earning.getPatientRef()));
            variable = variable.add(amount);
        }
        BigDecimal onCall = doctorEarnings.stream()
                .filter(item -> "ON_CALL".equals(item.getSourceType()) && "INCLUDED".equals(item.getStatus()))
                .map(DoctorEarning::getDoctorShare).filter(Objects::nonNull)
                .map(this::money).reduce(ZERO, BigDecimal::add);
        if (onCall.signum() > 0) {
            appendLine(entry, "ON_CALL_ALLOWANCE", "On-call allowance", "EARNING", onCall, "Completed on-call duties");
        }
        BigDecimal basePlusVariable = fixedPay.add(variable);
        if (profile.getGuaranteedMinimum() != null && basePlusVariable.compareTo(profile.getGuaranteedMinimum()) < 0) {
            BigDecimal topUp = money(profile.getGuaranteedMinimum().subtract(basePlusVariable));
            appendLine(entry, "MINIMUM_GUARANTEE", "Minimum guarantee top-up", "EARNING", topUp, "Configured doctor guarantee");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal configuredUnits(
            SalaryStructure.ComponentLine line, SalaryComponent component,
            NurseAttendanceSummary attendance, BigDecimal overtimeHours) {
        if (attendance == null) return line.getUnits();
        return switch (component.getCode()) {
            case "NIGHT_SHIFT_ALLOWANCE" -> BigDecimal.valueOf(attendance.getNightShifts());
            case "EVENING_SHIFT_ALLOWANCE" -> BigDecimal.valueOf(attendance.getEveningShifts());
            case "HOLIDAY_DUTY_ALLOWANCE" -> BigDecimal.valueOf(attendance.getHolidayDutyDays());
            case "WEEKLY_OFF_ALLOWANCE" -> BigDecimal.valueOf(attendance.getWeeklyOffDutyDays());
            case "OVERTIME" -> overtimeHours;
            case "LOP" -> BigDecimal.valueOf(attendance.getLopDays());
            default -> line.getUnits();
        };
    }

    private void identifyEmployee(PayrollCycle.Entry entry, String type, String employeeId) {
        if ("DOCTOR".equals(type)) {
            Doctor doctor = doctorRepository.findById(employeeId)
                    .orElseThrow(() -> notFound("Doctor profile"));
            entry.setEmployeeName(doctor.getDoctorName());
            entry.setEmployeeCode(doctor.getEmployeeId());
        } else {
            NurseProfile nurse = nurseRepository.findByAccountId(employeeId)
                    .orElseThrow(() -> notFound("Nurse profile"));
            entry.setEmployeeName(nurse.getName());
            entry.setEmployeeCode(nurse.getEmployeeId());
        }
    }

    private NurseAttendanceSummary requireFinalizedAttendance(String nurseId, PayrollCycle cycle) {
        List<NurseAttendanceSummary> summaries = mongo.find(Query.query(Criteria.where("nurseId").is(nurseId)
                .and("fromDate").lte(cycle.getToDate()).and("toDate").gte(cycle.getFromDate())
                .and("finalized").is(true)), NurseAttendanceSummary.class);
        if (summaries.size() != 1 || !cycle.getFromDate().equals(summaries.get(0).getFromDate())
                || !cycle.getToDate().equals(summaries.get(0).getToDate())) {
            throw badRequest("Finalize the nurse attendance summary for the full payroll period before calculation");
        }
        return summaries.get(0);
    }

    private BigDecimal approvedOvertimeHours(String nurseId, PayrollCycle cycle) {
        List<PayrollOvertime> overtime = mongo.find(Query.query(Criteria.where("nurseId").is(nurseId)
                .and("date").gte(cycle.getFromDate()).lte(cycle.getToDate())
                .and("status").is("APPROVED")), PayrollOvertime.class);
        return overtime.stream().map(PayrollOvertime::getHours).filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private List<StatutoryConfig> applicableStatutory(String type, PayrollCycle cycle) {
        return mongo.findAll(StatutoryConfig.class).stream()
                .filter(config -> applies(config.getAppliesTo(), type))
                .filter(config -> config.getEffectiveFrom() != null
                        && !config.getEffectiveFrom().isAfter(cycle.getToDate()))
                .filter(config -> mongo.findAll(StatutoryConfig.class).stream()
                        .filter(other -> Objects.equals(other.getName(), config.getName()))
                        .filter(other -> applies(other.getAppliesTo(), type))
                        .filter(other -> other.getEffectiveFrom() != null
                                && !other.getEffectiveFrom().isAfter(cycle.getToDate()))
                        .max(Comparator.comparing(StatutoryConfig::getEffectiveFrom))
                        .map(latest -> Objects.equals(latest.getId(), config.getId())).orElse(false))
                .toList();
    }

    private List<PayrollAdjustment> approvedAdjustments(EmployeeSalary salary, PayrollCycle cycle) {
        return mongo.find(Query.query(Criteria.where("employeeType").is(salary.getEmployeeType())
                .and("employeeId").is(salary.getEmployeeId()).and("cycleId").is(cycle.getId())
                .and("status").is("APPROVED")), PayrollAdjustment.class);
    }

    private List<PayrollLoan> activeLoans(EmployeeSalary salary) {
        return mongo.find(Query.query(Criteria.where("employeeType").is(salary.getEmployeeType())
                .and("employeeId").is(salary.getEmployeeId()).and("status").is("ACTIVE")
                .and("balance").gt(BigDecimal.ZERO)), PayrollLoan.class);
    }

    public PayrollCycle cycle(String id, String actor) {
        PayrollCycle cycle = require(PayrollCycle.class, id, "Payroll cycle");
        audit(actor, "READ", "PAYROLL_CYCLE", id);
        return cycle;
    }

    public List<PayrollCycle> cycles(String actor) {
        audit(actor, "READ", "PAYROLL_CYCLES", "all");
        return mongo.findAll(PayrollCycle.class);
    }

    public PayrollCycle transition(String id, String action, String actor) {
        String normalizedAction = normalize(action);
        if (Set.of("APPROVE", "LOCK", "MARK-PAID").contains(normalizedAction) && !canApprovePayroll()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only Finance or an administrator can approve, lock, or mark payroll as paid");
        }
        PayrollCycle cycle = require(PayrollCycle.class, id, "Payroll cycle");
        String next = switch (normalizedAction) {
            case "SUBMIT-REVIEW" -> transitionFrom(cycle, "CALCULATED", "UNDER_REVIEW");
            case "APPROVE" -> transitionFrom(cycle, "UNDER_REVIEW", "APPROVED");
            case "LOCK" -> transitionFrom(cycle, "APPROVED", "LOCKED");
            case "MARK-PAID" -> transitionFrom(cycle, "LOCKED", "PAID");
            default -> throw badRequest("Unknown payroll action");
        };
        cycle.setStatus(next);
        cycle.setUpdatedAt(Instant.now());
        if ("APPROVED".equals(next)) cycle.setApprovedBy(actor);
        if ("LOCKED".equals(next)) cycle.setLockedAt(Instant.now());
        if ("UNDER_REVIEW".equals(next)) {
            for (PayrollCycle.Entry entry : cycle.getEntries()) {
                boolean disputed = hasOpenDoctorDispute(cycle, entry.getEmployeeId());
                if (disputed) {
                    if (!"REVIEW_REQUIRED".equals(entry.getStatus())) entry.setStatus("DISPUTE_REVIEW");
                    cycle.getExceptions().add("Open doctor earnings dispute for " + entry.getEmployeeCode());
                }
            }
            cycle.setExceptions(cycle.getExceptions().stream().distinct().toList());
        }
        if ("APPROVED".equals(next) && cycle.getEntries().stream()
                .anyMatch(entry -> hasOpenDoctorDispute(cycle, entry.getEmployeeId()))) {
            throw conflict("Resolve doctor earnings disputes before approving payroll");
        }
        if ("APPROVED".equals(next)) {
            cycle.getEntries().stream().filter(entry -> "DISPUTE_REVIEW".equals(entry.getStatus()))
                    .forEach(entry -> entry.setStatus("CALCULATED"));
        }
        if ("PAID".equals(next)) applyLoanDeductions(cycle);
        PayrollCycle saved = mongo.save(cycle);
        audit(actor, normalizedAction, "PAYROLL_CYCLE", id);
        return saved;
    }

    private boolean hasOpenDoctorDispute(PayrollCycle cycle, String doctorId) {
        if (!"DOCTOR".equals(cycle.getEmployeeType())) return false;
        Set<String> disputedEarningIds = mongo.findAll(DoctorDispute.class).stream()
                .filter(dispute -> "OPEN".equals(dispute.getStatus()))
                .filter(dispute -> doctorId.equals(dispute.getDoctorId()))
                .map(DoctorDispute::getEarningId).collect(java.util.stream.Collectors.toSet());
        if (disputedEarningIds.isEmpty()) return false;
        return mongo.find(Query.query(Criteria.where("doctorId").is(doctorId)
                .and("serviceDate").gte(cycle.getFromDate()).lte(cycle.getToDate())
                .and("id").in(disputedEarningIds)), DoctorEarning.class).size() > 0;
    }

    private void applyLoanDeductions(PayrollCycle cycle) {
        for (PayrollCycle.Entry entry : cycle.getEntries()) {
            for (PayrollCycle.EntryLine line : entry.getLines()) {
                if (!"ADVANCE_RECOVERY".equals(line.getComponentCode()) || !StringUtils.hasText(line.getRemarks())) {
                    continue;
                }
                PayrollLoan loan = require(PayrollLoan.class, line.getRemarks(), "Payroll advance");
                if (loan.getPaidCycleIds() == null) loan.setPaidCycleIds(new ArrayList<>());
                if (loan.getPaidCycleIds().contains(cycle.getId())) continue;
                if (loan.getBalance().compareTo(line.getAmount()) < 0) {
                    throw conflict("Advance balance changed after calculation; correct this locked cycle before marking it paid");
                }
                loan.setBalance(loan.getBalance().subtract(line.getAmount()));
                if (loan.getBalance().signum() == 0) loan.setStatus("CLEARED");
                loan.getPaidCycleIds().add(cycle.getId());
                mongo.save(loan);
            }
        }
    }

    private String transitionFrom(PayrollCycle cycle, String expected, String next) {
        if (!expected.equals(cycle.getStatus())) {
            throw conflict("Payroll cycle must be " + expected + " before it can move to " + next);
        }
        if ("APPROVED".equals(next) && !cycle.getExceptions().isEmpty()
                && !StringUtils.hasText(cycle.getExceptionsReviewedBy())) {
            throw conflict("Review the payroll exceptions before approval");
        }
        if ("APPROVED".equals(next) && cycle.getEntries().stream()
                .anyMatch(entry -> "REVIEW_REQUIRED".equals(entry.getStatus()))) {
            throw conflict("Negative net pay must be corrected before payroll can be approved");
        }
        return next;
    }

    public PayrollAdjustment createAdjustment(PayrollAdjustment adjustment, String actor) {
        if (adjustment == null || !EMPLOYEE_TYPES.contains(normalize(adjustment.getEmployeeType()))
                || !StringUtils.hasText(adjustment.getEmployeeId()) || !StringUtils.hasText(adjustment.getCycleId())
                || adjustment.getAmount() == null || adjustment.getAmount().signum() <= 0
                || !Set.of("EARNING", "DEDUCTION").contains(normalize(adjustment.getType()))
                || !StringUtils.hasText(adjustment.getReason())) {
            throw badRequest("Employee, cycle, positive amount, earning/deduction type, and reason are required");
        }
        PayrollCycle cycle = require(PayrollCycle.class, adjustment.getCycleId(), "Payroll cycle");
        if (!"DRAFT".equals(cycle.getStatus())) throw conflict("Adjustments can only be added to a draft cycle");
        requireEmployee(normalize(adjustment.getEmployeeType()), adjustment.getEmployeeId());
        adjustment.setEmployeeType(normalize(adjustment.getEmployeeType()));
        adjustment.setType(normalize(adjustment.getType()));
        adjustment.setStatus("PENDING");
        adjustment.setId(null);
        PayrollAdjustment saved = mongo.save(adjustment);
        audit(actor, "CREATE", "PAYROLL_ADJUSTMENT", saved.getId());
        return saved;
    }

    public PayrollCycle reviewExceptions(String id, String note, String actor) {
        PayrollCycle cycle = require(PayrollCycle.class, id, "Payroll cycle");
        if (!"UNDER_REVIEW".equals(cycle.getStatus())) {
            throw conflict("Payroll exceptions can only be reviewed while the cycle is under review");
        }
        if (!cycle.getExceptions().isEmpty() && !StringUtils.hasText(note)) {
            throw badRequest("Enter a review note for the listed payroll exceptions");
        }
        cycle.setExceptionsReviewedBy(actor);
        cycle.setExceptionsReviewNote(note);
        cycle.setUpdatedAt(Instant.now());
        PayrollCycle saved = mongo.save(cycle);
        audit(actor, "REVIEW_EXCEPTIONS", "PAYROLL_CYCLE", id);
        return saved;
    }

    public PayrollAdjustment decideAdjustment(String id, boolean approve, String actor) {
        PayrollAdjustment adjustment = require(PayrollAdjustment.class, id, "Payroll adjustment");
        if (!"PENDING".equals(adjustment.getStatus())) throw conflict("This adjustment has already been reviewed");
        adjustment.setStatus(approve ? "APPROVED" : "REJECTED");
        adjustment.setApprovedBy(actor);
        PayrollAdjustment saved = mongo.save(adjustment);
        audit(actor, approve ? "APPROVE" : "REJECT", "PAYROLL_ADJUSTMENT", id);
        return saved;
    }

    public List<PayrollAdjustment> adjustments(String actor) {
        audit(actor, "READ", "PAYROLL_ADJUSTMENTS", "all");
        return mongo.findAll(PayrollAdjustment.class);
    }

    public PayrollLoan createLoan(PayrollLoan loan, String actor) {
        if (loan == null || !EMPLOYEE_TYPES.contains(normalize(loan.getEmployeeType()))
                || !StringUtils.hasText(loan.getEmployeeId()) || loan.getAmount() == null
                || loan.getAmount().signum() <= 0 || loan.getEmi() == null || loan.getEmi().signum() <= 0
                || loan.getEmi().compareTo(loan.getAmount()) > 0) {
            throw badRequest("Employee, positive loan amount, and EMI not exceeding the loan amount are required");
        }
        loan.setEmployeeType(normalize(loan.getEmployeeType()));
        requireEmployee(loan.getEmployeeType(), loan.getEmployeeId());
        loan.setBalance(loan.getAmount());
        loan.setStatus("ACTIVE");
        loan.setId(null);
        loan.setCreatedBy(actor);
        PayrollLoan saved = mongo.save(loan);
        audit(actor, "CREATE", "PAYROLL_LOAN", saved.getId());
        return saved;
    }

    public List<PayrollLoan> loans(String actor) {
        audit(actor, "READ", "PAYROLL_LOANS", "all");
        return mongo.findAll(PayrollLoan.class);
    }

    public List<PayrollOvertime> overtimeList(String actor) {
        audit(actor, "READ", "NURSE_OVERTIME", "all");
        user account = userRepository.findAllByEmailIdIgnoreCase(actor).stream().findFirst()
                .orElseThrow(() -> notFound("Account"));
        boolean manager = account.getRoles().contains("HEAD_NURSE")
                && account.getRoles().stream().noneMatch(role -> Set.of("SUPER_ADMIN", "HOSPITAL_ADMIN",
                        "CLINIC_ADMIN", "FINANCE", "HR").contains(role));
        if (!manager) return mongo.findAll(PayrollOvertime.class);
        Set<String> wards = rosterRepository.findByNurseIdAndStatus(account.getId(), "SCHEDULED").stream()
                .filter(roster -> rosterOverlaps(roster, LocalDate.now(), LocalDate.now()))
                .map(com.example.MedcareApp.Entity.nursing.NurseShiftRoster::getWardId)
                .collect(java.util.stream.Collectors.toSet());
        return mongo.findAll(PayrollOvertime.class).stream()
                .filter(record -> wards.contains(record.getWardId())).toList();
    }

    public PayrollOvertime submitOvertime(PayrollOvertime overtime, String actor) {
        if (overtime == null || !StringUtils.hasText(overtime.getNurseId()) || overtime.getDate() == null
                || overtime.getHours() == null || overtime.getHours().signum() <= 0
                || !StringUtils.hasText(overtime.getWardId())) {
            throw badRequest("Nurse, date, positive overtime hours, and ward are required");
        }
        requireEmployee("NURSE", overtime.getNurseId());
        verifyHeadNurseAssignment(actor, overtime.getNurseId(), overtime.getWardId(),
                overtime.getDate(), overtime.getDate());
        overtime.setId(null);
        overtime.setStatus("PENDING");
        overtime.setSubmittedBy(actor);
        PayrollOvertime saved = mongo.save(overtime);
        audit(actor, "SUBMIT", "NURSE_OVERTIME", saved.getId());
        return saved;
    }

    public PayrollOvertime decideOvertime(String id, boolean approve, String note, String actor) {
        PayrollOvertime overtime = require(PayrollOvertime.class, id, "Overtime request");
        if (!"PENDING".equals(overtime.getStatus())) throw conflict("This overtime request has already been reviewed");
        overtime.setStatus(approve ? "APPROVED" : "REJECTED");
        overtime.setApprovedBy(actor);
        overtime.setDecisionNote(note);
        PayrollOvertime saved = mongo.save(overtime);
        audit(actor, approve ? "APPROVE" : "REJECT", "NURSE_OVERTIME", id);
        return saved;
    }

    public NurseAttendanceSummary saveAttendance(NurseAttendanceSummary summary, String actor) {
        if (summary == null || !StringUtils.hasText(summary.getNurseId())
                || summary.getFromDate() == null || summary.getToDate() == null
                || summary.getFromDate().isAfter(summary.getToDate())
                || summary.getPresentDays() < 0 || summary.getAbsentDays() < 0
                || summary.getLopDays() < 0 || summary.getNightShifts() < 0
                || summary.getEveningShifts() < 0 || summary.getHolidayDutyDays() < 0
                || summary.getWeeklyOffDutyDays() < 0 || summary.getLateMarks() < 0) {
            throw badRequest("Enter a valid nurse and non-negative attendance counts for a date range");
        }
        requireEmployee("NURSE", summary.getNurseId());
        long periodDays = ChronoUnit.DAYS.between(summary.getFromDate(), summary.getToDate()) + 1;
        Map<String, Integer> wardDays = summary.getWardDays() == null ? Map.of() : summary.getWardDays();
        long totalWardDays = 0;
        for (Map.Entry<String, Integer> wardDay : wardDays.entrySet()) {
            if (!StringUtils.hasText(wardDay.getKey()) || wardDay.getValue() == null || wardDay.getValue() < 0) {
                throw badRequest("Ward worked-days must use ward IDs and non-negative whole-day counts");
            }
            totalWardDays += wardDay.getValue();
        }
        if (totalWardDays > summary.getPresentDays() || totalWardDays > periodDays) {
            throw badRequest("Ward worked-days cannot exceed the nurse's present days or attendance period");
        }
        summary.setWardDays(wardDays);
        verifyHeadNurseAssignment(actor, summary.getNurseId(), null,
                summary.getFromDate(), summary.getToDate());
        if (summary.isFinalized() && !isPayrollManager()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only HR or Finance can finalize attendance");
        }
        List<NurseAttendanceSummary> overlapping = mongo.find(Query.query(Criteria.where("nurseId")
                .is(summary.getNurseId()).and("fromDate").lte(summary.getToDate())
                .and("toDate").gte(summary.getFromDate())), NurseAttendanceSummary.class);
        if (overlapping.stream().anyMatch(existing -> !Objects.equals(existing.getId(), summary.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An attendance summary already covers part of this date range");
        }
        if (StringUtils.hasText(summary.getId())
                && require(NurseAttendanceSummary.class, summary.getId(), "Attendance summary").isFinalized()) {
            throw conflict("Finalized attendance summaries cannot be changed");
        }
        summary.setUpdatedAt(Instant.now());
        NurseAttendanceSummary saved = mongo.save(summary);
        audit(actor, summary.isFinalized() ? "FINALIZE" : "WRITE", "NURSE_ATTENDANCE", saved.getId());
        return saved;
    }

    private boolean isPayrollManager() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().anyMatch(authority -> Set.of("ROLE_SUPER_ADMIN", "ROLE_HOSPITAL_ADMIN",
                        "ROLE_CLINIC_ADMIN", "ROLE_FINANCE", "ROLE_HR").contains(authority.getAuthority()));
    }

    private boolean canApprovePayroll() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().anyMatch(authority -> Set.of("ROLE_SUPER_ADMIN", "ROLE_HOSPITAL_ADMIN",
                        "ROLE_CLINIC_ADMIN", "ROLE_FINANCE").contains(authority.getAuthority()));
    }

    private void verifyHeadNurseAssignment(
            String actor, String nurseId, String wardId, LocalDate from, LocalDate to) {
        user account = userRepository.findAllByEmailIdIgnoreCase(actor).stream().findFirst()
                .orElseThrow(() -> notFound("Account"));
        boolean headNurseOnly = account.getRoles().contains("HEAD_NURSE")
                && account.getRoles().stream().noneMatch(role -> Set.of("SUPER_ADMIN", "HOSPITAL_ADMIN",
                        "CLINIC_ADMIN", "FINANCE", "HR").contains(role));
        if (!headNurseOnly) return;
        Set<String> wards = rosterRepository.findByNurseIdAndStatus(account.getId(), "SCHEDULED").stream()
                .filter(roster -> rosterOverlaps(roster, from, to))
                .map(com.example.MedcareApp.Entity.nursing.NurseShiftRoster::getWardId)
                .collect(java.util.stream.Collectors.toSet());
        boolean allowed = rosterRepository.findByNurseIdAndStatus(nurseId, "SCHEDULED").stream()
                .anyMatch(roster -> rosterOverlaps(roster, from, to)
                        && wards.contains(roster.getWardId())
                        && (wardId == null || wardId.equals(roster.getWardId())));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Head Nurses may only submit attendance or overtime for nurses in their assigned ward and date range");
        }
    }

    private boolean rosterOverlaps(
            com.example.MedcareApp.Entity.nursing.NurseShiftRoster roster, LocalDate from, LocalDate to) {
        if (roster.getStartDate() == null || roster.getEndDate() == null) return false;
        LocalDate start = LocalDate.parse(roster.getStartDate());
        LocalDate end = LocalDate.parse(roster.getEndDate());
        return !end.isBefore(from) && !start.isAfter(to);
    }

    public List<NurseAttendanceSummary> attendance(String actor) {
        audit(actor, "READ", "NURSE_ATTENDANCE", "all");
        user account = userRepository.findAllByEmailIdIgnoreCase(actor).stream().findFirst()
                .orElseThrow(() -> notFound("Account"));
        boolean headNurseOnly = account.getRoles().contains("HEAD_NURSE")
                && account.getRoles().stream().noneMatch(role -> Set.of("SUPER_ADMIN", "HOSPITAL_ADMIN",
                        "CLINIC_ADMIN", "FINANCE", "HR").contains(role));
        if (!headNurseOnly) return mongo.findAll(NurseAttendanceSummary.class);
        Set<String> wards = rosterRepository.findByNurseIdAndStatus(account.getId(), "SCHEDULED").stream()
                .filter(roster -> rosterOverlaps(roster, LocalDate.now(), LocalDate.now()))
                .map(com.example.MedcareApp.Entity.nursing.NurseShiftRoster::getWardId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> nurseIds = rosterRepository.findAll().stream()
                .filter(roster -> "SCHEDULED".equals(roster.getStatus()) && wards.contains(roster.getWardId()))
                .filter(roster -> rosterOverlaps(roster, LocalDate.now(), LocalDate.now()))
                .map(com.example.MedcareApp.Entity.nursing.NurseShiftRoster::getNurseId)
                .collect(java.util.stream.Collectors.toSet());
        return mongo.findAll(NurseAttendanceSummary.class).stream()
                .filter(summary -> nurseIds.contains(summary.getNurseId())).toList();
    }

    public List<NurseAttendanceSummary> myAttendance(String actor) {
        user account = userRepository.findAllByEmailIdIgnoreCase(actor).stream().findFirst()
                .orElseThrow(() -> notFound("Account"));
        if (!account.getRoles().contains("NURSE") && !account.getRoles().contains("HEAD_NURSE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attendance summary is available to nurses only");
        }
        audit(actor, "READ_SELF", "NURSE_ATTENDANCE", account.getId());
        return mongo.find(Query.query(Criteria.where("nurseId").is(account.getId())), NurseAttendanceSummary.class);
    }

    public List<PayrollCycle> myPayslips(String actor) {
        user account = userRepository.findAllByEmailIdIgnoreCase(actor).stream().findFirst()
                .orElseThrow(() -> notFound("Account"));
        Set<String> roles = account.getRoles();
        String employeeType;
        String employeeId;
        if (roles.contains("DOCTOR")) {
            employeeType = "DOCTOR";
            employeeId = account.getDoctorId();
        } else if (roles.contains("NURSE") || roles.contains("HEAD_NURSE")) {
            employeeType = "NURSE";
            employeeId = account.getId();
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payslips are available to staff members only");
        }
        if (!StringUtils.hasText(employeeId)) return List.of();
        audit(actor, "READ_SELF", "PAYSLIPS", employeeId);
        return mongo.findAll(PayrollCycle.class).stream().filter(cycle -> Set.of("LOCKED", "PAID").contains(cycle.getStatus()))
                .filter(cycle -> employeeType.equals(cycle.getEmployeeType()))
                .filter(cycle -> cycle.getEntries().stream().anyMatch(entry -> employeeId.equals(entry.getEmployeeId())))
                .map(cycle -> {
                    PayrollCycle self = new PayrollCycle();
                    self.setId(cycle.getId());
                    self.setEmployeeType(cycle.getEmployeeType());
                    self.setMonth(cycle.getMonth());
                    self.setYear(cycle.getYear());
                    self.setFromDate(cycle.getFromDate());
                    self.setToDate(cycle.getToDate());
                    self.setStatus(cycle.getStatus());
                    self.setEntries(cycle.getEntries().stream()
                            .filter(entry -> employeeId.equals(entry.getEmployeeId())).toList());
                    return self;
                })
                .toList();
    }

    public byte[] payslipPdf(String entryId, String actor, boolean selfService) {
        PayrollCycle cycle = mongo.findAll(PayrollCycle.class).stream()
                .filter(item -> Set.of("LOCKED", "PAID").contains(item.getStatus()))
                .filter(item -> item.getEntries().stream().anyMatch(entry -> entryId.equals(entry.getId())))
                .findFirst().orElseThrow(() -> notFound("Payslip"));
        PayrollCycle.Entry entry = cycle.getEntries().stream()
                .filter(item -> entryId.equals(item.getId())).findFirst()
                .orElseThrow(() -> notFound("Payslip"));
        if (selfService) {
            user account = userRepository.findAllByEmailIdIgnoreCase(actor).stream().findFirst()
                    .orElseThrow(() -> notFound("Account"));
            boolean ownDoctorSlip = "DOCTOR".equals(entry.getEmployeeType())
                    && account.getRoles().contains("DOCTOR")
                    && entry.getEmployeeId().equals(account.getDoctorId());
            boolean ownNurseSlip = "NURSE".equals(entry.getEmployeeType())
                    && (account.getRoles().contains("NURSE") || account.getRoles().contains("HEAD_NURSE"))
                    && entry.getEmployeeId().equals(account.getId());
            if (!ownDoctorSlip && !ownNurseSlip) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may only download your own payslip");
            }
        }
        audit(actor, selfService ? "DOWNLOAD_SELF" : "DOWNLOAD", "PAYSLIP", entryId);
        List<String> rows = new ArrayList<>(List.of(
                "MEDCARE HOSPITAL - PAYSLIP",
                "Employee: " + entry.getEmployeeName() + " (" + entry.getEmployeeCode() + ")",
                "Employee type: " + entry.getEmployeeType(),
                "Payroll period: " + cycle.getMonth() + "/" + cycle.getYear(),
                "Status: " + cycle.getStatus(),
                "",
                "EARNINGS AND DEDUCTIONS"));
        entry.getLines().forEach(line -> rows.add(line.getType() + " | " + line.getComponentName()
                + " | INR " + line.getAmount() + (StringUtils.hasText(line.getRemarks()) ? " | " + line.getRemarks() : "")));
        rows.add("");
        rows.add("Gross: INR " + entry.getGross());
        rows.add("Deductions: INR " + entry.getTotalDeductions());
        rows.add("Net pay: INR " + entry.getNetPay());
        mongo.findAll(PayrollCycle.class).stream()
                .filter(item -> item.getYear() == cycle.getYear()
                        && item.getEmployeeType().equals(cycle.getEmployeeType())
                        && item.getMonth() <= cycle.getMonth()
                        && Set.of("LOCKED", "PAID").contains(item.getStatus()))
                .flatMap(item -> item.getEntries().stream())
                .filter(item -> entry.getEmployeeId().equals(item.getEmployeeId()))
                .map(PayrollCycle.Entry::getNetPay).filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .ifPresent(total -> rows.add("Year-to-date net pay: INR " + money(total)));
        if ("NURSE".equals(entry.getEmployeeType())) {
            mongo.findAll(NurseAttendanceSummary.class).stream()
                    .filter(summary -> entry.getEmployeeId().equals(summary.getNurseId())
                            && summary.getFromDate().equals(cycle.getFromDate())
                            && summary.getToDate().equals(cycle.getToDate()) && summary.isFinalized())
                    .findFirst().ifPresent(summary -> rows.add("Attendance: present " + summary.getPresentDays()
                            + ", absent " + summary.getAbsentDays() + ", LOP " + summary.getLopDays()
                            + ", night shifts " + summary.getNightShifts()));
        }
        return PayslipPdfGenerator.generate(rows);
    }

    public String reportCsv(String reportName, String actor) {
        String name = normalize(reportName);
        audit(actor, "EXPORT", "REPORT", name);
        List<PayrollCycle> all = mongo.findAll(PayrollCycle.class);
        StringBuilder csv = new StringBuilder("cycleId,employeeType,month,year,employeeCode,employeeName,gross,deductions,net,status\n");
        for (PayrollCycle cycle : all) {
            for (PayrollCycle.Entry entry : cycle.getEntries()) {
                csv.append(csv(cycle.getId())).append(',').append(cycle.getEmployeeType()).append(',')
                        .append(cycle.getMonth()).append(',').append(cycle.getYear()).append(',')
                        .append(csv(entry.getEmployeeCode())).append(',').append(csv(entry.getEmployeeName())).append(',')
                        .append(entry.getGross()).append(',').append(entry.getTotalDeductions()).append(',')
                        .append(entry.getNetPay()).append(',').append(entry.getStatus()).append('\n');
            }
        }
        return csv.toString();
    }

    private void validateStructure(SalaryStructure structure) {
        if (structure == null || !StringUtils.hasText(structure.getName())
                || !StringUtils.hasText(structure.getAppliesTo()) || structure.getComponents() == null
                || structure.getComponents().isEmpty()) {
            throw badRequest("Structure name, employee type, and at least one component are required");
        }
        structure.setAppliesTo(normalize(structure.getAppliesTo()));
        if (!Set.of("DOCTOR", "NURSE", "BOTH").contains(structure.getAppliesTo())) {
            throw badRequest("Select Doctor, Nurse, or Both for the salary structure");
        }
        for (SalaryStructure.ComponentLine line : structure.getComponents()) {
            if (!StringUtils.hasText(line.getComponentId()) || line.getAmount() == null
                    || line.getPercent() == null || line.getUnits() == null
                    || line.getAmount().signum() < 0 || line.getPercent().signum() < 0 || line.getUnits().signum() < 0) {
                throw badRequest("Salary components require valid IDs and non-negative amounts, percentages, and units");
            }
            SalaryComponent component = require(SalaryComponent.class, line.getComponentId(), "Salary component");
            if (!component.isActive() || !structureSupportsComponent(
                    structure.getAppliesTo(), component.getAppliesTo())) {
                throw badRequest("A salary component is inactive or not applicable to this structure");
            }
        }
    }

    private boolean structureSupportsComponent(String structureType, String componentType) {
        return "BOTH".equals(normalize(structureType)) || "BOTH".equals(normalize(componentType))
                || normalize(structureType).equals(normalize(componentType));
    }

    private void requireEmployee(String type, String employeeId) {
        boolean exists = "DOCTOR".equals(type)
                ? doctorRepository.existsById(employeeId) : nurseRepository.findByAccountId(employeeId).isPresent();
        if (!exists) throw notFound(type.equals("DOCTOR") ? "Doctor profile" : "Nurse profile");
    }

    private BigDecimal prorate(BigDecimal amount, EmployeeSalary salary, PayrollCycle cycle) {
        LocalDate start = salary.getEffectiveFrom().isAfter(cycle.getFromDate())
                ? salary.getEffectiveFrom() : cycle.getFromDate();
        LocalDate end = salary.getEffectiveTo() == null || salary.getEffectiveTo().isAfter(cycle.getToDate())
                ? cycle.getToDate() : salary.getEffectiveTo();
        if (start.isAfter(end)) return ZERO;
        long activeDays = ChronoUnit.DAYS.between(start, end) + 1;
        long periodDays = ChronoUnit.DAYS.between(cycle.getFromDate(), cycle.getToDate()) + 1;
        return PayrollCalculator.prorate(amount, activeDays, periodDays);
    }

    private boolean applies(String configured, String employeeType) {
        String value = normalize(configured);
        return "BOTH".equals(value) || value.equals(employeeType);
    }

    private String validateEmployeeType(String type) {
        String normalized = normalize(type);
        if (!EMPLOYEE_TYPES.contains(normalized)) throw badRequest("Employee type must be DOCTOR or NURSE");
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void appendLine(
            PayrollCycle.Entry entry, String code, String name, String type, BigDecimal amount, String remarks) {
        PayrollCycle.EntryLine line = new PayrollCycle.EntryLine();
        line.setComponentCode(code);
        line.setComponentName(name);
        line.setType(type);
        line.setAmount(money(amount));
        line.setRemarks(remarks);
        entry.getLines().add(line);
    }

    private BigDecimal money(BigDecimal value) {
        return PayrollCalculator.round(value);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private void audit(String actor, String action, String entity, String entityId) {
        PayrollAuditLog log = new PayrollAuditLog();
        log.setUserId(actor);
        log.setAction(action);
        log.setEntity(entity);
        log.setEntityId(entityId);
        mongo.save(log);
    }

    private <T> T require(Class<T> type, String id, String name) {
        T result = mongo.findById(id, type);
        if (result == null) throw notFound(name);
        return result;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " not found");
    }
}
