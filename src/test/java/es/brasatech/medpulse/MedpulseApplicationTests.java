package es.brasatech.medpulse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

class MedpulseApplicationTests {

	@Test
	void testSingleThreadedCorrectBooking() {
		MedpulseApplication.clearBookings();

		var doctorId = "doc1";
		var patient = MedpulseApplication.patients.get("pat1");
		// 2026-06-01 is Monday (Dr. House works 09:00 - 17:00)
		var dateTime = LocalDateTime.of(2026, 6, 1, 10, 0);
		var type = AppointmentType.SHORT; // 30 minutes duration

		var slot = MedpulseApplication.createSimpleAppointmentSlot(dateTime, doctorId, type);
		boolean success = MedpulseApplication.registerSimpleAppointmentSlot(slot, patient);

		Assertions.assertTrue(success, "First booking should succeed");
		Assertions.assertEquals(1, MedpulseApplication.slots.size(), "There should be 1 booking in the system");
		Assertions.assertEquals(patient, MedpulseApplication.slots.get(slot));
	}

	@Test
	void testSingleThreadedOverlapPrevention() {
		MedpulseApplication.clearBookings();

		var doc1 = "doc1";
		var doc2 = "doc2";
		var pat1 = MedpulseApplication.patients.get("pat1");
		var pat2 = MedpulseApplication.patients.get("pat2");

		// Book doc1 on Monday (works M, W, F): 10:00 to 10:30 (SHORT)
		var slot1 = MedpulseApplication.createSimpleAppointmentSlot(LocalDateTime.of(2026, 6, 1, 10, 0), doc1, AppointmentType.SHORT);
		Assertions.assertTrue(MedpulseApplication.registerSimpleAppointmentSlot(slot1, pat1));

		// 1. Same doctor, exact same slot -> Fail
		// Since we try to create an already overlapping slot, we must be careful:
		// createSimpleAppointmentSlot doesn't check overlaps (only registerSimpleAppointmentSlot checks overlaps),
		// but createSimpleAppointmentSlot DOES check doctor schedule & company closed dates.
		// So creating it is allowed, but registering it must fail!
		var slotDuplicate = new MedpulseApplication.AppointmentSlot(MedpulseApplication.doctors.get(doc1), LocalDateTime.of(2026, 6, 1, 10, 0), AppointmentType.SHORT);
		Assertions.assertFalse(MedpulseApplication.registerSimpleAppointmentSlot(slotDuplicate, pat2), "Duplicate booking should fail");

		// 2. Same doctor, overlapping slot starting inside the existing one -> Fail (10:15 to 10:45)
		var slotOverlappingStart = new MedpulseApplication.AppointmentSlot(MedpulseApplication.doctors.get(doc1), LocalDateTime.of(2026, 6, 1, 10, 15), AppointmentType.SHORT);
		Assertions.assertFalse(MedpulseApplication.registerSimpleAppointmentSlot(slotOverlappingStart, pat2), "Overlapping booking (start inside) should fail");

		// 3. Same doctor, overlapping slot wrapping the existing one -> Fail (09:45 to 11:15)
		var slotOverlappingWrap = new MedpulseApplication.AppointmentSlot(MedpulseApplication.doctors.get(doc1), LocalDateTime.of(2026, 6, 1, 9, 45), AppointmentType.MEDIUM);
		Assertions.assertFalse(MedpulseApplication.registerSimpleAppointmentSlot(slotOverlappingWrap, pat2), "Overlapping booking (wrapping existing) should fail");

		// 4. Same doctor, adjacent slot -> Succeed (10:30 to 11:00)
		var slotAdjacent = MedpulseApplication.createSimpleAppointmentSlot(LocalDateTime.of(2026, 6, 1, 10, 30), doc1, AppointmentType.SHORT);
		Assertions.assertTrue(MedpulseApplication.registerSimpleAppointmentSlot(slotAdjacent, pat2), "Adjacent non-overlapping booking should succeed");

		// 5. Different doctor, valid slot on a day they work (doc2 works Tuesday 2026-06-02) -> Succeed
		var slotDifferentDoctor = MedpulseApplication.createSimpleAppointmentSlot(LocalDateTime.of(2026, 6, 2, 10, 0), doc2, AppointmentType.MEDIUM);
		Assertions.assertTrue(MedpulseApplication.registerSimpleAppointmentSlot(slotDifferentDoctor, pat2), "Booking different doctor for valid slot should succeed");
	}

	@Test
	void testDoctorWorkingDaysRestriction() {
		// doc1 (Dr. House) works M, W, F. Let's try to book him on a Tuesday (2026-06-02)
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
				LocalDateTime.of(2026, 6, 2, 10, 0), // Tuesday
				"doc1", 
				AppointmentType.SHORT
			);
		}, "Booking Dr. House on Tuesday should fail weekly availability check");
	}

	@Test
	void testDoctorWorkingHoursRestriction() {
		// doc1 (Dr. House) works 09:00 - 17:00.
		
		// 1. Try to book too early (08:30)
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
				LocalDateTime.of(2026, 6, 1, 8, 30), // Monday
				"doc1", 
				AppointmentType.SHORT
			);
		}, "Booking Dr. House at 08:30 should fail working hours check");

		// 2. Try to book too late (16:45) with a MEDIUM appointment (60 mins, ends at 17:45)
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
				LocalDateTime.of(2026, 6, 1, 16, 45), // Monday
				"doc1", 
				AppointmentType.MEDIUM
			);
		}, "Booking Dr. House ending past 17:00 should fail working hours check");
	}

	@Test
	void testCompanyClosedDatesRestriction() {
		// July 4th, 2026 is registered as closed in the static block. Let's try to schedule then
		// Note: July 4th, 2026 is a Saturday, so let's check doc2 who works Saturdays
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
				LocalDateTime.of(2026, 7, 4, 10, 0), // Closed holiday
				"doc2", 
				AppointmentType.MEDIUM
			);
		}, "Booking on a company closed date should fail");
	}

	@Test
	void testConcurrentBookingRaceCondition() throws InterruptedException {
		MedpulseApplication.clearBookings();

		var doctorId = "doc1";
		// Monday, June 1st. doc1 works!
		var dateTime = LocalDateTime.of(2026, 6, 1, 14, 0);
		var type = AppointmentType.SHORT;
		var slot = MedpulseApplication.createSimpleAppointmentSlot(dateTime, doctorId, type);

		int numThreads = 20;
		var executor = Executors.newVirtualThreadPerTaskExecutor();
		var successCount = new AtomicInteger(0);
		var latch = new CountDownLatch(numThreads);

		for (int i = 0; i < numThreads; i++) {
			final String patientId = "pat" + (i + 1);
			final var patient = new MedpulseApplication.Patient(patientId, "Patient " + patientId);
			executor.submit(() -> {
				try {
					if (MedpulseApplication.registerSimpleAppointmentSlot(slot, patient)) {
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
		System.out.println("Bookings in map: " + MedpulseApplication.slots.size());
		System.out.println("=================================================");

		// In our naive thread-unsafe system, we expect successCount to be > 1 due to the race condition!
		Assertions.assertTrue(successCount.get() > 1,
				"Race condition did not occur! (Success count was " + successCount.get() + ")");
	}

}
