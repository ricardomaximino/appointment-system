package es.brasatech.medpulse.service;

import es.brasatech.medpulse.domain.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.*;

public class CalendarConsolePrinter {

    public static void printDayCalendar(Doctor doctor, LocalDate date, List<LocalDateTime> availableSlots, Set<AppointmentSlot> allBookedSlots) {
        System.out.println("\n+-------------------------------------------------------------+");
        System.out.printf("|  DAILY SCHEDULE FOR %-40s|\n", doctor.getName() + " - " + date + " (" + date.getDayOfWeek() + ")");
        System.out.println("+-------------------------------------------------------------+");
        System.out.println("| Time Slot | Status                                          |");
        System.out.println("+-----------+-------------------------------------------------+");
        
        List<TimeRange> shifts = null;
        if (doctor.getSpecificDatesAvailability() != null && doctor.getSpecificDatesAvailability().containsKey(date)) {
            shifts = doctor.getSpecificDatesAvailability().get(date);
        } else {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (doctor.getWeeklyAvailability() != null && doctor.getWeeklyAvailability().containsKey(dayOfWeek)) {
                shifts = doctor.getWeeklyAvailability().get(dayOfWeek);
            }
        }
        
        if (shifts == null || shifts.isEmpty()) {
            System.out.println("|                   OFF - NO ACTIVE SHIFTS                    |");
        } else {
            long stepMinutes = doctor.getAppointmentTypes().stream()
                    .mapToLong(t -> t.getDuration().toMinutes())
                    .min()
                    .orElse(30);

            for (TimeRange shift : shifts) {
                LocalTime time = shift.start();
                while (time.isBefore(shift.end())) {
                    LocalDateTime candidateStart = date.atTime(time);
                    
                    boolean isBooked = allBookedSlots.stream().anyMatch(bs -> 
                        bs.getDoctor().getDoctorId().equals(doctor.getDoctorId()) &&
                        (bs.getDateTime().equals(candidateStart) || 
                         (!candidateStart.isBefore(bs.getDateTime()) && candidateStart.isBefore(bs.getDateTime().plus(bs.getType().getDuration()))))
                    );
                    
                    String status = isBooked ? "[ BOOKED ]" : "[ AVAILABLE ]";
                    System.out.printf("| %-9s | %-47s |\n", time, status);
                    
                    time = time.plusMinutes(stepMinutes);
                }
            }
        }
        System.out.println("+-------------------------------------------------------------+");
    }

    public static void printWeekCalendar(Doctor doctor, LocalDate date, List<LocalDateTime> weeklyAvailableSlots) {
        LocalDate startOfWeek = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        System.out.println("\n+----------------------------------------------------------------------------------------------------+");
        System.out.printf("|  WEEKLY OVERVIEW FOR %-76s  |\n", doctor.getName() + " - Week of " + startOfWeek);
        System.out.println("+----------------------------------------------------------------------------------------------------+");
        
        StringBuilder header = new StringBuilder("|");
        StringBuilder slotCounts = new StringBuilder("|");
        for (int i = 0; i < 7; i++) {
            LocalDate current = startOfWeek.plusDays(i);
            String dayLabel = current.getDayOfWeek().toString().substring(0, 3) + " (" + current.getMonthValue() + "/" + current.getDayOfMonth() + ")";
            header.append(String.format(" %-11s |", dayLabel));
            
            long count = weeklyAvailableSlots.stream()
                .filter(dt -> dt.toLocalDate().equals(current))
                .count();
            
            boolean works = false;
            if (doctor.getSpecificDatesAvailability() != null && doctor.getSpecificDatesAvailability().containsKey(current)) {
                works = !doctor.getSpecificDatesAvailability().get(current).isEmpty();
            } else {
                works = doctor.getWeeklyAvailability() != null && doctor.getWeeklyAvailability().containsKey(current.getDayOfWeek());
            }
            
            String countLabel = works ? count + " spots" : "OFF";
            slotCounts.append(String.format(" %-11s |", countLabel));
        }
        System.out.println(header.toString());
        System.out.println("+--------------+--------------+--------------+--------------+--------------+--------------+--------------+");
        System.out.println(slotCounts.toString());
        System.out.println("+----------------------------------------------------------------------------------------------------+");
    }

