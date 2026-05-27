package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.*;
import es.brasatech.medpulse.service.impl.ConcurrentAppointmentServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
class ConcurrentAppointmentServiceTest {

    @Autowired
    @Qualifier("concurrentAppointmentServiceImpl")
    private ConcurrentAppointmentServiceImpl appointmentService;

    @Autowired
    private DomainDataService domainDataService;

    private Doctor getDoctor(String doctorId) {
        return domainDataService.findDoctorById(doctorId);
    }

    private Patient getPatient(String patientId) {
        return domainDataService.findPatientById(patientId);
    }

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
        var patient = getPatient("pat1");

        // Dynamically obtain next Monday (Dr. House works M, W, F 09:00 - 13:00, 14:00 - 17:00)
        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);
        var dateTime = nextMonday.atTime(10, 0);
        var type = AppointmentType.SHORT; // 30 minutes duration

        var slot = appointmentService.createAppointmentSlot(dateTime, doctorId, type);
        boolean success = appointmentService.registerAppointmentSlot(slot, patient);

        Assertions.assertTrue(success, "First booking should succeed");
        Assertions.assertEquals(1, domainDataService.getAppointmentCount(), "There should be 1 booking in the system");

        var bookedOpt = domainDataService.findBookedAppointmentsForDoctor(doctorId).stream()
                .filter(app -> app.getDateTime().equals(dateTime))
                .findFirst();
        Assertions.assertTrue(bookedOpt.isPresent(), "Appointment should be found in database");
        Assertions.assertEquals(patient.getPatientId(), bookedOpt.get().getPatient().getPatientId(), "Patient should match");
    }

    @Test
    void testSingleThreadedOverlapPrevention() {
        appointmentService.clearBookings();

        var doc1 = "doc1";
        var doc2 = "doc2";
        var pat1 = getPatient("pat1");
        var pat2 = getPatient("pat2");

        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);
        LocalDate nextTuesday = getNextDayOfWeek(DayOfWeek.TUESDAY);

        // Book doc1 on next Monday: 10:00 to 10:30 (SHORT)
        var slot1 = appointmentService.createAppointmentSlot(nextMonday.atTime(10, 0), doc1, AppointmentType.SHORT);
        Assertions.assertTrue(appointmentService.registerAppointmentSlot(slot1, pat1));

        // 1. Same doctor, exact same slot -> Fail
        var slotDuplicate = new AppointmentSlot(getDoctor(doc1), nextMonday.atTime(10, 0), AppointmentType.SHORT);
        Assertions.assertFalse(appointmentService.registerAppointmentSlot(slotDuplicate, pat2), "Duplicate booking should fail");

        // 2. Same doctor, overlapping slot starting inside the existing one -> Fail (10:15 to 10:45)
        var slotOverlappingStart = new AppointmentSlot(getDoctor(doc1), nextMonday.atTime(10, 15), AppointmentType.SHORT);
        Assertions.assertFalse(appointmentService.registerAppointmentSlot(slotOverlappingStart, pat2), "Overlapping booking (start inside) should fail");

        // 3. Same doctor, overlapping slot wrapping the existing one -> Fail (09:45 to 10:45)
        var slotOverlappingWrap = new AppointmentSlot(getDoctor(doc1), nextMonday.atTime(9, 45), AppointmentType.MEDIUM);
        Assertions.assertFalse(appointmentService.registerAppointmentSlot(slotOverlappingWrap, pat2), "Overlapping booking (wrapping existing) should fail");

        // 4. Same doctor, adjacent slot -> Succeed (10:30 to 11:00)
        var slotAdjacent = appointmentService.createAppointmentSlot(nextMonday.atTime(10, 30), doc1, AppointmentType.SHORT);
        Assertions.assertTrue(appointmentService.registerAppointmentSlot(slotAdjacent, pat2), "Adjacent non-overlapping booking should succeed");

        // 5. Different doctor, valid slot on a day they work (doc2 works Tuesday) -> Succeed
        var slotDifferentDoctor = appointmentService.createAppointmentSlot(nextTuesday.atTime(10, 0), doc2, AppointmentType.MEDIUM);
        Assertions.assertTrue(appointmentService.registerAppointmentSlot(slotDifferentDoctor, pat2), "Booking different doctor for valid slot should succeed");
    }

    @Test
    void testDoctorWorkingDaysRestriction() {
        // doc1 (Dr. House) works M, W, F. Let's try to book him on next Tuesday
        LocalDate nextTuesday = getNextDayOfWeek(DayOfWeek.TUESDAY);
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createAppointmentSlot(
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
            appointmentService.createAppointmentSlot(
                    nextMonday.atTime(8, 30),
                    "doc1",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. House at 08:30 should fail working hours check");

        // 2. Try to book too late (16:45) with a MEDIUM appointment (60 mins, ends at 17:45)
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createAppointmentSlot(
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
        domainDataService.addCompanyClosedDate(nextSaturday);

        try {
            Assertions.assertThrows(IllegalStateException.class, () -> {
                appointmentService.createAppointmentSlot(
                        nextSaturday.atTime(10, 0),
                        "doc2",
                        AppointmentType.MEDIUM
                );
            }, "Booking on a company closed date should fail");
        } finally {
            // Cleanup closed date to not pollute other tests
            domainDataService.removeCompanyClosedDate(nextSaturday);
        }
    }

    @Test
    void testDoctorLunchBreakRestriction() {
        // doc1 (Dr. House) works Monday: 09:00 - 13:00 and 14:00 - 17:00. Lunch break is 13:00 - 14:00.
        LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);

        // 1. Booking fully during lunch break (13:00 - 13:30) -> Should fail
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createAppointmentSlot(
                    nextMonday.atTime(13, 0),
                    "doc1",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. House during lunch hour should fail shift check");

        // 2. Booking crossing the start boundary (12:45 - 13:15) -> Should fail
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createAppointmentSlot(
                    nextMonday.atTime(12, 45),
                    "doc1",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. House crossing lunch hour start should fail shift check");

        // 3. Booking crossing the end boundary (13:45 - 14:15) -> Should fail
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createAppointmentSlot(
                    nextMonday.atTime(13, 45),
                    "doc1",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. House crossing lunch hour end should fail shift check");

        // 4. Booking inside afternoon shift (14:00 - 14:30) -> Should succeed
        var slotAfternoon = appointmentService.createAppointmentSlot(
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
        var slot1 = appointmentService.createAppointmentSlot(
                date1.atTime(9, 0),
                "doc3",
                AppointmentType.SHORT
        );
        Assertions.assertNotNull(slot1);

        // 2. Booking on date1 during the second shift (17:00 - 17:30) -> Should succeed
        var slot2 = appointmentService.createAppointmentSlot(
                date1.atTime(17, 0),
                "doc3",
                AppointmentType.SHORT
        );
        Assertions.assertNotNull(slot2);

        // 3. Booking on date1 outside shifts (13:00 - 13:30) -> Should fail
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createAppointmentSlot(
                    date1.atTime(13, 0),
                    "doc3",
                    AppointmentType.SHORT
            );
        }, "Booking Dr. Strange outside specific date shifts should fail");

        // 4. Booking on date2 within shift (12:00 - 13:00) -> Should succeed
        var slot3 = appointmentService.createAppointmentSlot(
                date2.atTime(12, 0),
                "doc3",
                AppointmentType.MEDIUM
        );
        Assertions.assertNotNull(slot3);

        // 5. Booking on a completely unconfigured date (e.g. 3 days from now) -> Should fail
        LocalDate unconfiguredDate = LocalDate.now().plusDays(3);
        Assertions.assertThrows(IllegalStateException.class, () -> {
            appointmentService.createAppointmentSlot(
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
        var slot = appointmentService.createAppointmentSlot(dateTime, doctorId, type);

        int numThreads = 20;
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var successCount = new AtomicInteger(0);
        var latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final String patientId = "pat" + (i + 1);
            final var patient = new Patient(patientId, "Patient " + patientId);
            executor.submit(() -> {
                try {
                    if (appointmentService.registerAppointmentSlot(slot, patient)) {
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
        System.out.println("CONCURRENT BOOKING TEST RESULTS (ConcurrentService)");
        System.out.println("Total requests: " + numThreads);
        System.out.println("Successful bookings: " + successCount.get());
        System.out.println("Bookings in map: " + domainDataService.getAppointmentCount());
        System.out.println("=================================================");

        // In our thread-safe system, we expect successCount to be exactly 1!
        Assertions.assertEquals(1, successCount.get(),
                "Race condition occurred and double booking allowed! (Success count was " + successCount.get() + ")");
    }

    @Test
    void testHighVolumeConcurrentThroughput() throws InterruptedException {
        System.setProperty("benchmark.active", "true");
        try {
            appointmentService.clearBookings();

            int numRequests = 5000;
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            var successfulBookings = new AtomicInteger(0);
            var failedBookings = new AtomicInteger(0);
            var readSuccessCount = new AtomicInteger(0);

            LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);
            LocalDate nextWednesday = getNextDayOfWeek(DayOfWeek.WEDNESDAY);

            var startNano = System.nanoTime();
            var latch = new CountDownLatch(numRequests);

            for (int i = 0; i < numRequests; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        String doctorId = "doc" + ((index % 3) + 1); // doc1, doc2, doc3
                        LocalDate targetDate = (index % 2 == 0) ? nextMonday : nextWednesday;

                        if (index % 5 != 0) {
                            // 80% Read Queries: Get available slots (suppress printing inside loops for high-speed benchmark)
                            var slots = appointmentService.getAvailableSlots(doctorId, targetDate);
                            if (slots != null) {
                                readSuccessCount.incrementAndGet();
                            }
                        } else {
                            // 20% Write Queries: Book a slot
                            int hour = 9 + (index % 8); // 9:00 to 16:00
                            var dateTime = targetDate.atTime(hour, 0);
                            var patient = new Patient("pat_" + index, "Patient " + index);

                            try {
                                var slot = appointmentService.createAppointmentSlot(dateTime, doctorId, AppointmentType.SHORT);
                                if (appointmentService.registerAppointmentSlot(slot, patient)) {
                                    successfulBookings.incrementAndGet();
                                } else {
                                    failedBookings.incrementAndGet();
                                }
                            } catch (Exception e) {
                                failedBookings.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            var durationMs = (System.nanoTime() - startNano) / 1_000_000.0;
            executor.shutdown();

            double opsPerSecond = (numRequests / durationMs) * 1000.0;

            System.out.println("\n=================================================");
            System.out.println("HIGH-VOLUME CONCURRENT THROUGHPUT PERFORMANCE");
            System.out.println("=================================================");
            System.out.printf("Total Requests Processed : %d\n", numRequests);
            System.out.printf("Availability Read Queries: %d\n", readSuccessCount.get());
            System.out.printf("Successful Bookings      : %d\n", successfulBookings.get());
            System.out.printf("Rejected/Failed Bookings : %d\n", failedBookings.get());
            System.out.printf("Total Execution Time     : %.2f ms\n", durationMs);
            System.out.printf("Average Latency per Req  : %.3f ms\n", durationMs / numRequests);
            System.out.printf("Throughput               : %.2f req/sec\n", opsPerSecond);
            System.out.println("=================================================\n");

            Assertions.assertTrue(opsPerSecond > 1000.0, "Throughput should easily exceed 1000 req/sec");
        } finally {
            System.clearProperty("benchmark.active");
        }
    }
}
