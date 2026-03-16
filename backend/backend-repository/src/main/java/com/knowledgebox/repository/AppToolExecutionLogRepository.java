package com.knowledgebox.repository;

import com.knowledgebox.domain.apptool.AppToolExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppToolExecutionLogRepository extends JpaRepository<AppToolExecutionLog, Long>, JpaSpecificationExecutor<AppToolExecutionLog> {
}
