package com.medora.assistant.appointment;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * LangChain4j AI assistant interface for managing medical appointments.
 * The LLM (Ollama) will call the @Tool methods to perform actions.
 */
public interface AppointmentAssistant {

    @SystemMessage("""
            You are Medora Assistant, a helpful medical appointment scheduling bot.
            You help patients book, view, cancel, and reschedule their medical appointments.
            
            Rules:
            - Always be polite and professional
            - When booking an appointment, make sure you have the patient name, doctor name, date, and time
            - If any required information is missing, ask the patient for it
            - Dates should be in YYYY-MM-DD format
            - Times should be in HH:MM format (24-hour)
            - When listing appointments, show them in a clear, readable format
            - When canceling, confirm the appointment ID
            - When rescheduling, you need the appointment ID and new date/time
            - After each action, summarize what was done
            - If the patient asks medical questions, politely direct them to the disease query feature
            """)
    @UserMessage("{itself}")
    String chat(String userMessage);

    @Tool("Create a new medical appointment for a patient")
    String createAppointment(
            @V("patientName") String patientName,
            @V("doctorName") String doctorName,
            @V("appointmentDate") String appointmentDate,
            @V("appointmentTime") String appointmentTime,
            @V("reason") String reason,
            @V("patientEmail") String patientEmail,
            @V("patientPhone") String patientPhone
    );

    @Tool("List all upcoming appointments for a patient")
    String listAppointments(@V("patientName") String patientName);

    @Tool("Cancel a scheduled appointment by ID")
    String cancelAppointment(@V("appointmentId") Long appointmentId);

    @Tool("Reschedule an existing appointment to a new date and time")
    String rescheduleAppointment(
            @V("appointmentId") Long appointmentId,
            @V("newDate") String newDate,
            @V("newTime") String newTime
    );
}
