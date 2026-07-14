package org.ticketsouq.eventservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.eventservice.model.Section;

import java.util.List;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID> {

}
