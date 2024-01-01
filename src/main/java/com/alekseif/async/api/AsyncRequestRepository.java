package com.alekseif.async.api;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AsyncRequestRepository extends JpaRepository<AsyncRequestEntity, Long> {

}
