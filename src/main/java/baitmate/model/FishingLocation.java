package baitmate.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "fishing_location", schema = "baitmate")
public class FishingLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "locationid")
    private Long id;

    @Column(name = "location_name", nullable = false)
    private String locationName;

    @Column
    private double latitude;
    @Column
    private double longitude;

    @ManyToMany(mappedBy = "savedLocations")
    private List<User> usersSaved;

    @OneToMany(mappedBy = "location")
    private List<CatchRecord> catchRecords;

    @ManyToMany
    @JoinTable(
            name = "fish_in_fishing_location",
            joinColumns = @JoinColumn(name = "location_id"),
            inverseJoinColumns = @JoinColumn(name = "fish_id")
    )
    private List<Fish> fishes;

}