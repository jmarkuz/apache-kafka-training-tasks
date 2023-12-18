package edu.jmarkuz.training.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "wikimedia_recentchanges")
public class WikimediaData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String wikimediaData;
}
