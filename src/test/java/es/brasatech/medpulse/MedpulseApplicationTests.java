package es.brasatech.medpulse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

class MedpulseApplicationTests {

	private static LocalDate getNextDayOfWeek(DayOfWeek day) {
		LocalDate date = LocalDate.now().plusDays(1);
		while (date.getDayOfWeek() != day) {
			date = date.plusDays(1);
		}
		return date;
	}

	@Test
	void testSingleThreadedCorrectBooking() {
		MedpulseApplication.clearBookings();

		var doctorId = "doc1";
		var patient = MedpulseApplication.patients.get("pat1");
		
		// Dynamically obtain next Monday (Dr. House works M, W, F 09:00 - 13:00, 14:00 - 17:00)
		LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);
		var dateTime = nextMonday.atTime(10, 0);
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

		LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);
		LocalDate nextTuesday = getNextDayOfWeek(DayOfWeek.TUESDAY);

		// Book doc1 on next Monday: 10:00 to 10:30 (SHORT)
		var slot1 = MedpulseApplication.createSimpleAppointmentSlot(nextMonday.atTime(10, 0), doc1, AppointmentType.SHORT);
		Assertions.assertTrue(MedpulseApplication.registerSimpleAppointmentSlot(slot1, pat1));

		// 1. Same doctor, exact same slot -> Fail
		var slotDuplicate = new MedpulseApplication.AppointmentSlot(MedpulseApplication.doctors.get(doc1), nextMonday.atTime(10, 0), AppointmentType.SHORT);
		Assertions.assertFalse(MedpulseApplication.registerSimpleAppointmentSlot(slotDuplicate, pat2), "Duplicate booking should fail");

		// 2. Same doctor, overlapping slot starting inside the existing one -> Fail (10:15 to 10:45)
		var slotOverlappingStart = new MedpulseApplication.AppointmentSlot(MedpulseApplication.doctors.get(doc1), nextMonday.atTime(10, 15), AppointmentType.SHORT);
		Assertions.assertFalse(MedpulseApplication.registerSimpleAppointmentSlot(slotOverlappingStart, pat2), "Overlapping booking (start inside) should fail");

		// 3. Same doctor, overlapping slot wrapping the existing one -> Fail (09:45 to 10:45)
		var slotOverlappingWrap = new MedpulseApplication.AppointmentSlot(MedpulseApplication.doctors.get(doc1), nextMonday.atTime(9, 45), AppointmentType.MEDIUM);
		Assertions.assertFalse(MedpulseApplication.registerSimpleAppointmentSlot(slotOverlappingWrap, pat2), "Overlapping booking (wrapping existing) should fail");

		// 4. Same doctor, adjacent slot -> Succeed (10:30 to 11:00)
		var slotAdjacent = MedpulseApplication.createSimpleAppointmentSlot(nextMonday.atTime(10, 30), doc1, AppointmentType.SHORT);
		Assertions.assertTrue(MedpulseApplication.registerSimpleAppointmentSlot(slotAdjacent, pat2), "Adjacent non-overlapping booking should succeed");

		// 5. Different doctor, valid slot on a day they work (doc2 works Tuesday) -> Succeed
		var slotDifferentDoctor = MedpulseApplication.createSimpleAppointmentSlot(nextTuesday.atTime(10, 0), doc2, AppointmentType.MEDIUM);
		Assertions.assertTrue(MedpulseApplication.registerSimpleAppointmentSlot(slotDifferentDoctor, pat2), "Booking different doctor for valid slot should succeed");
	}

	@Test
	void testDoctorWorkingDaysRestriction() {
		// doc1 (Dr. House) works M, W, F. Let's try to book him on next Tuesday
		LocalDate nextTuesday = getNextDayOfWeek(DayOfWeek.TUESDAY);
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
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
			MedpulseApplication.createSimpleAppointmentSlot(
				nextMonday.atTime(8, 30), 
				"doc1", 
				AppointmentType.SHORT
			);
		}, "Booking Dr. House at 08:30 should fail working hours check");

		// 2. Try to book too late (16:45) with a MEDIUM appointment (60 mins, ends at 17:45)
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
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
		MedpulseApplication.companyClosedDates.add(nextSaturday);

		try {
			Assertions.assertThrows(IllegalStateException.class, () -> {
				MedpulseApplication.createSimpleAppointmentSlot(
					nextSaturday.atTime(10, 0), 
					"doc2", 
					AppointmentType.MEDIUM
				);
			}, "Booking on a company closed date should fail");
		} finally {
			// Cleanup closed date to not pollute other tests
			MedpulseApplication.companyClosedDates.remove(nextSaturday);
		}
	}

	@Test
	void testDoctorLunchBreakRestriction() {
		// doc1 (Dr. House) works Monday: 09:00 - 13:00 and 14:00 - 17:00. Lunch break is 13:00 - 14:00.
		LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);

		// 1. Booking fully during lunch break (13:00 - 13:30) -> Should fail
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
				nextMonday.atTime(13, 0), 
				"doc1",
				AppointmentType.SHORT
			);
		}, "Booking Dr. House during lunch hour should fail shift check");

		// 2. Booking crossing the start boundary (12:45 - 13:15) -> Should fail
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
				nextMonday.atTime(12, 45), 
				"doc1",
				AppointmentType.SHORT
			);
		}, "Booking Dr. House crossing lunch hour start should fail shift check");

		// 3. Booking crossing the end boundary (13:45 - 14:15) -> Should fail
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
				nextMonday.atTime(13, 45), 
				"doc1",
				AppointmentType.SHORT
			);
		}, "Booking Dr. House crossing lunch hour end should fail shift check");

		// 4. Booking inside afternoon shift (14:00 - 14:30) -> Should succeed
		var slotAfternoon = MedpulseApplication.createSimpleAppointmentSlot(
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
		var slot1 = MedpulseApplication.createSimpleAppointmentSlot(
			date1.atTime(9, 0),
			"doc3",
			AppointmentType.SHORT
		);
		Assertions.assertNotNull(slot1);

		// 2. Booking on date1 during the second shift (17:00 - 17:30) -> Should succeed
		var slot2 = MedpulseApplication.createSimpleAppointmentSlot(
			date1.atTime(17, 0),
			"doc3",
			AppointmentType.SHORT
		);
		Assertions.assertNotNull(slot2);

		// 3. Booking on date1 outside shifts (13:00 - 13:30) -> Should fail
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
				date1.atTime(13, 0),
				"doc3",
				AppointmentType.SHORT
			);
		}, "Booking Dr. Strange outside specific date shifts should fail");

		// 4. Booking on date2 within shift (12:00 - 13:00) -> Should succeed
		var slot3 = MedpulseApplication.createSimpleAppointmentSlot(
			date2.atTime(12, 0),
			"doc3",
			AppointmentType.MEDIUM
		);
		Assertions.assertNotNull(slot3);

		// 5. Booking on a completely unconfigured date (e.g. 3 days from now) -> Should fail
		LocalDate unconfiguredDate = LocalDate.now().plusDays(3);
		Assertions.assertThrows(IllegalStateException.class, () -> {
			MedpulseApplication.createSimpleAppointmentSlot(
				unconfiguredDate.atTime(10, 0),
				"doc3",
				AppointmentType.SHORT
			);
		}, "Booking Dr. Strange on an unconfigured day should fail");
	}

	@Test
	void testConcurrentBookingRaceCondition() throws InterruptedException {
		MedpulseApplication.clearBookings();

		var doctorId = "doc1";
		LocalDate nextMonday = getNextDayOfWeek(DayOfWeek.MONDAY);
		var dateTime = nextMonday.atTime(14, 0);
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
