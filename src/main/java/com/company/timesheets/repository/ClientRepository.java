package com.company.timesheets.repository;

import com.company.timesheets.entity.Client;
import io.jmix.core.repository.JmixDataRepository;

import java.util.UUID;

public interface ClientRepository extends JmixDataRepository<Client, UUID> {
}