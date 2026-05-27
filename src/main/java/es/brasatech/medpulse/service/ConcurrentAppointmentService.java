package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.AppointmentSlot;
import es.brasatech.medpulse.domain.Patient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.StampedLock;

public class ConcurrentAppointmentService extends SimpleAppointmentService {

    public ConcurrentAppointmentService() {
        super();
    }

    @Override
    public boolean registerAppointmentSlot(AppointmentSlot slot, Patient patient) {
        if (slot == null || patient == null) {
            return false;
        }
        StampedLock doctorLock = InMemoryDataStore.getLockForDoctor(slot.doctor().doctorId());
        long stamp = doctorLock.writeLock();
        try {
            return super.registerAppointmentSlot(slot, patient);
        } finally {
            doctorLock.unlockWrite(stamp);
        }
    }

    @Override
    public List<LocalDateTime> getAvailableSlots(String doctorId, LocalDate date) {
        StampedLock doctorLock = InMemoryDataStore.getLockForDoctor(doctorId);
        long stamp = doctorLock.readLock();
        try {
            return super.getAvailableSlots(doctorId, date);
        } finally {
            doctorLock.unlockRead(stamp);
        }
    }
}
