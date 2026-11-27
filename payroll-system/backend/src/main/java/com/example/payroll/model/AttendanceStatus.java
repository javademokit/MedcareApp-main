package com.example.payroll.model;

public enum AttendanceStatus {
    PRESENT("P"), ABSENT("A"), HALF_DAY("H"), PAID_LEAVE("L"), WEEKLY_OFF("W");

    private final String code;
    AttendanceStatus(String code) { this.code = code; }
    public String code() { return code; }
}
