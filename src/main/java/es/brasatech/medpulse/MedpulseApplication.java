package es.brasatech.medpulse;

//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

public class MedpulseApplication {

	public record Doctor(String doctorId, String name, List<AppointmentType> appointmentTypes) {}
	public record Patient(String patientId, String name) {}
	public record AppointmentSlot(Doctor doctor, LocalDateTime dateTime, AppointmentType type) {}
	public static HashMap<String, Doctor> doctors = new HashMap<String, Doctor>();
	public static HashMap<String, Patient> patients = new HashMap<String, Patient>();
	public static HashMap<AppointmentSlot, Patient> slots = new HashMap<AppointmentSlot, Patient>();

	static {
		// Initialize dummy doctors
		var doc1 = new Doctor("doc1", "Dr. House", List.of(AppointmentType.SHORT, AppointmentType.MEDIUM));
		var doc2 = new Doctor("doc2", "Dr. Grey", List.of(AppointmentType.MEDIUM, AppointmentType.LONG));
		doctors.put(doc1.doctorId(), doc1);
		doctors.put(doc2.doctorId(), doc2);

		// Initialize dummy patients
		var pat1 = new Patient("pat1", "John Doe");
		var pat2 = new Patient("pat2", "Jane Smith");
		patients.put(pat1.patientId(), pat1);
		patients.put(pat2.patientId(), pat2);
	}

	public static void clearBookings() {
		slots.clear();
	}

//	public static void main(String[] args) {
//		SpringApplication.run(MedpulseApplication.class, args);
//	}


	public static void main(String[] args) {

	}

	public static AppointmentSlot createSimpleAppointmentSlot(LocalDateTime appointmentDateTime, String doctorId, AppointmentType appointmentType) {
		var doctor = doctors.get(doctorId);
		if (doctor == null) {
			throw new IllegalArgumentException("Doctor not found: " + doctorId);
		}
		if (!doctor.appointmentTypes.contains(appointmentType)) {
			System.out.printf("Doctor %s does not have permissions to be assigned for appointment type %s\n", doctor.name(), appointmentType);
		}
		return new AppointmentSlot(doctor, appointmentDateTime, appointmentType);
	}

	public static boolean registerSimpleAppointmentSlot(AppointmentSlot slot, Patient patient) {
		if (slot == null || patient == null) {
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
					System.out.printf("Overlap detected! Doctor %s is already booked from %s to %s. Desired slot is %s to %s.\n",
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
