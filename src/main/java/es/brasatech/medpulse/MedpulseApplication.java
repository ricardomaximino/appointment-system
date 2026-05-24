package es.brasatech.medpulse;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MedpulseApplication {

	public record TimeRange(LocalTime start, LocalTime end) {
		public boolean contains(LocalTime tStart, LocalTime tEnd) {
			return !tStart.isBefore(start) && !tEnd.isAfter(end);
		}
	}

	public record Doctor(
		String doctorId,
		String name,
		List<AppointmentType> appointmentTypes,
		Map<DayOfWeek, List<TimeRange>> weeklyAvailability,
		Map<LocalDate, List<TimeRange>> specificDatesAvailability
	) {
		public Doctor(String doctorId, String name, List<AppointmentType> appointmentTypes, Map<DayOfWeek, List<TimeRange>> weeklyAvailability) {
			this(doctorId, name, appointmentTypes, weeklyAvailability, Map.of());
		}
	}

	public record Patient(String patientId, String name) {}
	public record AppointmentSlot(Doctor doctor, LocalDateTime dateTime, AppointmentType type) {}

	public static Map<String, Doctor> doctors = new HashMap<>();
	public static Map<String, Patient> patients = new HashMap<>();
	public static Map<AppointmentSlot, Patient> slots = new HashMap<>();
	public static Set<LocalDate> companyClosedDates = new HashSet<>();

	static {
		// Initialize dummy doctors with availability schedules (split shifts / lunch breaks)
		var houseAvailability = Map.of(
			DayOfWeek.MONDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))),
			DayOfWeek.WEDNESDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))),
			DayOfWeek.FRIDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)), new TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0)))
		);
		var doc1 = new Doctor("doc1", "Dr. House", List.of(AppointmentType.SHORT, AppointmentType.MEDIUM), houseAvailability);

		var greyAvailability = Map.of(
			DayOfWeek.TUESDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0))),
			DayOfWeek.THURSDAY, List.of(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0))),
			DayOfWeek.SATURDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)))
		);
		var doc2 = new Doctor("doc2", "Dr. Grey", List.of(AppointmentType.MEDIUM, AppointmentType.LONG), greyAvailability);

		// Seed doc3 (Dr. Strange) with specific date availabilities using dynamic relative dates
		var strangeAvailability = Map.of(
			LocalDate.now().plusDays(2), List.of(new TimeRange(LocalTime.of(8, 30), LocalTime.of(12, 0)), new TimeRange(LocalTime.of(17, 0), LocalTime.of(18, 0))),
			LocalDate.now().plusDays(5), List.of(new TimeRange(LocalTime.of(11, 0), LocalTime.of(17, 0)))
		);
		var doc3 = new Doctor("doc3", "Dr. Strange", List.of(AppointmentType.SHORT, AppointmentType.MEDIUM, AppointmentType.LONG), Map.of(), strangeAvailability);

		doctors.put(doc1.doctorId(), doc1);
		doctors.put(doc2.doctorId(), doc2);
		doctors.put(doc3.doctorId(), doc3);

		// Initialize dummy patients
		var pat1 = new Patient("pat1", "John Doe");
		var pat2 = new Patient("pat2", "Jane Smith");
		patients.put(pat1.patientId(), pat1);
		patients.put(pat2.patientId(), pat2);

		// Default company closed date for testing: e.g. 15 days from now
		companyClosedDates.add(LocalDate.now().plusDays(15));
	}

	static void main(String[] args) {}

	public static void clearBookings() {
		slots.clear();
	}


	public static void validateAppointmentSlotAvailability(AppointmentSlot slot) {
		if (slot == null) {
			throw new IllegalArgumentException("Slot cannot be null");
		}
		Doctor doctor = slot.doctor();
		LocalDateTime startDateTime = slot.dateTime();
		LocalDate appointmentDate = startDateTime.toLocalDate();
		LocalTime startTime = startDateTime.toLocalTime();
		LocalTime endTime = startTime.plus(slot.type().getDuration());

		// 1. Check if the company is closed
		if (companyClosedDates.contains(appointmentDate)) {
			throw new IllegalStateException("Appointment cannot be scheduled: Company is closed on " + appointmentDate);
		}

		// 2. Resolve active shifts for the day (prioritize specific dates over weekly recurring schedules)
		List<TimeRange> shifts = null;
		if (doctor.specificDatesAvailability() != null && doctor.specificDatesAvailability().containsKey(appointmentDate)) {
			shifts = doctor.specificDatesAvailability().get(appointmentDate);
		} else {
			DayOfWeek dayOfWeek = startDateTime.getDayOfWeek();
			if (doctor.weeklyAvailability() != null && doctor.weeklyAvailability().containsKey(dayOfWeek)) {
				shifts = doctor.weeklyAvailability().get(dayOfWeek);
			}
		}

		if (shifts == null || shifts.isEmpty()) {
			throw new IllegalStateException("Appointment cannot be scheduled: Doctor %s does not work on %s".formatted(doctor.name(), appointmentDate));
		}

		// 3. Check doctor's working shifts (must fall fully within at least one working shift)
		boolean fitsInShift = false;
		for (TimeRange shift : shifts) {
			if (shift.contains(startTime, endTime)) {
				fitsInShift = true;
				break;
			}
		}

		if (!fitsInShift) {
			throw new IllegalStateException("Appointment cannot be scheduled: Desired time %s - %s is outside Doctor %s's working shifts for %s".formatted(
					startTime, endTime, doctor.name(), appointmentDate));
		}
	}

	public static AppointmentSlot createSimpleAppointmentSlot(LocalDateTime appointmentDateTime, String doctorId, AppointmentType appointmentType) {
		var doctor = doctors.get(doctorId);
		if (doctor == null) {
			throw new IllegalArgumentException("Doctor not found: " + doctorId);
		}
		if (!doctor.appointmentTypes.contains(appointmentType)) {
			System.out.printf("Doctor %s does not have permissions to be assigned for appointment type %s\n", doctor.name(), appointmentType);
		}
		var slot = new AppointmentSlot(doctor, appointmentDateTime, appointmentType);
		validateAppointmentSlotAvailability(slot);
		return slot;
	}

	public static boolean registerSimpleAppointmentSlot(AppointmentSlot slot, Patient patient) {
		if (slot == null || patient == null) {
			return false;
		}

		// Validate availability rules
		try {
			validateAppointmentSlotAvailability(slot);
		} catch (IllegalStateException e) {
			System.out.println("Registration failed: " + e.getMessage());
			return false;
		}

		LocalDateTime startNew = slot.dateTime();
		LocalDateTime endNew = startNew.plus(slot.type().getDuration());

		// Check if doctor has any overlapping appointment slot
		for (var entry : slots.entrySet()) {
			var bookedSlot = entry.getKey();
			if (bookedSlot.doctor().doctorId().equals(slot.doctor().doctorId())) {
				LocalDateTime startBooked = bookedSlot.dateTime();
				LocalDateTime endBooked = startBooked.plus(bookedSlot.type().getDuration());

				// Overlap condition: startNew < endBooked AND startBooked < endNew
				if (startNew.isBefore(endBooked) && startBooked.isBefore(endNew)) {
					System.out.printf("Overlap detected! Doctor %s is already booked from %s to %s. Desired slot is %s to %s.%n",
							slot.doctor().name(), startBooked, endBooked, startNew, endNew);
					return false;
				}
			}
		}

		// Artificial tiny delay to simulate core business logic validation processing and trigger race conditions in concurrent execution
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		slots.put(slot, patient);
		System.out.println("Appointment slot [doctor: %s, start: %s, end: %s ] was registered successfully!".formatted(slot.doctor().name(), startNew, endNew));
		return true;
	}

}

enum AppointmentType {
	SHORT(30L),
	MEDIUM(60L),
	LONG(90L);
	private final long duration;
	private AppointmentType(long duration) {
		this.duration = duration;
	}

	public Duration getDuration() {
		return Duration.ofMinutes(this.duration);
	}
}
