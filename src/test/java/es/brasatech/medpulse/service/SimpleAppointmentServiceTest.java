package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.AppointmentSlot;
import es.brasatech.medpulse.domain.AppointmentType;
import es.brasatech.medpulse.domain.Patient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class SimpleAppointmentServiceTest {

    private final SimpleAppointmentService appointmentService = new SimpleAppointmentService();

    private static LocalDate getNextDayOfWeek(DayOfWeek day) {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() != day) {
            date = date.plusDays(1);
        }
        return date;
    }

    @Test
    void testSingleThreadedCorrectBooking() {
        appointmentService.clearBookings();

        var doctorId = "doc1";
        var patient = appointmentService.patients.get("pat1");

        // Dynamically obtain next Monday (Dr. House works M, W, F 09:00 - 13:00, 14:00 - 17:00)
        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);
        var dateTime = nextMonday.atTime(10, 0);
        var type = AppointmentType.SHORT; // 30 minutes duration

        var slot = appointmentService.createSimpleAppointmentSlot(dateTime, doctorId, type);
        boolean success = appointmentService.registerSimpleAppointmentSlot(slot, patient);

        Assertions.assertTrue(success, "First booking should succeed");
        Assertions.assertEquals(1, appointmentService.slots.size(), "There should be 1 booking in the system");
        Assertions.assertEquals(patient, appointmentService.slots.get(slot));
    }

    @Test
    void testSingleThreadedOverlapPrevention() {
        appointmentService.clearBookings();

        var doc1 = "doc1";
        var doc2 = "doc2";
        var pat1 = appointmentService.patients.get("pat1");
        var pat2 = appointmentService.patients.get("pat2");

        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);
        LocalDate nextTuesday = getNextDayOfWeek(DayOfWeek.TUESDAY);

        // Book doc1 on next Monday: 10:00 to 10:30 (SHORT)
        var slot1 = appointmentService.createSimpleAppointmentSlot(nextMonday.atTime(10, 0), doc1, AppointmentType.SHORT);
        Assertions.assertTrue(appointmentService.registerSimpleAppointmentSlot(slot1, pat1));

        // 1. Same doctor, exact same slot -> Fail
        var slotDuplicate = new AppointmentSlot(appointmentService.doctors.get(doc1), nextMonday.atTime(10, 0), AppointmentType.SHORT);
        Assertions.assertFalse(appointmentService.registerSimpleAppointmentSlot(slotDuplicate, pat2), "Duplicate booking should fail");

        // 2. Same doctor, overlapping slot starting inside the existing one -> Fail (10:15 to 10:45)
        var slotOverlappingStart = new AppointmentSlot(appointmentService.doctors.get(doc1), nextMonday.atTime(10, 15), AppointmentType.SHORT);
        Assertions.assertFalse(appointmentService.registerSimpleAppointmentSlot(slotOverlappingStart, pat2), "Overlapping booking (start inside) should fail");

        // 3. Same doctor, overlapping slot wrapping the existing one -> Fail (09:45 to 10:45)
        var slotOverlappingWrap = new AppointmentSlot(appointmentService.doctors.get(doc1), nextMonday.atTime(9, 45), AppointmentType.MEDIUM);
        Assertions.assertFalse(appointmentService.registerSimpleAppointmentSlot(slotOverlappingWrap, pat2), "Overlapping booking (wrapping existing) should fail");

        // 4. Same doctor, adjacent slot -> Succeed (10:30 to 11:00)
        var slotAdjacent = appointmentService.createSimpleAppointmentSlot(nextMonday.atTime(10, 30), doc1, AppointmentType.SHORT);
        Assertions.assertTrue(appointmentService.registerSimpleAppointmentSlot(slotAdjacent, pat2), "Adjacent non-overlapping booking should succeed");

        // 5. Different doctor, valid slot on a day they work (doc2 works Tuesday) -> Succeed
        var slotDifferentDoctor = appointmentService.createSimpleAppointmentSlot(nextTuesday.atTime(10, 0), doc2, AppointmentType.MEDIUM);
        Assertions.assertTrue(appointmentService.registerSimpleAppointmentSlot(slotDifferentDoctor, pat2), "Booking different doctor for valid slot should succeed");
    }

    @Test
    void testDoctorWorkingDaysRestriction() {
        // doc1 (Dr. House) works M, W, F. Let's try to book him on next Tuesday
        LocalDate nextTuesday = getNextDayOfWeek(DayOfWeek.TUESDAY);
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createSimpleAppointmentSlot(
                    nextTuesday.atTime(10, 0),
                    "doc1",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. House on Tuesday should fail weekly availability check");
    }

    @Test
    void testDoctorWorkingHoursRestriction() {
        // doc1 (Dr. House) works 09:00 - 13:00, 14:00 - 17:00.
        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);

        // 1. Try to book too early (08:30)
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createSimpleAppointmentSlot(
                    nextMonday.atTime(8, 30),
                    "doc1",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. House at 08:30 should fail working hours check");

        // 2. Try to book too late (16:45) with a MEDIUM appointment (60 mins, ends at 17:45)
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createSimpleAppointmentSlot(
                    nextMonday.atTime(16, 45),
                    "doc1",
                    AppointmentType.MEDIUM
            );
        }, "Booking Dr. House ending past 17:00 should fail working hours check");
    }

    @Test
    void testCompanyClosedDatesRestriction() {
        // Dynamically fetch next Saturday, add it to closed dates, and assert booking doc2 fails
        LocalDate nextSaturday = getNextDayOfWeek(DayOfWeek.SATURDAY);
        appointmentService.companyClosedDates.add(nextSaturday);

        try {
            Assertions.assertThrows(IllegalStateException.class, () -> {
                appointmentService.createSimpleAppointmentSlot(
                        nextSaturday.atTime(10, 0),
                        "doc2",
                        AppointmentType.MEDIUM
                );
            }, "Booking on a company closed date should fail");
        } finally {
            // Cleanup closed date to not pollute other tests
            appointmentService.companyClosedDates.remove(nextSaturday);
        }
    }

    @Test
    void testDoctorLunchBreakRestriction() {
        // doc1 (Dr. House) works Monday: 09:00 - 13:00 and 14:00 - 17:00. Lunch break is 13:00 - 14:00.
        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);

        // 1. Booking fully during lunch break (13:00 - 13:30) -> Should fail
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createSimpleAppointmentSlot(
                    nextMonday.atTime(13, 0),
                    "doc1",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. House during lunch hour should fail shift check");

        // 2. Booking crossing the start boundary (12:45 - 13:15) -> Should fail
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createSimpleAppointmentSlot(
                    nextMonday.atTime(12, 45),
                    "doc1",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. House crossing lunch hour start should fail shift check");

        // 3. Booking crossing the end boundary (13:45 - 14:15) -> Should fail
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createSimpleAppointmentSlot(
                    nextMonday.atTime(13, 45),
                    "doc1",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. House crossing lunch hour end should fail shift check");

        // 4. Booking inside afternoon shift (14:00 - 14:30) -> Should succeed
        var slotAfternoon = appointmentService.createSimpleAppointmentSlot(
                nextMonday.atTime(14, 0),
                "doc1",
                AppointmentType.SHORT
        );
        Assertions.assertNotNull(slotAfternoon, "Booking Dr. House exactly at start of second shift should succeed");
    }

    @Test
    void testDoctorSpecificDatesAvailability() {
        // doc3 (Dr. Strange) has specific dates availability:
        // - 2 days from now: 08:30 - 12:00 and 17:00 - 18:00
        // - 5 days from now: 11:00 - 17:00

        LocalDate date1 = LocalDate.now().plusDays(2);
        LocalDate date2 = LocalDate.now().plusDays(5);

        // 1. Booking on date1 during the first shift (09:00 - 09:30) -> Should succeed
        var slot1 = appointmentService.createSimpleAppointmentSlot(
                date1.atTime(9, 0),
                "doc3",
                AppointmentType.SHORT
        );
        Assertions.assertNotNull(slot1);

        // 2. Booking on date1 during the second shift (17:00 - 17:30) -> Should succeed
        var slot2 = appointmentService.createSimpleAppointmentSlot(
                date1.atTime(17, 0),
                "doc3",
                AppointmentType.SHORT
        );
        Assertions.assertNotNull(slot2);

        // 3. Booking on date1 outside shifts (13:00 - 13:30) -> Should fail
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createSimpleAppointmentSlot(
                    date1.atTime(13, 0),
                    "doc3",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. Strange outside specific date shifts should fail");

        // 4. Booking on date2 within shift (12:00 - 13:00) -> Should succeed
        var slot3 = appointmentService.createSimpleAppointmentSlot(
                date2.atTime(12, 0),
                "doc3",
                AppointmentType.MEDIUM
        );
        Assertions.assertNotNull(slot3);

        // 5. Booking on a completely unconfigured date (e.g. 3 days from now) -> Should fail
        LocalDate unconfiguredDate = LocalDate.now().plusDays(3);
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createSimpleAppointmentSlot(
                    unconfiguredDate.atTime(10, 0),
                    "doc3",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. Strange on an unconfigured day should fail");
    }

    @Test
    void testConcurrentBookingRaceCondition() throws InterruptedException {
        appointmentService.clearBookings();

        var doctorId = "doc1";
        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);
        var dateTime = nextMonday.atTime(14, 0);
        var type = AppointmentType.SHORT;
        var slot = appointmentService.createSimpleAppointmentSlot(dateTime, doctorId, type);

        int numThreads = 20;
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var successCount = new AtomicInteger(0);
        var latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final String patientId = "pat" + (i + 1);
            final var patient = new Patient(patientId, "Patient " + patientId);
            executor.submit(() -> {
                try {
                    if (appointmentService.registerSimpleAppointmentSlot(slot, patient)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("=================================================");
        System.out.println("CONCURRENT BOOKING TEST RESULTS");
        System.out.println("Total requests: " + numThreads);
        System.out.println("Successful bookings: " + successCount.get());
        System.out.println("Bookings in map: " + appointmentService.slots.size());
        System.out.println("=================================================");

        // In our naive thread-unsafe system, we expect successCount to be > 1 due to the race condition!
        Assertions.assertTrue(successCount.get() > 1,
                "Race condition did not occur! (Success count was " + successCount.get() + ")");
    }

    @Test
    void testAvailableSlotsDayFiltering() {
        appointmentService.clearBookings();

        var doctorId = "doc1"; // Dr. House: Mon, Wed, Fri 09:00-13:00, 14:00-17:00
        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);

        // Before any booking, retrieve available slots
        var availableSlotsBefore = appointmentService.getAvailableSlots(doctorId, nextMonday);
        Assertions.assertFalse(availableSlotsBefore.isEmpty(), "There should be available slots on working day");

        // Verify no slots are in lunch hour (13:00 - 14:00)
        boolean hasLunchHourSlot = availableSlotsBefore.stream()
                .anyMatch(dt -> dt.toLocalTime().equals(LocalTime.of(13, 0)) ||
                        dt.toLocalTime().equals(LocalTime.of(13, 30)));
        Assertions.assertFalse(hasLunchHourSlot, "No slot should be available during lunch break");

        // Register one booking: 10:00 - 11:00 (MEDIUM)
        var patient = appointmentService.patients.get("pat1");
        var slotToBook = appointmentService.createSimpleAppointmentSlot(nextMonday.atTime(10, 0), doctorId, AppointmentType.MEDIUM);
        Assertions.assertTrue(appointmentService.registerSimpleAppointmentSlot(slotToBook, patient));

        // Retrieve available slots again
        var availableSlotsAfter = appointmentService.getAvailableSlots(doctorId, nextMonday);

        // Verify the booked starting points (10:00 and 10:30) fall inside [10:00, 11:00) and are excluded
        Assertions.assertFalse(availableSlotsAfter.contains(nextMonday.atTime(10, 0)), "10:00 start time should be excluded");
        Assertions.assertFalse(availableSlotsAfter.contains(nextMonday.atTime(10, 30)), "10:30 start time should be excluded");
        
        // Verify surrounding slots are free
        Assertions.assertTrue(availableSlotsAfter.contains(nextMonday.atTime(9, 30)), "09:30 should be available");
        Assertions.assertTrue(availableSlotsAfter.contains(nextMonday.atTime(11, 0)), "11:00 should be available");
    }

    @Test
    void testAvailableSlotsWeekAndMonthFiltering() {
        appointmentService.clearBookings();

        var doctorId = "doc1"; // Dr. House: Mon, Wed, Fri
        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);

        // Week test
        var weeklySlots = appointmentService.getAvailableSlotsForWeek(doctorId, nextMonday);
        Assertions.assertFalse(weeklySlots.isEmpty(), "Weekly slots should not be empty");

        // Month test
        var monthlySlots = appointmentService.getAvailableSlotsForMonth(doctorId, nextMonday.getYear(), nextMonday.getMonthValue());
        Assertions.assertFalse(monthlySlots.isEmpty(), "Monthly slots should not be empty");

        // Year test
        var yearlySlots = appointmentService.getAvailableSlotsForYear(doctorId, nextMonday.getYear());
        Assertions.assertFalse(yearlySlots.isEmpty(), "Yearly slots should not be empty");
    }
}