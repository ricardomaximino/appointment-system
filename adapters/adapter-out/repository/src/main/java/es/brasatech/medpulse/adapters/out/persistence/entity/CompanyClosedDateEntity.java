package es.brasatech.medpulse.adapters.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "company_closed_dates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyClosedDateEntity {

    @Id
    private LocalDate closedDate;
}
