package com.saicomex.repository;

import com.saicomex.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findAllByLocationTypeOrderByNameAsc(String locationType);
}