    public static void printMonthCalendar(Doctor doctor, int year, int month, List<LocalDateTime> monthlyAvailableSlots) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        int dayOfWeekOffset = firstDay.getDayOfWeek().getValue() % 7; 
        int length = firstDay.lengthOfMonth();
        
        System.out.println("\n+-----------------------------------------------------------------------------+");
        System.out.printf("|  MONTHLY CALENDAR FOR %-51s  |\n", doctor.getName() + " - " + Month.of(month) + " " + year);
        System.out.println("+-----------------------------------------------------------------------------+");
        System.out.println("|   SUN    |   MON    |   TUE    |   WED    |   THU    |   FRI    |   SAT    |");
        System.out.println("+----------+----------+----------+----------+----------+----------+----------+");
        
        StringBuilder firstRowDays = new StringBuilder("|");
        StringBuilder firstRowSlots = new StringBuilder("|");
        for (int i = 0; i < dayOfWeekOffset; i++) {
            firstRowDays.append("          |");
            firstRowSlots.append("          |");
        }
        
        int currentDay = 1;
        int col = dayOfWeekOffset;
        
        while (col < 7) {
            firstRowDays.append(String.format(" %-8s |", String.format("%02d", currentDay)));
            
            LocalDate curDate = LocalDate.of(year, month, currentDay);
            long count = monthlyAvailableSlots.stream().filter(dt -> dt.toLocalDate().equals(curDate)).count();
            boolean works = false;
            if (doctor.getSpecificDatesAvailability() != null && doctor.getSpecificDatesAvailability().containsKey(curDate)) {
                works = !doctor.getSpecificDatesAvailability().get(curDate).isEmpty();
            } else {
                works = doctor.getWeeklyAvailability() != null && doctor.getWeeklyAvailability().containsKey(curDate.getDayOfWeek());
            }
            String label = works ? count + " spots" : "OFF";
            firstRowSlots.append(String.format(" %-8s |", label));
            
            currentDay++;
            col++;
        }
        System.out.println(firstRowDays.toString());
        System.out.println(firstRowSlots.toString());
        System.out.println("+----------+----------+----------+----------+----------+----------+----------+");
        
        while (currentDay <= length) {
            StringBuilder rowDays = new StringBuilder("|");
            StringBuilder rowSlots = new StringBuilder("|");
            col = 0;
            while (col < 7 && currentDay <= length) {
                rowDays.append(String.format(" %-8s |", String.format("%02d", currentDay)));
                
                LocalDate curDate = LocalDate.of(year, month, currentDay);
                long count = monthlyAvailableSlots.stream().filter(dt -> dt.toLocalDate().equals(curDate)).count();
                boolean works = false;
                if (doctor.getSpecificDatesAvailability() != null && doctor.getSpecificDatesAvailability().containsKey(curDate)) {
                    works = !doctor.getSpecificDatesAvailability().get(curDate).isEmpty();
                } else {
                    works = doctor.getWeeklyAvailability() != null && doctor.getWeeklyAvailability().containsKey(curDate.getDayOfWeek());
                }
                String label = works ? count + " spots" : "OFF";
                rowSlots.append(String.format(" %-8s |", label));
                
                currentDay++;
                col++;
            }
            while (col < 7) {
                rowDays.append("          |");
                rowSlots.append("          |");
                col++;
            }
            System.out.println(rowDays.toString());
            System.out.println(rowSlots.toString());
            System.out.println("+----------+----------+----------+----------+----------+----------+----------+");
        }
    }

    public static void printYearCalendar(Doctor doctor, int year, List<LocalDateTime> yearlyAvailableSlots) {
        System.out.println("\n+---------------------------------------+");
        System.out.printf("|  YEARLY OVERVIEW FOR %-16s |\n", doctor.getName() + " - " + year);
        System.out.println("+---------------------------------------+");
        System.out.println("| Month     | Available Slots           |");
        System.out.println("+-----------+---------------------------+");
        for (int m = 1; m <= 12; m++) {
            final int monthVal = m;
            long count = yearlyAvailableSlots.stream()
                .filter(dt -> dt.getMonthValue() == monthVal)
                .count();
            System.out.printf("| %-9s | %-25s |\n", Month.of(m), count + " spots");
        }
        System.out.println("+---------------------------------------+");
    }
}
